package com.deepseek.safehub
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var tvStatus: TextView
    private lateinit var btnAction: Button
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvStatus = findViewById(R.id.tvStatus)
        btnAction = findViewById(R.id.btnAction)
        checkEnv()
    }

    private fun checkEnv() {
        executor.execute {
            val hasRoot = runRoot("id").contains("uid=0")
            val hasLsp = File("/data/adb/modules/zygisk_lsposed").exists()
            runOnUiThread {
                when {
                    !hasRoot -> {
                        tvStatus.text = "未获取 Root 权限，无法继续。"
                        btnAction.text = "请求 Root"; btnAction.visibility = Button.VISIBLE; btnAction.setOnClickListener { checkEnv() }
                    }
                    !hasLsp -> {
                        tvStatus.text = "环境就绪\n未安装 LSPosed"
                        btnAction.text = "一键下载并刷入 LSPosed"
                        btnAction.visibility = Button.VISIBLE; btnAction.setOnClickListener { installLSPosed() }
                    }
                    else -> {
                        tvStatus.text = "LSPosed 已就绪\n启用模块并勾选DeepSeek后拉起。"
                        btnAction.text = "启动 DeepSeek"; btnAction.visibility = Button.VISIBLE; btnAction.setOnClickListener { openDeepSeek() }
                    }
                }
            }
        }
    }

    private fun installLSPosed() {
        tvStatus.text = "正在下载 LSPosed..."
        btnAction.visibility = Button.GONE
        executor.execute {
            try {
                val url = URL("https://github.com/LSPosed/LSPosed/releases/download/v1.9.2/LSPosed-v1.9.2-7024-zygisk-release.zip")
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                val file = File(cacheDir, "lsp.zip")
                val output = FileOutputStream(file)
                conn.inputStream.use { it.copyTo(output) }
                runOnUiThread { tvStatus.text = "正在通过 Magisk 刷入..." }
                runRoot("magisk --install-module ${file.absolutePath}")
                runOnUiThread {
                    tvStatus.text = "刷入成功！即将重启"
                    btnAction.text = "立即重启"; btnAction.visibility = Button.VISIBLE; btnAction.setOnClickListener { runRoot("reboot") }
                }
            } catch (e: Exception) { runOnUiThread { tvStatus.text = "失败: ${e.message}"; checkEnv() } }
        }
    }

    private fun openDeepSeek() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.deepseek.chat")
            startActivity(intent); finish()
        } catch (e: Exception) { tvStatus.text = "未安装 DeepSeek" }
    }

    private fun runRoot(cmd: String): String {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText(); p.waitFor(); out
        } catch (e: Exception) { "" }
    }
}
