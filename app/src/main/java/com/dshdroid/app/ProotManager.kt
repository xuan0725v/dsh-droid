package com.dshdroid.app

import android.content.Context
import java.io.File
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

object ProotManager {
    const val PORT = 3081
    val rootfs: File get() = File(ctx!!.filesDir, "ubuntu")
    private var ctx: Context? = null

    fun unpack(ctx: Context, asset: String, dest: File) {
        dest.mkdirs()
        val tin = TarArchiveInputStream(GZIPInputStream(ctx.assets.open(asset)))
        var e = tin.nextTarEntry
        val buf = ByteArray(1 shl 16)
        while (e != null) {
            val out = File(dest, e.name).canonicalFile
            if (!out.path.startsWith(dest.canonicalPath)) { e = tin.nextTarEntry; continue } // 防路径穿越
            if (e.isDirectory) out.mkdirs()
            else {
                out.parentFile?.mkdirs()
                out.outputStream().use { o -> var n: Int; while (tin.read(buf).also { n = it } > 0) o.write(buf, 0, n) }
                // 还原权限位（可执行）
                val mode = e.mode and 0b111_101_101
                out.setExecutable(mode and 0b001_000_000 != 0, false)
                out.setWritable(mode and 0b000_010_000 != 0, false)
            }
            e = tin.nextTarEntry
        }
        tin.close()
    }

    fun ensureSeed(ctx: Context) {
        this.ctx = ctx
        val mark = File(ctx.filesDir, ".seed-v1")
        if (mark.exists()) return
        val root = File(ctx.filesDir, "ubuntu")
        unpack(ctx, "rootfs.tar.gz", root)
        unpack(ctx, "dsh-runtime.tgz", File(root, "usr/local/lib/node_modules"))
        unpack(ctx, "dsh-home-seed.tgz", File(ctx.filesDir, "dsh-home"))
        unpack(ctx, "host-plugins.tar.gz", File(ctx.filesDir, "dsh-home/profiles/web/node_modules"))
        // DNS：ubuntu-base 默认走 systemd-resolved（容器里没有），换成公共 DNS
        File(root, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        mark.writeText(System.currentTimeMillis().toString())
    }

    fun start(ctx: Context): Process {
        this.ctx = ctx
        val home = File(ctx.filesDir, "dsh-home")
        home.mkdirs()
        val nativeDir: File = ctx.applicationInfo.nativeLibraryDir.let(::File)
        val proot = File(nativeDir, "libproot.so")
        val node = File(nativeDir, "libnode.so")
        File(rootfs, "opt/node/bin").mkdirs()
        File(rootfs, "tmp").mkdirs()
        val argv = mutableListOf(
            proot.path, "--kill-on-exit",
            "-r", rootfs.path, "-0", "-w", "/root",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", home.path + ":/root/.dsh",
            "-b", node.path + ":/opt/node/bin/node",
            "/usr/bin/env", "-i",
            "HOME=/root", "USER=root", "PATH=/opt/node/bin:/usr/local/bin:/usr/bin:/bin",
            "LANG=C.UTF-8", "SHLVL=1", "TMPDIR=/tmp",
            "/opt/node/bin/node", "--expose-internals",
            "/usr/local/lib/node_modules/@deepseek-ai/dsh/lib/bin.js",
            "web", "--no-open", "--port", PORT.toString(), "--host", "127.0.0.1"
        )
        val pb = ProcessBuilder(argv).directory(ctx.filesDir).redirectErrorStream(true)
        return pb.start()
    }
}
