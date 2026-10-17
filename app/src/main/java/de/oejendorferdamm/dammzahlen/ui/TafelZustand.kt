/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.oejendorferdamm.dammzahlen.model.Bus
import de.oejendorferdamm.dammzahlen.model.Feld
import de.oejendorferdamm.dammzahlen.model.FeldArt
import de.oejendorferdamm.dammzahlen.model.Figur
import de.oejendorferdamm.dammzahlen.model.Geometrie
import de.oejendorferdamm.dammzahlen.model.Karte
import de.oejendorferdamm.dammzahlen.model.Linie
import de.oejendorferdamm.dammzahlen.model.Plaettchen
import de.oejendorferdamm.dammzahlen.model.Rahmen
import de.oejendorferdamm.dammzahlen.model.Seite
import de.oejendorferdamm.dammzahlen.model.Strahl
import de.oejendorferdamm.dammzahlen.model.Strich
import de.oejendorferdamm.dammzahlen.model.Teil
import de.oejendorferdamm.dammzahlen.model.Uebergang

/** Was gerade über die Tafel gelegt ist. */
enum class Werkzeug { HAND, STIFT }

/**
 * Der ganze Zustand der Tafel: Seiten, aktive Seite, Rückgängig, Kindermodus, Stift, Blitzblick
 * und ein laufender Übergangs-Ablauf. Jede Änderung ersetzt die Seite (unveränderliche Daten) –
 * dadurch sind Rückgängig und Speichern einfach und sicher.
 */
class TafelZustand(start: List<Seite>, aktiv: Int) {
    var seiten by mutableStateOf(start.ifEmpty { listOf(Seite()) })
        private set
    var aktiv by mutableIntStateOf(aktiv.coerceIn(0, seiten.lastIndex))
        private set
    val seite get() = seiten[aktiv]

    /** Kinder dürfen nur Plättchen und Figuren schieben, wenden und zurücklegen. */
    var kindermodus by mutableStateOf(false)
        private set
    /** Stand beim Wechsel in den Kindermodus – „Zurücksetzen“ stellt ihn wieder her. */
    private var kinderStart: List<Seite> = emptyList()

    var werkzeug by mutableStateOf(Werkzeug.HAND)
    var stiftFarbe by mutableIntStateOf(0xFFFFFFFF.toInt())
    var terme by mutableStateOf(true)

    /** Blitzblick: Behälter verdeckt. */
    var vorhang by mutableStateOf(false)

    /** Laufender Übergangs-Ablauf mit aktuellem Schritt. */
    var ablauf by mutableStateOf<List<Uebergang.Schritt>?>(null)
        private set
    var schritt by mutableIntStateOf(0)
        private set

    /** Gerade erschienene Teile (für die Einblend-Animation): Id → Zeitpunkt. */
    val erschienen = HashMap<Long, Long>()

    private val zurueck = ArrayDeque<Seite>()
    var kannZurueck by mutableStateOf(false)
        private set

    private var naechsteId: Long = (seiten.flatMap { it.teile }.maxOfOrNull { it.id } ?: 0L) + 1
    fun neueId() = naechsteId++

    // ------------------------------------------------------------ Grundoperationen

    /** Seite ersetzen; [merken] = Rückgängig möglich machen. */
    fun setzeSeite(neu: Seite, merken: Boolean = true, vorher: Seite = seite) {
        if (neu == seite && vorher == seite) return
        if (merken && vorher != neu) {
            zurueck.addLast(vorher)
            while (zurueck.size > 50) zurueck.removeFirst()
            kannZurueck = true
        }
        seiten = seiten.toMutableList().also { it[aktiv] = neu }
    }

    fun aendereTeile(merken: Boolean = true, f: (List<Teil>) -> List<Teil>) = setzeSeite(seite.copy(teile = f(seite.teile)), merken)

    fun rueckgaengig() {
        val vorher = zurueck.removeLastOrNull() ?: return
        seiten = seiten.toMutableList().also { it[aktiv] = vorher }
        kannZurueck = zurueck.isNotEmpty()
    }

    private fun zurueckLeeren() {
        zurueck.clear()
        kannZurueck = false
    }

    // ------------------------------------------------------------ Seiten

    fun zuSeite(i: Int) {
        if (i !in seiten.indices || i == aktiv) return
        ablaufBeenden()
        aktiv = i
        vorhang = false
        zurueckLeeren()
    }

    fun neueSeite() {
        ablaufBeenden()
        seiten = seiten + Seite()
        aktiv = seiten.lastIndex
        zurueckLeeren()
    }

    fun seiteLoeschen() {
        if (seiten.size <= 1) { setzeSeite(Seite()); return }
        seiten = seiten.toMutableList().also { it.removeAt(aktiv) }
        aktiv = aktiv.coerceAtMost(seiten.lastIndex)
        zurueckLeeren()
    }

    // ------------------------------------------------------------ Material

    /** Neues Material ungefähr in die Mitte der freien Tafel (versetzt, falls dort schon etwas liegt). */
    fun hinzufuegen(art: String) {
        val versatz = (seite.teile.count { !it.beweglichFuerKinder } % 5) * 40f
        val neu: List<Teil> = when (art) {
            "zehner" -> listOf(Feld(neueId(), 800f + versatz, 300f + versatz, FeldArt.ZEHNER))
            "zwanziger" -> listOf(Feld(neueId(), 620f + versatz, 300f + versatz, FeldArt.ZWANZIGER))
            "hunderter" -> listOf(Feld(neueId(), 640f + versatz, 60f + versatz, FeldArt.HUNDERTER))
            "bus" -> listOf<Teil>(Bus(neueId(), 520f + versatz, 160f + versatz)) +
                // zehn Kinder warten an der Haltestelle
                List(10) { i -> Figur(neueId(), 560f + versatz + i * 62f, 640f + versatz, i) }
            "rahmen20" -> listOf(Rahmen(neueId(), 600f + versatz, 360f + versatz, 2))
            "rahmen100" -> listOf(Rahmen(neueId(), 600f + versatz, 120f + versatz, 10))
            "strahl20" -> listOf(Strahl(neueId(), 170f, 600f + versatz, 20))
            "strahl100" -> listOf(Strahl(neueId(), 170f, 600f + versatz, 100))
            "strich" -> listOf(Strich(neueId(), 270f, 560f + versatz, 0, 20))
            "plaettchen10" -> List(10) { i -> Plaettchen(neueId(), 500f + versatz + i * 62f, 820f, rot = true) }
            "kinder10" -> List(10) { i -> Figur(neueId(), 500f + versatz + i * 62f, 820f, i + 3) }
            else -> emptyList()
        }
        val jetzt = System.currentTimeMillis()
        neu.forEach { erschienen[it.id] = jetzt }
        aendereTeile { it + neu }
    }

    fun karteHinzufuegen(text: String) {
        val k = Karte(neueId(), 760f, 40f, text.trim().take(60))
        erschienen[k.id] = System.currentTimeMillis()
        aendereTeile { it + k }
    }

    fun karteAendern(id: Long, text: String) = aendereTeile { liste ->
        if (text.isBlank()) liste.filter { it.id != id }
        else liste.map { if (it.id == id && it is Karte) it.copy(text = text.trim().take(60)) else it }
    }

    /** Alles von der Seite nehmen (Lehrkraft). */
    fun seiteLeeren() = setzeSeite(Seite())

    fun zeichnungLoeschen() = setzeSeite(seite.copy(linien = emptyList()))

    fun linieHinzufuegen(l: Linie) = setzeSeite(seite.copy(linien = seite.linien + l))

    // ------------------------------------------------------------ Kindermodus

    fun kindermodusAn() {
        ablaufBeenden()
        kinderStart = seiten
        kindermodus = true
        werkzeug = Werkzeug.HAND
    }

    fun kindermodusAus() {
        kindermodus = false
        kinderStart = emptyList()
    }

    /** Im Kindermodus: alles wieder so wie beim Einschalten. */
    fun zuruecksetzen() {
        if (kinderStart.isEmpty()) return
        seiten = kinderStart
        aktiv = aktiv.coerceAtMost(seiten.lastIndex)
        vorhang = false
        zurueckLeeren()
    }

    // ------------------------------------------------------------ Übergang

    /** Neue Seite mit dem Ablauf (die bisherige Tafel bleibt unverändert erhalten). */
    fun uebergangStarten(a: Int, b: Int, plus: Boolean) {
        if (!Uebergang.gueltig(a, b, plus)) return
        val schritte = Uebergang.schritte(a, b, plus, ::neueId)
        if (seite.teile.isNotEmpty() || seite.linien.isNotEmpty()) {
            seiten = seiten + Seite()
            aktiv = seiten.lastIndex
        }
        zurueckLeeren()
        ablauf = schritte
        schritt = 0
        zeigeSchritt()
    }

    fun schrittVor() {
        val a = ablauf ?: return
        if (schritt < a.lastIndex) { schritt++; zeigeSchritt() }
    }

    fun schrittZurueck() {
        if (ablauf == null || schritt == 0) return
        schritt--
        zeigeSchritt()
    }

    private fun zeigeSchritt() {
        val a = ablauf ?: return
        val alt = seite.teile.associateBy { it.id }
        val neu = a[schritt].teile
        val jetzt = System.currentTimeMillis()
        // Neue Plättchen erscheinen mit einer kleinen Animation
        neu.forEach { t -> if (t is Plaettchen && alt[t.id] == null) erschienen[t.id] = jetzt }
        seiten = seiten.toMutableList().also { it[aktiv] = Seite(neu, seite.linien) }
    }

    /** Ablauf schließen – das Ergebnis bleibt als normale Seite liegen. */
    fun ablaufBeenden() {
        ablauf = null
        schritt = 0
    }

    // ------------------------------------------------------------ Blitzblick

    fun hatBehaelter() = seite.teile.any { Geometrie.istBehaelter(it) || it is Rahmen }
}
