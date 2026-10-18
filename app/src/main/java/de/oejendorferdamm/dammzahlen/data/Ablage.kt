/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.data

import android.content.Context
import de.oejendorferdamm.dammzahlen.model.Seite
import de.oejendorferdamm.dammzahlen.model.SeitenJson
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** Die Tafel wird beim Ändern still gesichert und beim nächsten Start wiederhergestellt. */
object Ablage {
    private fun datei(c: Context) = File(c.filesDir, "tafel.json")

    fun laden(c: Context): SeitenJson.Ergebnis? = try {
        datei(c).takeIf { it.exists() }?.readText()?.let { SeitenJson.lies(it) }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun speichern(c: Context, seiten: List<Seite>, aktiv: Int) {
        try {
            val tmp = File(c.filesDir, "tafel.json.tmp")
            tmp.writeText(SeitenJson.schreibe(seiten, aktiv))
            if (!tmp.renameTo(datei(c))) { datei(c).delete(); tmp.renameTo(datei(c)) }
        } catch (_: Exception) {
        }
    }
}

/** Einstellungen dieses Geräts. */
class Einstellungen(c: Context) {
    private val p = c.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    var hintergrund: Int
        get() = p.getInt("hintergrund", TAFELGRUEN)
        set(v) = p.edit().putInt("hintergrund", v).apply()
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    /** Geteilter Vorlagen-Ordner (Dokument-Baum), null = keiner. */
    var vorlagenOrdner: String?
        get() = p.getString("vorlagenOrdner", null)
        set(v) = p.edit().putString("vorlagenOrdner", v).apply()
    var terme: Boolean
        get() = p.getBoolean("terme", true)
        set(v) = p.edit().putBoolean("terme", v).apply()

    /** PIN für das Verlassen des Kindermodus (gesalzen gehasht, nie im Klartext). */
    val hatPin get() = p.getString("pinHash", null) != null

    fun setzePin(pin: String?) {
        if (pin.isNullOrEmpty()) { p.edit().remove("pinHash").remove("pinSalz").apply(); return }
        val salz = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        p.edit().putString("pinSalz", salz).putString("pinHash", hash(salz, pin)).apply()
    }

    fun pinStimmt(pin: String): Boolean {
        val h = p.getString("pinHash", null) ?: return true
        return hash(p.getString("pinSalz", "") ?: "", pin) == h
    }

    private fun hash(salz: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest("$salz:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val TAFELGRUEN = 0xFF5E8C6A.toInt()
        val HINTERGRUENDE = listOf(TAFELGRUEN, 0xFF262A26.toInt(), 0xFF3A4F6E.toInt(), 0xFFB9BDB8.toInt())
    }
}
