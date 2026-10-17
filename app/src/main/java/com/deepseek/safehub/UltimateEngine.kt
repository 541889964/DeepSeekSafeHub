package com.deepseek.safehub
import android.util.Base64
import kotlin.random.Random
import java.util.UUID

object UltimateEngine {
    var officialClient: Any? = null
    var authHeader: String? = null
    private const val WORK_DIR = "/sdcard/DeepSeekAgent"
    
    // 防封号：上次请求的时间戳
    private var lastRequestTime = 0L

    fun askAI(sessionId: String, prompt: String, onLog: (String) -> Unit): String {
        if (officialClient == null || authHeader == null) {
            onLog("❌ 网络通道未就绪，请先聊天一句话！"); return ""
        }
        
        // 【防封号机制 1】：强制随机冷却 3~8 秒
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < 3000) {
            val sleepTime = 3000L - elapsed + Random.nextLong(0, 5000)
            onLog("🛡️ 触发防封控冷却，休眠 ${sleepTime/1000} 秒...")
            Thread.sleep(sleepTime)
        }
        lastRequestTime = System.currentTimeMillis()

        return try {
            val builderClass = Class.forName("okhttp3.Request\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("url", String::class.java).invoke(builder, "https://chat.deepseek.com/api/v0/chat/completion")
            val jsonBody = """{"chat_session_id":"$sessionId","prompt":"${prompt.replace("\"", "\\\"").replace("\n", "\\n")}","ref_file_ids":[]}"""
            
            val bodyClass = Class.forName("okhttp3.RequestBody")
            val bodyCompanion = bodyClass.getField("Companion").get(null)
            val mediaTypeClass = Class.forName("okhttp3.MediaType")
            val mediaTypeCompanion = mediaTypeClass.getField("Companion").get(null)
            val mediaType = mediaTypeCompanion.javaClass.getMethod("toMediaType", String::class.java).invoke(mediaTypeCompanion, "application/json")
            val body = bodyCompanion.javaClass.getMethod("toRequestBody", String::class.java, mediaTypeClass).invoke(bodyCompanion, jsonBody, mediaType)

            builderClass.getMethod("post", bodyClass).invoke(builder, body)
            builderClass.getMethod("header", String::class.java, String::class.java).invoke(builder, "Authorization", authHeader)
            builderClass.getMethod("header", String::class.java, String::class.java).invoke(builder, "Content-Type", "application/json")

            val request = builderClass.getMethod("build").invoke(builder)
            val call = officialClient!!.javaClass.getMethod("newCall", Class.forName("okhttp3.Request")).invoke(officialClient, request)
            val response = call.javaClass.getMethod("execute").invoke(call)
            
            // 【防封号机制 2】：判断 429 限流状态
            val code = response.javaClass.getMethod("code").invoke(response) as Int
            if (code == 429) {
                onLog("🚨 触发风控 429！强制休眠 5 分钟...")
                Thread.sleep(300000L)
                return ""
            }

            val responseBody = response.javaClass.getMethod("body").invoke(response)
            responseBody?.javaClass?.getMethod("string")?.invoke(responseBody) as? String ?: ""
        } catch (t: Throwable) { onLog("❌ 请求失败: ${t.message}"); "" }
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

            Regex("\\[INSTALL\\](.*?)\\[/INSTALL\\]").findAll(aiResp).forEach { m ->
                val path = m.groupValues[1].trim()
                onLog("📦 自动安装: $path")
                val res = runRoot("pm install -r $path")
                if (res.contains("Failure")) {
                    onLog("⚠️ 签名冲突，卸载重试...")
                    val pkgName = runRoot("aapt dump badging $path | grep package | awk '{print $2}'").split("'")[1]
                    runRoot("pm uninstall $pkgName")
                    onLog(runRoot("pm install $path"))
                } else { onLog(res) }
            }
        }
    }
}
