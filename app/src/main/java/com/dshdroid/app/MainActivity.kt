package com.dshdroid.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private var loaded = false
    private var tries = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 标题栏（复刻 dsh-starter：Codex 风格深色 + 自绘按钮）──
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF171923.toInt())
            setPadding(28, 20, 20, 20)
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "◆ DeepSeek Harness"
                setTextColor(0xFFE8ECF4.toInt()); textSize = 15f
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btn("⟳") { if (loaded) web.reload() else poll() })
            addView(btn("⤓") { startActivity(Intent(this@MainActivity, UpdaterActivity::class.java)) })
            addView(btn("✕") {
                stopService(Intent(this@MainActivity, DshService::class.java)); finishAffinity()
            })
        }
        // ── WebView ──
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            webViewClient = WebViewClient()
        }
        // ── 状态条 ──
        status = TextView(this).apply {
            text = "  正在启动服务…"
            setBackgroundColor(0xFF0F1420.toInt()); setTextColor(0xFF8B96AB.toInt())
            setPadding(28, 10, 28, 10); textSize = 12f
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(status)
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        startForegroundService(Intent(this, DshService::class.java))
        poll()
    }

    private fun btn(t: String, cb: () -> Unit) = Button(this).apply {
        text = t; setBackgroundColor(Color.TRANSPARENT)
        setTextColor(0xFF8B96AB.toInt()); textSize = 14f
        setPadding(22, 6, 22, 6)
        setOnClickListener { cb() }
    }

    private fun poll() {
        Thread {
            var ok = false
            try {
                val c = URL("http://127.0.0.1:${ProotManager.PORT}/").openConnection() as HttpURLConnection
                c.connectTimeout = 1500; c.readTimeout = 1500
                ok = c.responseCode in 200..499
            } catch (_: Exception) {}
            handler.post {
                when {
                    ok && !loaded -> {
                        loaded = true; status.text = "  ✅ 服务就绪"
                        web.loadUrl("http://127.0.0.1:${ProotManager.PORT}/")
                    }
                    ok && loaded -> {}
                    !ok && tries < 120 -> {
                        tries++
                        status.text = "  ⏳ 启动中… ${tries * 2}s（首次需解压运行时，请稍候）"
                        handler.postDelayed({ poll() }, 2000)
                    }
                    else -> status.text = "  ❌ 服务未能启动，请下拉通知栏后重试，或查看 files/dsh.log"
                }
            }
        }.start()
    }

    override fun onBackPressed() {
        if (this::web.isInitialized && web.canGoBack()) web.goBack() else moveTaskToBack(true)
    }
}
