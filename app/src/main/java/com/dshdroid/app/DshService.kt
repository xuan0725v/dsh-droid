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
            val privLog = File(filesDir, "dsh.log")
            val pubLog = File("/sdcard/Download/DSHA/dsh-droid.log")
            try {
                pubLog.parentFile?.mkdirs()
                ProotManager.ensureSeed(this)
                val p = ProotManager.start(this)
                proc = p
                privLog.writeText("")  // 每次启动清空，便于定位本次
                // 边读边写并即时刷盘：崩溃前最后几行也能落盘
                val buf = ByteArray(4096); var n: Int
                privLog.outputStream().use { out ->
                    while (p.inputStream.read(buf).also { n = it } > 0) {
                        out.write(buf, 0, n); out.flush()
                        try { pubLog.appendBytes(buf.copyOfRange(0, n)) } catch (_: Exception) {}
                    }
                }
                // 进程结束（含被信号杀死）后记录退出码
                val code = p.waitFor()
                val tail = "\n[dsh-droid] proot/node 进程结束 exit=$code\n"
                privLog.appendText(tail)
                try { pubLog.appendText(tail) } catch (_: Exception) {}
            } catch (t: Throwable) {
                val e = "\n[ERROR] $t\n"
                privLog.appendText(e)
                try { pubLog.appendText(e) } catch (_: Exception) {}
            }
        }.also { it.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        proc?.destroy()
        super.onDestroy()
    }
}
