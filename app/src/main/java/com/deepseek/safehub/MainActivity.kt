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

        tvStatus.text = "环境就绪\n\n请确保已在 LSPosed 管理器中启用本模块，并勾选作用域 'DeepSeek'。\n\n完成后点击下方按钮启动。"
        
        btnAction.text = "启动 DeepSeek 开启中枢"
        btnAction.visibility = Button.VISIBLE
        
        btnAction.setOnClickListener {
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.deepseek.chat")
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                tvStatus.text = "未检测到官方 DeepSeek App，请先安装。"
            }
        }
    }
}
