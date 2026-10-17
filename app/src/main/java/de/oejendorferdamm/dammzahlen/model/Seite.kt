/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.model

import org.json.JSONArray
import org.json.JSONObject

/** Ein Stiftstrich auf der Tafel (Vorbild-Pixel). */
data class Linie(val farbe: Int, val breite: Float, val punkte: List<Punkt>)

/** Eine Tafelseite: Teile und Zeichnung. */
data class Seite(val teile: List<Teil> = emptyList(), val linien: List<Linie> = emptyList())

/** Speichern und Laden als JSON – schlicht, lesbar und robust gegen Unbekanntes. */
object SeitenJson {
    const val FORMAT = 1

    fun schreibe(seiten: List<Seite>, aktiv: Int): String = JSONObject().apply {
        put("format", FORMAT)
        put("app", "DammZahlen")
        put("aktiv", aktiv)
        put("seiten", JSONArray().apply { seiten.forEach { put(seite(it)) } })
    }.toString()

    fun seite(s: Seite): JSONObject = JSONObject().apply {
        put("teile", JSONArray().apply { s.teile.forEach { put(teil(it)) } })
        put("linien", JSONArray().apply {
            s.linien.forEach { l ->
                put(JSONObject().put("farbe", l.farbe).put("breite", l.breite.toDouble()).put("punkte", JSONArray().apply {
                    l.punkte.forEach { p -> put(p.x.toDouble()); put(p.y.toDouble()) }
                }))
            }
        })
    }

    private fun teil(t: Teil): JSONObject = JSONObject().put("id", t.id).put("x", t.x.toDouble()).put("y", t.y.toDouble()).apply {
        when (t) {
            is Plaettchen -> put("typ", "plaettchen").put("rot", t.rot).put("weg", t.weg)
            is Figur -> put("typ", "figur").put("art", t.art)
            is Feld -> put("typ", "feld").put("art", t.art.name)
            is Bus -> put("typ", "bus")
            is Rahmen -> put("typ", "rahmen").put("reihen", t.reihen).put("links", JSONArray(t.links))
            is Strahl -> put("typ", "strahl").put("bis", t.bis).apply { t.marke?.let { put("marke", it) } }
            is Strich -> put("typ", "strich").put("start", t.start).put("ende", t.ende).put("spruenge", JSONArray().apply {
                t.spruenge.forEach { put(JSONObject().put("von", it.von).put("bis", it.bis)) }
            })
            is Karte -> put("typ", "karte").put("text", t.text)
        }
    }

    data class Ergebnis(val seiten: List<Seite>, val aktiv: Int)

    /** Liest; unbrauchbare Teile werden ausgelassen. null = gar keine DammZahlen-Datei. */
    fun lies(text: String): Ergebnis? = try {
        val o = JSONObject(text)
        if (o.optInt("format", -1) < 1) null else {
            val a = o.optJSONArray("seiten") ?: JSONArray()
            val seiten = (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::seiteLesen) }.ifEmpty { listOf(Seite()) }
            Ergebnis(seiten, o.optInt("aktiv", 0).coerceIn(0, seiten.lastIndex))
        }
    } catch (_: Exception) {
        null
    }

    fun seiteLesen(o: JSONObject): Seite {
        val t = o.optJSONArray("teile") ?: JSONArray()
        val teile = (0 until t.length()).mapNotNull { t.optJSONObject(it)?.let(::teilLesen) }
        val l = o.optJSONArray("linien") ?: JSONArray()
        val linien = (0 until l.length()).mapNotNull { i ->
            val lo = l.optJSONObject(i) ?: return@mapNotNull null
            val pa = lo.optJSONArray("punkte") ?: return@mapNotNull null
            val punkte = (0 until pa.length() / 2).map { Punkt(pa.optDouble(2 * it).toFloat(), pa.optDouble(2 * it + 1).toFloat()) }
            if (punkte.isEmpty()) null else Linie(lo.optInt("farbe"), lo.optDouble("breite", 6.0).toFloat(), punkte)
        }
        return Seite(teile, linien)
    }

    private fun teilLesen(o: JSONObject): Teil? {
        val id = o.optLong("id", -1L).takeIf { it >= 0 } ?: return null
        val x = o.optDouble("x", 0.0).toFloat().coerceIn(-2000f, 4000f)
        val y = o.optDouble("y", 0.0).toFloat().coerceIn(-2000f, 4000f)
        return when (o.optString("typ")) {
            "plaettchen" -> Plaettchen(id, x, y, o.optBoolean("rot", true), o.optBoolean("weg", false))
            "figur" -> Figur(id, x, y, o.optInt("art", 0))
            "feld" -> FeldArt.entries.find { it.name == o.optString("art") }?.let { Feld(id, x, y, it) }
            "bus" -> Bus(id, x, y)
            "rahmen" -> {
                val reihen = o.optInt("reihen", 2).coerceIn(1, 10)
                val la = o.optJSONArray("links")
                Rahmen(id, x, y, reihen, List(reihen) { (la?.optInt(it, 0) ?: 0).coerceIn(0, 10) })
            }
            "strahl" -> Strahl(id, x, y, o.optInt("bis", 20).coerceIn(10, 100), if (o.has("marke")) o.optInt("marke") else null)
            "strich" -> {
                val sa = o.optJSONArray("spruenge") ?: JSONArray()
                Strich(id, x, y, o.optInt("start", 0), o.optInt("ende", 20),
                    (0 until sa.length()).mapNotNull { i -> sa.optJSONObject(i)?.let { Sprung(it.optInt("von"), it.optInt("bis")) } })
            }
            "karte" -> Karte(id, x, y, o.optString("text").take(60))
            else -> null
        }
    }
}
