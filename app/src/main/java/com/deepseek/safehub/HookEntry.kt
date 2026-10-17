package com.deepseek.safehub
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class HookEntry : XposedModule() {
    companion object { private val handler = Handler(Looper.getMainLooper()) }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != "com.deepseek.chat") return
        val cl = param.classLoader

        try {
            val clientClass = Class.forName("okhttp3.OkHttpClient", false, cl)
            val reqClass = Class.forName("okhttp3.Request", false, cl)
            hook(clientClass.getMethod("newCall", reqClass)).intercept { chain ->
                val req = chain.args[0]
                if (req != null) {
                    if (UltimateEngine.officialClient == null) UltimateEngine.officialClient = chain.thisObject
                    try {
                        val auth = req.javaClass.getMethod("header", String::class.java).invoke(req, "Authorization") as? String
                        if (auth != null && auth.startsWith("Bearer ")) UltimateEngine.authHeader = auth
                    } catch (_: Throwable) {}
                }
                chain.proceed()
            }
        } catch (t: Throwable) {}

        try {
            val activityClass = Class.forName("android.app.Activity", false, cl)
            hook(activityClass.getMethod("onResume")).intercept { chain ->
                val res = chain.proceed()
                (chain.thisObject as? Activity)?.let { injectUI(it) }
                res
            }
        } catch (t: Throwable) {}
    }

    private fun injectUI(activity: Activity) {
        handler.post {
            val rootView = activity.window?.decorView as? FrameLayout ?: return@post
            if (rootView.findViewWithTag<Button>("ultimate_btn") != null) return@post

            val fab = Button(activity).apply {
                tag = "ultimate_btn"
                text = "⚡"
                textSize = 24f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1E1E1E"))
                }
                elevation = 20f
                setOnClickListener { showDialog(activity) }
            }
            val lp = FrameLayout.LayoutParams(140, 140).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 40, 300)
            }
            rootView.addView(fab, lp)
        }
    }

    private fun showDialog(activity: Activity) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30)
        }
        val input = EditText(activity).apply { hint = "如: 把 xx 项目 UI 改炫酷并编译安装" }
        layout.addView(input)
        val output = TextView(activity).apply {
            text = "安全中枢已就绪...\n防封策略已启动。\n请先在聊天框发一条消息激活。"
            textSize = 11f; setTextColor(Color.parseColor("#00FF00")); setBackgroundColor(Color.parseColor("#000000")); setPadding(20, 20, 20, 20)
        }
        val scroll = ScrollView(activity).apply { addView(output); layoutParams = LinearLayout.LayoutParams(-1, 600) }
        layout.addView(scroll)

        val dialog = AlertDialog.Builder(activity).setTitle("⚡ 安全中枢").setView(layout).setPositiveButton("执行", null).setNegativeButton("关闭", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val task = input.text.toString().trim()
                if (task.isNotEmpty()) {
                    output.text = ">>> 任务启动(冷却中)...\n"
                    Thread {
                        UltimateEngine.execute(task) { log ->
                            handler.post {
                                output.append("$log\n")
                                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                            }
                        }
                    }.start()
                }
            }
        }
        dialog.show()
    }
}
