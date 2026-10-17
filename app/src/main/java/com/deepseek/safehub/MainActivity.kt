package com.deepseek.safehub

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val btnAction = findViewById<Button>(R.id.btnAction)

        tvStatus.text = "DeepSeek 安全中枢\n\n请在 LSPosed 中勾选本模块并重启应用。\n点击下方按钮拉起 DeepSeek 开始工作。"
        
        btnAction.text = "启动 DeepSeek"
        btnAction.visibility = Button.VISIBLE
        
        btnAction.setOnClickListener {
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.deepseek.chat")
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                tvStatus.text = "启动失败：您似乎没有安装官方 DeepSeek。"
            }
        }
    }
}
