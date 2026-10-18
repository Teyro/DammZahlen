/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.model

import kotlin.random.Random

/** Was für Aufgaben erzeugt werden. */
enum class AufgabenArt(val bezeichnung: String) {
    PLUS("Plus"), MINUS("Minus"), ERGAENZEN("Ergänzen"), VERDOPPELN("Verdoppeln"), HALBIEREN("Halbieren"), GEMISCHT("Gemischt")
}

enum class Uebergangswahl(val bezeichnung: String) { EGAL("egal"), MIT("mit Übergang"), OHNE("ohne Übergang") }

/**
 * Erzeugt Aufgaben passend zum Zahlenraum (10, 20, 100) und zur Wahl „mit/ohne Zehnerübergang“ –
 * ohne doppelte Aufgaben, Ergebnisse immer im Zahlenraum, keine „+ 0“-Aufgaben.
 */
object Generator {
    fun aufgaben(art: AufgabenArt, raum: Int, uebergang: Uebergangswahl, anzahl: Int, zufall: Random = Random.Default): List<Aufgabe> {
        val ergebnis = LinkedHashSet<Aufgabe>()
        var versuche = 0
        while (ergebnis.size < anzahl && versuche < 5000) {
            versuche++
            val wahl = if (art == AufgabenArt.GEMISCHT) listOf(AufgabenArt.PLUS, AufgabenArt.MINUS, AufgabenArt.ERGAENZEN).random(zufall) else art
            val a = when (wahl) {
                AufgabenArt.PLUS -> {
                    val x = zufall.nextInt(1, raum)
                    val y = zufall.nextInt(1, raum - x + 1)
                    Aufgabe(0, 0f, 0f, x, y, Rechenart.PLUS, 2)
                }
                AufgabenArt.MINUS -> {
                    val x = zufall.nextInt(2, raum + 1)
                    val y = zufall.nextInt(1, x)
                    Aufgabe(0, 0f, 0f, x, y, Rechenart.MINUS, 2)
                }
                AufgabenArt.ERGAENZEN -> {
                    val x = zufall.nextInt(1, raum)
                    val y = zufall.nextInt(1, raum - x + 1)
                    Aufgabe(0, 0f, 0f, x, y, Rechenart.PLUS, 1)
                }
                AufgabenArt.VERDOPPELN -> Aufgabe(0, 0f, 0f, zufall.nextInt(1, raum / 2 + 1), 0, Rechenart.DOPPELT, 2)
                AufgabenArt.HALBIEREN -> Aufgabe(0, 0f, 0f, 2 * zufall.nextInt(1, raum / 2 + 1), 0, Rechenart.HAELFTE, 2)
                AufgabenArt.GEMISCHT -> continue
            }
            if (a.ergebnis !in 0..raum) continue
            val plus = a.art != Rechenart.MINUS
            val passt = when (uebergang) {
                Uebergangswahl.EGAL -> true
                Uebergangswahl.MIT -> a.art in setOf(Rechenart.PLUS, Rechenart.MINUS) && Uebergang.mitUebergang(a.a, a.b, plus)
                Uebergangswahl.OHNE -> a.art !in setOf(Rechenart.PLUS, Rechenart.MINUS) || !Uebergang.mitUebergang(a.a, a.b, plus)
            }
            if (passt) ergebnis.add(a)
        }
        return ergebnis.toList()
    }

    /** Zahlenhaus: links 0 … n vorgegeben (oder leer), rechts leer – bei großen Dachzahlen höchstens 11 Stockwerke. */
    fun zahlenhaus(dach: Int, linksVorgegeben: Boolean, zufall: Random = Random.Default): List<Zelle> {
        val links = if (dach <= 10) (0..dach).toList() else (0..dach).shuffled(zufall).take(11).sorted()
        return links.flatMap { l -> listOf(if (linksVorgegeben) Zelle(l, fest = true) else Zelle(), Zelle()) }
    }

    /** Zahlenmauer: unten vorgegebene Zahlen, so dass der Deckstein höchstens [raum] ist. */
    fun zahlenmauer(stufen: Int, raum: Int, zufall: Random = Random.Default): List<Zelle> {
        repeat(500) {
            val unten = List(stufen) { zufall.nextInt(0, maxOf(2, raum / (1 shl (stufen - 1)) + 1)) }
            var reihe = unten
            while (reihe.size > 1) reihe = reihe.zipWithNext { x, y -> x + y }
            if (reihe[0] in 1..raum) {
                val zellen = ArrayList<Zelle>()
                unten.forEach { zellen.add(Zelle(it, fest = true)) }
                repeat(stufen * (stufen + 1) / 2 - stufen) { zellen.add(Zelle()) }
                return zellen
            }
        }
        return List(stufen * (stufen + 1) / 2) { if (it < stufen) Zelle(1, true) else Zelle() }
    }
}
