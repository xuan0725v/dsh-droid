package com.dshdroid.app

import android.app.Activity
import android.os.*
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

/** 上游同步：检查 dsh-starter 最新版本；内容更新通过 GitHub Actions 重新构建 APK（覆盖安装不丢数据） */
class UpdaterActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0F1420.toInt()); setPadding(40, 60, 40, 40)
        }
        val out = TextView(this).apply {
            text = "检查上游版本…"; setTextColor(0xFFE8ECF4.toInt()); textSize = 15f
        }
        box.addView(TextView(this).apply {
            text = "⤓ 上游同步"; setTextColor(0xFF8B96AB.toInt()); textSize = 20f; setPadding(0, 0, 0, 24)
        })
        box.addView(out)
        setContentView(box)
        Thread {
            val r = try {
                val c = URL("https://api.github.com/repos/sryimnoob123/dsh-starter/releases/latest")
                    .openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                c.getInputStream().bufferedReader().readText()
            } catch (e: Exception) { "ERR: $e" }
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(r)?.groupValues?.get(1) ?: "未知"
            runOnUiThread {
                out.text = "上游最新版本: $tag\n本机构建版本: 0.1.0\n\n" +
                    "内容更新方式：\n1. 打开本项目的 GitHub 仓库\n2. Actions 页签 → build → Run workflow\n" +
                    "3. 构建完成后下载 app-debug.apk 覆盖安装\n（你的会话与配置在覆盖安装后完整保留）\n\n" +
                    "上游 release 每次更新后重新构建一次，内容即自动跟进（种子来自上游最新安装包）。"
            }
        }.start()
    }
}
