package de.oejendorferdamm.dammzahlen

import de.oejendorferdamm.dammzahlen.model.Bus
import de.oejendorferdamm.dammzahlen.model.Feld
import de.oejendorferdamm.dammzahlen.model.FeldArt
import de.oejendorferdamm.dammzahlen.model.Figur
import de.oejendorferdamm.dammzahlen.model.Geometrie
import de.oejendorferdamm.dammzahlen.model.Karte
import de.oejendorferdamm.dammzahlen.model.Plaettchen
import de.oejendorferdamm.dammzahlen.model.Rahmen
import de.oejendorferdamm.dammzahlen.model.Sprung
import de.oejendorferdamm.dammzahlen.model.Strich
import de.oejendorferdamm.dammzahlen.model.Uebergang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModellTest {
    private var id = 0L
    private fun neu() = ++id

    @Test
    fun zehnerueberganglPlus() {
        val s = Uebergang.schritte(8, 5, true, ::neu)
        assertEquals(3, s.size)
        val letzte = s.last().teile
        val feld = letzte.filterIsInstance<Feld>().single()
        assertEquals(FeldArt.ZWANZIGER, feld.art)
        val inh = Geometrie.inhalt(feld, letzte)
        assertEquals(8, inh.rot)
        assertEquals(5, inh.blau)
        assertEquals(listOf(Sprung(8, 10), Sprung(10, 13)), letzte.filterIsInstance<Strich>().single().spruenge)
        assertEquals("8 + 5 = 8 + 2 + 3 = 13", letzte.filterIsInstance<Karte>().single().text)
        // Schritt 2 füllt genau den ersten Zehner
        assertEquals(10, Geometrie.inhalt(feld, s[1].teile).gesamt)
    }

    @Test
    fun zehneruebergangMinus() {
        val s = Uebergang.schritte(13, 5, false, ::neu)
        val letzte = s.last().teile
        val feld = letzte.filterIsInstance<Feld>().single()
        assertEquals(8, Geometrie.inhalt(feld, letzte).gesamt)
        assertEquals("13 − 5 = 13 − 3 − 2 = 8", letzte.filterIsInstance<Karte>().single().text)
    }

    @Test
    fun hunderteruebergangUndOhneUebergang() {
        val s = Uebergang.schritte(38, 7, true, ::neu)
        val feld = s.last().teile.filterIsInstance<Feld>().single()
        assertEquals(FeldArt.HUNDERTER, feld.art)
        assertEquals(45, Geometrie.inhalt(feld, s.last().teile).gesamt)
        assertEquals(listOf(Sprung(38, 40), Sprung(40, 45)), s.last().teile.filterIsInstance<Strich>().single().spruenge)
        assertEquals(2, Uebergang.schritte(3, 4, true, ::neu).size)
        assertFalse(Uebergang.mitUebergang(10, 5, true))
        assertTrue(Uebergang.mitUebergang(52, 4, false))
        assertFalse(Uebergang.gueltig(90, 20, true))
    }

    @Test
    fun einrastenUndBus() {
        val bus = Bus(1, 100f, 100f)
        val plaetze = Geometrie.plaetze(bus)
        assertEquals(20, plaetze.size)
        val kind = Figur(2, plaetze[3].x + 10f, plaetze[3].y - 8f, 0)
        val ziel = Geometrie.einrasten(kind, listOf(bus, kind))
        assertNotNull(ziel)
        assertEquals(plaetze[3], ziel)
        // Platz belegt → nicht noch einmal
        val anderes = Figur(3, plaetze[3].x, plaetze[3].y, 1)
        assertNull(Geometrie.einrasten(kind.copy(x = plaetze[3].x, y = plaetze[3].y), listOf(bus, anderes, kind)))
        val oben = Figur(4, plaetze[12].x, plaetze[12].y, 2)
        val inh = Geometrie.inhalt(bus, listOf(bus, anderes, oben))
        assertEquals(1, inh.unten)
        assertEquals(1, inh.oben)
        assertEquals("unten 1 · oben 1 · zusammen 2", Geometrie.term(bus, listOf(bus, anderes, oben)))
    }

    @Test
    fun zehnerfeldTerm() {
        val f = Feld(1, 0f, 0f, FeldArt.ZEHNER)
        val p = Geometrie.plaetze(f)
        val teile = listOf(f, Plaettchen(2, p[0].x, p[0].y, true), Plaettchen(3, p[1].x, p[1].y, false), Plaettchen(4, 999f, 999f, true))
        assertEquals("1 + 1 = 2", Geometrie.term(f, teile))
    }

    @Test
    fun rechenrahmen() {
        var r = Rahmen(1, 0f, 0f, 2)
        r = Geometrie.kugelSchieben(r, 0, 6)
        assertEquals(7, Geometrie.rahmenZahl(r))
        r = Geometrie.kugelSchieben(r, 0, 2)
        assertEquals(2, Geometrie.rahmenZahl(r))
        val k = Geometrie.kugel(r, 0, 1)
        assertEquals(0 to 1, Geometrie.kugelBei(r, k.x, k.y))
    }

    @Test
    fun speichernUndLaden() {
        val teile = Uebergang.schritte(8, 5, true, ::neu).last().teile + Bus(99, 10f, 20f) +
            de.oejendorferdamm.dammzahlen.model.Strahl(100, 0f, 0f, 20, 7) + Rahmen(101, 0f, 0f, 2, listOf(3, 10)) + Figur(102, 1f, 2f, 3)
        val seiten = listOf(
            de.oejendorferdamm.dammzahlen.model.Seite(teile, listOf(de.oejendorferdamm.dammzahlen.model.Linie(-1, 6f, listOf(de.oejendorferdamm.dammzahlen.model.Punkt(1f, 2f), de.oejendorferdamm.dammzahlen.model.Punkt(3f, 4f))))),
            de.oejendorferdamm.dammzahlen.model.Seite()
        )
        val e = de.oejendorferdamm.dammzahlen.model.SeitenJson.lies(de.oejendorferdamm.dammzahlen.model.SeitenJson.schreibe(seiten, 1))!!
        assertEquals(seiten, e.seiten)
        assertEquals(1, e.aktiv)
        assertNull(de.oejendorferdamm.dammzahlen.model.SeitenJson.lies("kaputt"))
    }
}
