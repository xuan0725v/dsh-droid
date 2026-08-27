package com.dshdroid.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import java.io.File

class DshService : Service() {
    private var proc: Process? = null
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("dsh", "DSH 服务", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "dsh")
            .setContentTitle("DeepSeek Harness 运行中")
            .setContentText("127.0.0.1:${ProotManager.PORT}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (thread?.isAlive == true) return START_STICKY
        thread = Thread {
            try {
                ProotManager.ensureSeed(this)
                val p = ProotManager.start(this)
                proc = p
                File(filesDir, "dsh.log").outputStream().use { out ->
                    p.inputStream.copyTo(out)
                }
            } catch (t: Throwable) {
                File(filesDir, "dsh.log").appendText("\n[ERROR] $t")
            }
        }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        proc?.destroy()
        super.onDestroy()
    }
}
