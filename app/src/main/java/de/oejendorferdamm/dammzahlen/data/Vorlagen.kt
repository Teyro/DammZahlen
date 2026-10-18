/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import de.oejendorferdamm.dammzahlen.model.Seite
import de.oejendorferdamm.dammzahlen.model.SeitenJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Eigene Vorlagen: eine Tafelseite unter einem Namen. Gespeichert auf dem Board (filesDir/vorlagen)
 * oder – zum Teilen mit allen Boards – in einem gewählten Ordner (Cloud-Ordner, NAS, USB-Stick).
 * Dateien heißen „Name.dammzahlen“ (JSON).
 */
object Vorlagen {
    const val ENDUNG = ".dammzahlen"
    private const val MAX = 4L * 1024 * 1024

    data class Eintrag(val name: String, val ort: String) // ort = Dateipfad oder Dokument-Uri

    fun sichererName(name: String) = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "-").take(50).ifEmpty { "Vorlage" }

    private fun ordner(c: Context) = File(c.filesDir, "vorlagen").apply { mkdirs() }

    fun lokal(c: Context): List<Eintrag> =
        ordner(c).listFiles { f -> f.name.endsWith(ENDUNG) }.orEmpty().sortedBy { it.name.lowercase() }
            .map { Eintrag(it.name.removeSuffix(ENDUNG), it.absolutePath) }

    fun lokalSpeichern(c: Context, name: String, seite: Seite) {
        val ziel = File(ordner(c), sichererName(name) + ENDUNG)
        val tmp = File(ziel.parentFile, ziel.name + ".tmp")
        tmp.writeText(SeitenJson.schreibe(listOf(seite), 0))
        tmp.renameTo(ziel)
    }

    fun lokalLoeschen(e: Eintrag) = File(e.ort).delete()

    fun lokalLaden(e: Eintrag): Seite? = try {
        File(e.ort).takeIf { it.length() <= MAX }?.readText()?.let { SeitenJson.lies(it)?.seiten?.firstOrNull() }
    } catch (_: Exception) {
        null
    }

    // ------------------------------------------------------------ geteilter Ordner

    fun ordnerMerken(c: Context, baum: Uri) =
        c.contentResolver.takePersistableUriPermission(baum, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

    suspend fun imOrdner(c: Context, baum: Uri): List<Eintrag> = withContext(Dispatchers.IO) {
        val kinder = DocumentsContract.buildChildDocumentsUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum))
        val liste = ArrayList<Eintrag>()
        c.contentResolver.query(kinder, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { k ->
            while (k.moveToNext()) {
                val name = k.getString(1) ?: continue
                if (name.endsWith(ENDUNG, ignoreCase = true)) liste.add(Eintrag(name.dropLast(ENDUNG.length), DocumentsContract.buildDocumentUriUsingTree(baum, k.getString(0)).toString()))
            }
        }
        liste.sortedBy { it.name.lowercase() }
    }

    suspend fun ausOrdnerLaden(c: Context, e: Eintrag): Seite? = withContext(Dispatchers.IO) {
        try {
            c.contentResolver.openInputStream(Uri.parse(e.ort))?.use { ein ->
                val daten = ein.readBytes()
                if (daten.size > MAX) null else SeitenJson.lies(daten.toString(Charsets.UTF_8))?.seiten?.firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** In den Ordner schreiben – gibt es den Namen schon, wird die Datei überschrieben. */
    suspend fun inOrdnerSpeichern(c: Context, baum: Uri, name: String, seite: Seite): Boolean = withContext(Dispatchers.IO) {
        try {
            val datei = sichererName(name) + ENDUNG
            val vorhanden = imOrdner(c, baum).firstOrNull { it.name.equals(sichererName(name), ignoreCase = true) }
            val uri = vorhanden?.let { Uri.parse(it.ort) } ?: DocumentsContract.createDocument(
                c.contentResolver, DocumentsContract.buildDocumentUriUsingTree(baum, DocumentsContract.getTreeDocumentId(baum)), "application/json", datei
            ) ?: return@withContext false
            c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(SeitenJson.schreibe(listOf(seite), 0).toByteArray()) } != null
        } catch (_: Exception) {
            false
        }
    }
}
