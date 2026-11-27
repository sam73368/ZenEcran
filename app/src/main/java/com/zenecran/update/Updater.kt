package com.zenecran.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.zenecran.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val build: Int, val name: String, val notes: String, val url: String)

/**
 * Mises à jour depuis les Releases GitHub du dépôt (doit être public).
 * Seules requêtes réseau de l'appli : lire la dernière version et télécharger l'APK.
 */
object Updater {
    const val REPO = "sam73368/ZenEcran"
    private const val API = "https://api.github.com/repos/$REPO/releases/tags/latest"

    val currentBuild: Int get() = BuildConfig.VERSION_CODE

    /** Renvoie la version publiée si elle est plus récente, null sinon. Lève une exception en cas d'erreur réseau. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val c = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ZenEcran")
        }
        try {
            when (c.responseCode) {
                200 -> Unit
                404 -> throw IllegalStateException("Dépôt introuvable : il doit être public sur GitHub.")
                else -> throw IllegalStateException("GitHub a répondu ${c.responseCode}.")
            }
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val name = o.optString("name")
            val body = o.optString("body")
            val build = Regex("build[ =](\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("build (\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext null
            val assets = o.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
            }
            val notes = body.lines().map { it.trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("build=") && !it.contains("Co-Authored") && !it.contains("Claude-Session") }
                .orEmpty()
            if (url != null && build > currentBuild) UpdateInfo(build, name, notes, url) else null
        } finally {
            c.disconnect()
        }
    }

    /** Télécharge l'APK dans le cache de l'appli. onProgress reçoit 0..1. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "update.apk")
        var url = URL(info.url)
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "ZenEcran")
            }
            val code = c.responseCode
            if (code in 300..399 && hops < 5) {
                url = URL(url, c.getHeaderField("Location"))
                c.disconnect()
                hops++
                continue
            }
            if (code != 200) throw IllegalStateException("Téléchargement refusé ($code).")
            break
        }
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        c.disconnect()
        out
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Lance l'installation : Android affiche sa fenêtre de confirmation « Mettre à jour ». */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(pi.intentSender)
        }
    }
}

/** Reçoit le résultat de l'installation et ouvre la confirmation d'Android si nécessaire. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "erreur inconnue"
                val hint = if (msg.contains("signature", true) || msg.contains("conflict", true)) {
                    " — désinstalle l'ancienne version une fois, puis réinstalle."
                } else ""
                Toast.makeText(ctx, "Mise à jour impossible : $msg$hint", Toast.LENGTH_LONG).show()
            }
        }
    }
}
