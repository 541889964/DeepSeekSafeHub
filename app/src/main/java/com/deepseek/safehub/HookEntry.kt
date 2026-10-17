package com.deepseek.safehub

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class HookEntry : IXposedHookLoadPackage {
    companion object {
        private val handler = Handler(Looper.getMainLooper())
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.deepseek.chat") return
        XposedBridge.log("DeepSeek 安全中枢已注入！")

        // 1. 劫持网络
        try {
            val clientClass = XposedHelpers.findClass("okhttp3.OkHttpClient", lpparam.classLoader)
            val reqClass = XposedHelpers.findClass("okhttp3.Request", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(clientClass, "newCall", reqClass, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val req = param.args[0]
                    if (req != null) {
                        if (UltimateEngine.officialClient == null) UltimateEngine.officialClient = param.thisObject
                        try {
                            val auth = XposedHelpers.callMethod(req, "header", "Authorization") as? String
                            if (auth != null && auth.startsWith("Bearer ")) UltimateEngine.authHeader = auth
                        } catch (_: Throwable) {}
                    }
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("网络劫持失败: ${t.message}")
        }

        // 2. 注入悬浮窗
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", lpparam.classLoader, "onResume", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity
                    if (activity != null) injectUI(activity)
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("UI注入失败: ${t.message}")
        }
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
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1E1E1E")) }
                elevation = 20f
                setOnClickListener { showDialog(activity) }
            }
            val lp = FrameLayout.LayoutParams(140, 140).apply { gravity = Gravity.BOTTOM or Gravity.END; setMargins(0, 0, 40, 300) }
            rootView.addView(fab, lp)
        }
    }

    private fun showDialog(activity: Activity) {
        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 30) }
        val input = EditText(activity).apply { hint = "输入大白话指令" }
        layout.addView(input)
        val output = TextView(activity).apply { text = "中枢就绪..."; textSize = 11f; setTextColor(Color.parseColor("#00FF00")); setBackgroundColor(Color.BLACK); setPadding(20, 20, 20, 20) }
        val scroll = ScrollView(activity).apply { addView(output); layoutParams = LinearLayout.LayoutParams(-1, 600) }
        layout.addView(scroll)
        val dialog = AlertDialog.Builder(activity).setTitle("⚡ 安全中枢").setView(layout).setPositiveButton("执行", null).setNegativeButton("关闭", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val task = input.text.toString().trim()
                if (task.isNotEmpty()) {
                    output.text = ">>> 任务启动...\n"
                    Thread {
                        UltimateEngine.execute(task) { log ->
                            handler.post { output.append("$log\n"); scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) } }
                        }
                    }.start()
                }
            }
        }
        dialog.show()
    }
}
