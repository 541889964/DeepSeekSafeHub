package com.deepseek.safehub
import android.util.Base64
import java.io.File
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL

object UltimateEngine {
    var officialClient: Any? = null
    var authHeader: String? = null
    private const val WORK_DIR = "/sdcard/DeepSeekAgent"
    private var lastRequestTime = 0L

    // 暴力提取本地 Token
    private fun tryGetLocalToken() {
        if (authHeader != null) return
        try {
            val prefsDir = File("/data/user/0/com.deepseek.chat/shared_prefs")
            if (prefsDir.exists()) {
                prefsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".xml")) {
                        val content = file.readText()
                        val regex = Regex("Bearer [a-zA-Z0-9-_.]+")
                        val match = regex.find(content)
                        if (match != null) {
                            authHeader = match.value
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun askAI(sessionId: String, prompt: String, onLog: (String) -> Unit): String {
        tryGetLocalToken()
        
        if (authHeader == null) {
            onLog("❌ 仍未获取到账号 Token，请随便发条消息后重试！")
            return ""
        }

        val now = System.currentTimeMillis()
        if (now - lastRequestTime < 3000) {
            Thread.sleep(3000)
        }
        lastRequestTime = System.currentTimeMillis()

        return try {
            // 自己建网络通道，不再借用它的！
            val url = URL("https://chat.deepseek.com/api/v0/chat/completion")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", authHeader!!)
            conn.doOutput = true

            val jsonBody = """{"chat_session_id":"$sessionId","prompt":"${prompt.replace("\"", "\\\"").replace("\n", "\\n")}","ref_file_ids":[]}"""
            conn.outputStream.use { it.write(jsonBody.toByteArray()) }

            if (conn.responseCode == 429) {
                onLog("🚨 触发风控 429！强制休眠...")
                Thread.sleep(300000L)
                return ""
            }

            // 简易提取流式返回
            val reader = conn.inputStream.bufferedReader()
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.startsWith("data: ")) {
                    val data = line.substring(6)
                    if (data != "[DONE]") {
                        try {
                            val json = org.json.JSONObject(data)
                            val content = json.optJSONObject("v")?.optString("content") ?: ""
                            sb.append(content)
                        } catch (_: Throwable) {}
                    }
                }
            }
            sb.toString()
        } catch (t: Throwable) {
            onLog("❌ 请求失败: ${t.message}")
            ""
        }
    }

    fun runRoot(cmd: String): String {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val reader = p.inputStream.bufferedReader()
            val sb = StringBuilder(); var line: String?
            while (reader.readLine().also { line = it } != null) { sb.append(line).append("\n") }
            p.waitFor(); sb.toString()
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun execute(userInput: String, onLog: (String) -> Unit) {
        runRoot("mkdir -p $WORK_DIR")
        val pastMemory = runRoot("cat $WORK_DIR/history.txt 2>/dev/null")
        
        val systemPrompt = """
            你是 Android 工程师。工作区: $WORK_DIR
            历史: $pastMemory
            规则：
            1. 读代码：[READ]路径[/READ]
            2. 写代码：[WRITE]路径[/WRITE]```代码```
            3. 执行命令：[CMD]命令[/CMD]
            4. 安装 APK：[INSTALL]路径[/INSTALL]
            5. 换页总结：[NEXT_PAGE]总结[/NEXT_PAGE]
            6. 完成：[DONE]
        """.trimIndent()

        var sessionId = UUID.randomUUID().toString().replace("-", "")
        var context = "任务: $userInput"
        
        for (step in 1..15) {
            onLog("\n--- 思考 $step ---")
            val aiResp = askAI(sessionId, "$systemPrompt\n\n上下文:\n$context", onLog)
            if (aiResp.isEmpty()) break

            if (aiResp.contains("[DONE]")) { onLog("✅ 任务完成。"); break }
            
            if (aiResp.contains("[NEXT_PAGE]")) {
                val summary = Regex("\\[NEXT_PAGE\\](.*?)\\[/NEXT_PAGE\\]").find(aiResp)?.groupValues?.get(1) ?: ""
                onLog("♾️ 记忆接力...")
                sessionId = UUID.randomUUID().toString().replace("-", "")
                context = "记忆: $summary"
                continue
            }

            Regex("\\[CMD\\](.*?)\\[/CMD\\]").findAll(aiResp).forEach { m ->
                val cmd = m.groupValues[1].trim()
                onLog("⚡ 执行: $cmd")
                context += "\n执行 $cmd 结果: " + runRoot(cmd).take(200)
            }

            Regex("\\[READ\\](.*?)\\[/READ\\]").findAll(aiResp).forEach { m ->
                val path = m.groupValues[1].trim()
                onLog("📖 读取: $path")
                context += "\n文件 $path 内容:\n" + runRoot("cat $path").take(2000)
            }

            Regex("\\[WRITE\\](.*?)\\[/WRITE\\][\\s\\S]*?```[a-zA-Z]*\\n([\\s\\S]*?)```").findAll(aiResp).forEach { m ->
                val path = m.groupValues[1].trim()
                val code = m.groupValues[2]
                onLog("✍️ 覆盖: $path")
                val b64 = Base64.encodeToString(code.toByteArray(), Base64.NO_WRAP)
                runRoot("echo '$b64' | base64 -d > $path")
            }
        }
    }
}
