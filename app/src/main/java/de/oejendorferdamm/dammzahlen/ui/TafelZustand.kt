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
import de.oejendorferdamm.dammzahlen.model.Aufgabe
import de.oejendorferdamm.dammzahlen.model.LernGeometrie
import de.oejendorferdamm.dammzahlen.model.Punkt
import de.oejendorferdamm.dammzahlen.model.Stange
import de.oejendorferdamm.dammzahlen.model.Stellentafel
import de.oejendorferdamm.dammzahlen.model.Wuerfel
import de.oejendorferdamm.dammzahlen.model.Zahlenhaus
import de.oejendorferdamm.dammzahlen.model.Zahlenmauer
import de.oejendorferdamm.dammzahlen.model.Zelle

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
        // Nach jeder Änderung gelten alte Prüf-Markierungen nicht mehr
        if (neu != seite) marken = emptyMap()
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

    // ------------------------------------------------------------ Prüfen

    /** Prüf-Markierungen je Teil (je Zelle bzw. Stockwerk: true richtig, false falsch, null leer). */
    var marken by mutableStateOf<Map<Long, List<Boolean?>>>(emptyMap())
        private set

    /** Ergebnis der letzten Prüfung: richtig von gesamt (null = keine Anzeige). */
    var pruefErgebnis by mutableStateOf<Pair<Int, Int>?>(null)

    fun hatPruefbares() = seite.teile.any { it is Aufgabe || it is Zahlenhaus || it is Zahlenmauer }

    fun pruefen() {
        val neu = HashMap<Long, List<Boolean?>>()
        var richtig = 0
        var gesamt = 0
        for (t in seite.teile) {
            val m: List<Boolean?> = when (t) {
                is Aufgabe -> listOf(t.eingabe?.let { it == t.loesung } ?: false)
                is Zahlenhaus -> LernGeometrie.hausPruefen(t).map { it ?: false }
                is Zahlenmauer -> LernGeometrie.mauerPruefen(t).mapIndexed { i, ok -> if (t.zellen[i].fest) null else ok ?: false }
                else -> continue
            }
            neu[t.id] = m
            richtig += m.count { it == true }
            gesamt += m.count { it != null }
        }
        marken = neu
        pruefErgebnis = richtig to gesamt
    }

    /** Zahl in eine Zelle (Zahlenhaus, Mauer) bzw. Lücke (Aufgabe) schreiben; [fest] nur durch die Lehrkraft. */
    fun setzeZahl(id: Long, index: Int, wert: Int?, fest: Boolean = false) = aendereTeile { liste ->
        liste.map { t ->
            if (t.id != id) t else when (t) {
                is Aufgabe -> t.copy(eingabe = wert)
                is Zahlenhaus -> t.copy(zellen = t.zellen.mapIndexed { i, z -> if (i == index) Zelle(wert, fest && wert != null) else z })
                is Zahlenmauer -> t.copy(zellen = t.zellen.mapIndexed { i, z -> if (i == index) Zelle(wert, fest && wert != null) else z })
                else -> t
            }
        }
    }

    // ------------------------------------------------------------ Stellenwert

    fun buendeln(tafelId: Long) {
        val tafel = seite.teile.firstOrNull { it.id == tafelId } as? Stellentafel ?: return
        LernGeometrie.buendeln(tafel, seite.teile, ::neueId)?.let { neu -> aendereTeile { neu } }
    }

    fun entbuendeln(tafelId: Long, stangeId: Long) {
        val tafel = seite.teile.firstOrNull { it.id == tafelId } as? Stellentafel ?: return
        val stange = seite.teile.firstOrNull { it.id == stangeId } as? Stange ?: return
        LernGeometrie.entbuendeln(tafel, stange, seite.teile, ::neueId)?.let { neu -> aendereTeile { neu } }
    }

    // ------------------------------------------------------------ Behälter-Menü (Lehrkraft)

    /** Plättchen bzw. Figuren, die gerade in diesem Behälter sitzen. */
    fun inhaltVon(b: Teil): List<Teil> {
        val plaetze = Geometrie.plaetze(b) + if (b is Stellentafel) LernGeometrie.stangenPlaetze(b) + LernGeometrie.wuerfelPlaetze(b) else emptyList()
        return seite.teile.filter { s -> Geometrie.istStueck(s) && plaetze.any { it.abstand(Punkt(s.x, s.y)) < 4f } }
    }

    /** Behälter mit [anzahl] Plättchen füllen (erst [rot] rote, dann blaue) – vorhandene werden ersetzt. */
    fun fuellen(id: Long, anzahl: Int, rot: Int) {
        val b = seite.teile.firstOrNull { it.id == id } ?: return
        val weg = inhaltVon(b).map { it.id }.toSet()
        val plaetze = Geometrie.plaetze(b)
        val jetzt = System.currentTimeMillis()
        val neu = plaetze.take(anzahl.coerceIn(0, plaetze.size)).mapIndexed { i, p ->
            if (b is Bus) Figur(neueId(), p.x, p.y, i) else Plaettchen(neueId(), p.x, p.y, rot = i < rot)
        }
        neu.forEach { erschienen[it.id] = jetzt }
        aendereTeile { liste -> liste.filter { it.id !in weg } + neu }
    }

    fun leeren(id: Long) {
        val b = seite.teile.firstOrNull { it.id == id } ?: return
        val weg = inhaltVon(b).map { it.id }.toSet()
        aendereTeile { liste ->
            liste.filter { it.id !in weg }.map { t ->
                when {
                    t.id != id -> t
                    t is Zahlenhaus -> t.copy(zellen = t.zellen.map { if (it.fest) it else Zelle() })
                    t is Zahlenmauer -> t.copy(zellen = t.zellen.map { if (it.fest) it else Zelle() })
                    t is Aufgabe -> t.copy(eingabe = null)
                    t is Rahmen -> t.copy(links = List(t.reihen) { 0 })
                    t is Strahl -> t.copy(marke = null)
                    else -> t
                }
            }
        }
    }

    fun wenden(id: Long) {
        val b = seite.teile.firstOrNull { it.id == id } ?: return
        val drin = inhaltVon(b).map { it.id }.toSet()
        aendereTeile { liste -> liste.map { if (it.id in drin && it is Plaettchen) it.copy(rot = !it.rot) else it } }
    }

    /** Teil entfernen – bei Behältern samt Inhalt. */
    fun entfernen(id: Long) {
        val b = seite.teile.firstOrNull { it.id == id } ?: return
        val weg = inhaltVon(b).map { it.id }.toSet() + id
        aendereTeile { liste -> liste.filter { it.id !in weg } }
    }

    fun duplizieren(id: Long) {
        val b = seite.teile.firstOrNull { it.id == id } ?: return
        val dx = 40f
        val kopien = (listOf(b) + inhaltVon(b)).map { t -> umId(t, neueId()).verschoben(t.x + dx, t.y + dx) }
        val jetzt = System.currentTimeMillis()
        kopien.forEach { erschienen[it.id] = jetzt }
        aendereTeile { it + kopien }
    }

    // ------------------------------------------------------------ Aufgaben, Vorlagen

    /** Neue Seite mit Aufgabenkarten (rechts) und optional einem Feld zum Legen (links). */
    fun aufgabenSeite(aufgaben: List<Aufgabe>, mitFeld: FeldArt?) {
        ablaufBeenden()
        val teile = ArrayList<Teil>()
        if (mitFeld != null) teile.add(Feld(neueId(), 160f, if (mitFeld == FeldArt.HUNDERTER) 120f else 300f, mitFeld))
        val links = if (mitFeld == FeldArt.HUNDERTER) 920f else if (mitFeld != null) 980f else 300f
        val spalten = if (mitFeld != null) 2 else 3
        aufgaben.forEachIndexed { i, a ->
            val spalte = i % spalten
            val zeile = i / spalten
            teile.add(a.copy(id = neueId(), x = links + spalte * 470f, y = 120f + zeile * 140f))
        }
        neueSeiteMit(Seite(teile))
    }

    fun teilHinzufuegen(t: Teil) {
        val neu = umId(t, neueId())
        erschienen[neu.id] = System.currentTimeMillis()
        aendereTeile { it + neu }
    }

    /** Seite (z. B. aus einer Vorlage) als neue Seite anhängen – mit frischen Ids. */
    fun neueSeiteMit(vorlage: Seite) {
        ablaufBeenden()
        val neu = vorlage.copy(teile = vorlage.teile.map { umId(it, neueId()) })
        seiten = if (seite.teile.isEmpty() && seite.linien.isEmpty()) seiten.toMutableList().also { it[aktiv] = neu } else seiten + neu
        aktiv = seiten.indexOfLast { it === neu }.takeIf { it >= 0 } ?: seiten.lastIndex
        vorhang = false
        marken = emptyMap()
        zurueckLeeren()
    }

    private fun umId(t: Teil, id: Long): Teil = when (t) {
        is Plaettchen -> t.copy(id = id)
        is Figur -> t.copy(id = id)
        is Feld -> t.copy(id = id)
        is Bus -> t.copy(id = id)
        is Rahmen -> t.copy(id = id)
        is Strahl -> t.copy(id = id)
        is Strich -> t.copy(id = id)
        is Karte -> t.copy(id = id)
        is Stange -> t.copy(id = id)
        is Wuerfel -> t.copy(id = id)
        is Stellentafel -> t.copy(id = id)
        is Zahlenhaus -> t.copy(id = id)
        is Zahlenmauer -> t.copy(id = id)
        is Aufgabe -> t.copy(id = id)
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
            "stellen" -> listOf(Stellentafel(neueId(), 660f + versatz, 160f + versatz))
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

    fun hatBehaelter() = seite.teile.any { Geometrie.istBehaelter(it) || it is Rahmen || it is Stellentafel }
}
