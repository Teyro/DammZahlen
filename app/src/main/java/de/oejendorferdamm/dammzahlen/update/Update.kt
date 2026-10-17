/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates wie bei DammBoard: nur aus den GitHub-Releases dieses Repositorys, nur APKs mit
 * derselben Signatur wie die installierte App. Ist GitHub nicht erreichbar, passiert nichts.
 */
object Update {
    private const val API = "https://api.github.com/repos/Teyro/DammZahlen/releases/latest"
    private const val DOWNLOAD = "https://github.com/Teyro/DammZahlen/releases/download/"
    private const val MAX_APK = 80L * 1024 * 1024

    data class Info(val version: String, val url: String, val groesse: Long, val notizen: String)

    fun istNeuer(neu: String, alt: String): Boolean {
        val a = neu.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val b = alt.removePrefix("v").split('.').map { it.substringBefore('-').toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun pruefen(): Info? = withContext(Dispatchers.IO) {
        try {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "DammZahlen")
            try {
                if (c.responseCode != 200) return@withContext null
                val o = JSONObject(c.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8))
                val version = o.optString("tag_name").removePrefix("v")
                val assets = o.optJSONArray("assets") ?: return@withContext null
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val url = a.optString("browser_download_url")
                    if (a.optString("name").endsWith(".apk") && url.startsWith(DOWNLOAD)) {
                        return@withContext Info(version, url, a.optLong("size"), o.optString("body"))
                    }
                }
                null
            } finally {
                c.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Lädt die APK (nur von GitHub, Weiterleitungen nur zu GitHub-Servern). */
    suspend fun laden(context: Context, info: Info, fortschritt: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        if (!info.url.startsWith(DOWNLOAD)) throw IOException("Unerwartete Download-Adresse")
        val ordner = File(context.cacheDir, "update").apply { mkdirs() }
        val ziel = File(ordner, "DammZahlen.apk")
        var url = URL(info.url)
        var sprung = 0
        while (true) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "DammZahlen")
            val code = c.responseCode
            if (code in 300..399) {
                val weiter = URL(url, c.getHeaderField("Location") ?: throw IOException("Weiterleitung ohne Ziel"))
                c.disconnect()
                if (weiter.protocol != "https" || !(weiter.host == "github.com" || weiter.host.endsWith(".githubusercontent.com"))) throw IOException("Unerwartete Weiterleitung")
                if (++sprung > 5) throw IOException("Zu viele Weiterleitungen")
                url = weiter
                continue
            }
            if (code != 200) throw IOException("Herunterladen fehlgeschlagen (HTTP $code)")
            try {
                var gelesen = 0L
                c.inputStream.use { ein ->
                    ziel.outputStream().use { aus ->
                        val puffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = ein.read(puffer)
                            if (n < 0) break
                            aus.write(puffer, 0, n)
                            gelesen += n
                            if (gelesen > MAX_APK) throw IOException("Datei ist unerwartet groß")
                            if (info.groesse > 0) fortschritt((gelesen.toFloat() / info.groesse).coerceIn(0f, 1f))
                        }
                    }
                }
                if (info.groesse > 0 && gelesen != info.groesse) throw IOException("Download unvollständig")
            } catch (e: Exception) {
                ziel.delete()
                throw e
            } finally {
                c.disconnect()
            }
            break
        }
        if (!signaturPasst(context, ziel)) {
            ziel.delete()
            throw IOException("Die geladene Datei ist nicht korrekt signiert")
        }
        ziel
    }

    @Suppress("DEPRECATION")
    fun signaturPasst(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val neu = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        val alt = pm.getPackageInfo(context.packageName, flags)
        neu != null && neu.packageName == context.packageName && abdruecke(neu) == abdruecke(alt) && abdruecke(alt).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    private fun abdruecke(p: PackageInfo): Set<String> {
        val sig = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else p.signatures
        return sig.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    fun darfInstallieren(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun erlaubnisOeffnen(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    fun installieren(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
