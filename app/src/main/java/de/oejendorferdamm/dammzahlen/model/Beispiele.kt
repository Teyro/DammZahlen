/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.model

/**
 * Fertige Vorlagen für den Start – typische Aufgaben aus Klasse 1 und 2. Ids sind nur vorläufig;
 * beim Einfügen bekommt jedes Teil eine neue.
 */
object Beispiele {
    data class Beispiel(val name: String, val seite: () -> Seite)

    private var id = 0L
    private fun n() = ++id

    private fun busMit(unten: Int, oben: Int): List<Teil> {
        val bus = Bus(n(), 520f, 160f)
        val plaetze = Geometrie.plaetze(bus)
        return listOf<Teil>(bus) + (0 until unten).map { Figur(n(), plaetze[it].x, plaetze[it].y, it) } +
            (0 until oben).map { Figur(n(), plaetze[10 + it].x, plaetze[10 + it].y, it + 4) }
    }

    private fun feldMit(art: FeldArt, x: Float, y: Float, rot: Int, blau: Int): List<Teil> {
        val f = Feld(n(), x, y, art)
        val p = Geometrie.plaetze(f)
        return listOf<Teil>(f) + (0 until rot + blau).map { Plaettchen(n(), p[it].x, p[it].y, rot = it < rot) }
    }

    val alle = listOf(
        Beispiel("Bus: Wie viele Kinder fahren mit?") {
            Seite(busMit(7, 5) + Karte(n(), 640f, 640f, "Wie viele Kinder sind im Bus?"))
        },
        Beispiel("Bus: 3 Kinder steigen aus") {
            Seite(busMit(8, 4) + Karte(n(), 600f, 640f, "12 Kinder. 3 steigen aus. Wie viele bleiben?"))
        },
        Beispiel("Zehnerfeld: Zerlegungen der 10") {
            Seite(feldMit(FeldArt.ZEHNER, 780f, 200f, 10, 0) + Karte(n(), 620f, 460f, "Wende Plättchen: 10 = ? + ?"))
        },
        Beispiel("Zwanzigerfeld: 9 + 4") {
            Seite(feldMit(FeldArt.ZWANZIGER, 620f, 200f, 9, 0) + Karte(n(), 760f, 440f, "9 + 4 = ?") + Strich(n(), 270f, 560f, 0, 20))
        },
        Beispiel("Stellenwert: Lege 47") {
            Seite(listOf(Stellentafel(n(), 660f, 160f), Karte(n(), 1320f, 200f, "Lege 47")))
        },
        Beispiel("Zahlenhaus der 10") {
            Seite(listOf(Zahlenhaus(n(), 820f, 40f, 10, Generator.zahlenhaus(10, true))))
        },
        Beispiel("Zahlenmauer bis 20") {
            Seite(listOf(Zahlenmauer(n(), 780f, 200f, 3, Generator.zahlenmauer(3, 20))))
        },
        Beispiel("Rechenrahmen: Zeige 16") {
            Seite(listOf(Rahmen(n(), 600f, 300f, 2), Karte(n(), 800f, 160f, "Zeige 16")))
        },
        Beispiel("Zahlenstrahl: Wo liegt 37?") {
            Seite(listOf(Strahl(n(), 170f, 400f, 100), Karte(n(), 780f, 200f, "Wo liegt 37?")))
        }
    )
}
