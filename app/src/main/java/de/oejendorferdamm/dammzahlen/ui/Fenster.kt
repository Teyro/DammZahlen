/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzahlen.R
import de.oejendorferdamm.dammzahlen.data.Einstellungen
import de.oejendorferdamm.dammzahlen.data.Vorlagen
import de.oejendorferdamm.dammzahlen.model.AufgabenArt
import de.oejendorferdamm.dammzahlen.model.Beispiele
import de.oejendorferdamm.dammzahlen.model.Bus
import de.oejendorferdamm.dammzahlen.model.Feld
import de.oejendorferdamm.dammzahlen.model.FeldArt
import de.oejendorferdamm.dammzahlen.model.Generator
import de.oejendorferdamm.dammzahlen.model.Geometrie
import de.oejendorferdamm.dammzahlen.model.Karte
import de.oejendorferdamm.dammzahlen.model.Rahmen
import de.oejendorferdamm.dammzahlen.model.Seite
import de.oejendorferdamm.dammzahlen.model.Stellentafel
import de.oejendorferdamm.dammzahlen.model.Strahl
import de.oejendorferdamm.dammzahlen.model.Strich
import de.oejendorferdamm.dammzahlen.model.Teil
import de.oejendorferdamm.dammzahlen.model.Uebergangswahl
import de.oejendorferdamm.dammzahlen.model.Zahlenhaus
import de.oejendorferdamm.dammzahlen.model.Zahlenmauer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Ziffernblock

/** Große Ziffern zum Eintippen – für Kinderhände auf der Tafel. Die Lehrkraft kann Zahlen als Vorgabe festlegen. */
@Composable
fun Ziffernblock(start: Int?, lehrkraft: Boolean, onAbbrechen: () -> Unit, onFertig: (Int?, Boolean) -> Unit) {
    var text by remember { mutableStateOf(start?.toString() ?: "") }
    var fest by remember { mutableStateOf(lehrkraft) }
    Fenster("Zahl eintragen", onAbbrechen, breite = 620) {
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(18.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Text(text.ifEmpty { " " }, fontSize = 80.sp, fontFamily = Grundschrift, fontWeight = FontWeight.Bold, color = Color(0xFF1E5FC0))
        }
        Spacer(Modifier.height(16.dp))
        val tasten = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓"))
        tasten.forEach { reihe ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                reihe.forEach { taste ->
                    val hervor = taste == "✓"
                    Box(
                        Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (hervor) AkzentBlau else Color.White)
                            .clickable {
                                when (taste) {
                                    "⌫" -> text = text.dropLast(1)
                                    "✓" -> onFertig(text.toIntOrNull(), fest && lehrkraft)
                                    else -> if (text.length < 3) text = (text + taste).trimStart('0').ifEmpty { "0" }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(taste, fontSize = 48.sp, fontFamily = Grundschrift, color = if (hervor) Color.White else TextFarbe, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (lehrkraft) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { fest = !fest }) {
            Text("Als Vorgabe (Kinder können sie nicht ändern)", fontSize = 20.sp, color = TextFarbe, modifier = Modifier.weight(1f))
            Switch(checked = fest, onCheckedChange = { fest = it })
        }
    }
}

// ---------------------------------------------------------------- Behälter-Menü

@Composable
fun BehaelterMenue(teil: Teil, zustand: TafelZustand, onZu: () -> Unit) {
    val titel = when (teil) {
        is Feld -> teil.art.bezeichnung
        is Bus -> "Bus"
        is Rahmen -> "Rechenrahmen"
        is Strahl -> "Zahlenstrahl"
        is Strich -> "Rechenstrich"
        is Stellentafel -> "Stellentafel"
        is Zahlenhaus -> "Zahlenhaus"
        is Zahlenmauer -> "Zahlenmauer"
        else -> "Material"
    }
    val plaetze = Geometrie.plaetze(teil).size
    var anzahl by remember { mutableIntStateOf(minOf(plaetze, if (teil is Bus) 10 else 10)) }
    var rot by remember { mutableIntStateOf(minOf(anzahl, 5)) }
    Fenster(titel, onZu, breite = 900) {
        if (plaetze > 0) {
            Text(if (teil is Bus) "Kinder einsteigen lassen" else "Mit Plättchen füllen (erst rote, dann blaue)", color = TextFarbeSchwach, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anzahl", fontSize = 22.sp, color = TextFarbe, modifier = Modifier.width(110.dp))
                KleinZaehler(anzahl, 0..plaetze) { anzahl = it; if (rot > it) rot = it }
                if (plaetze > 20) {
                    Spacer(Modifier.width(8.dp))
                    TextKnopf("+10", schrift = 20.sp) { anzahl = (anzahl + 10).coerceAtMost(plaetze); rot = minOf(rot, anzahl) }
                }
                if (teil !is Bus) {
                    Spacer(Modifier.width(30.dp))
                    Text("davon rot", fontSize = 22.sp, color = TextFarbe, modifier = Modifier.width(120.dp))
                    KleinZaehler(rot, 0..anzahl) { rot = it }
                    if (plaetze > 20) { Spacer(Modifier.width(8.dp)); TextKnopf("+10", schrift = 20.sp) { rot = (rot + 10).coerceAtMost(anzahl) } }
                }
                Spacer(Modifier.width(20.dp))
                TextKnopf("Füllen", hervorgehoben = true) { zustand.fuellen(teil.id, anzahl, rot); onZu() }
            }
            Spacer(Modifier.height(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextKnopf(if (teil is Zahlenhaus || teil is Zahlenmauer) "Eingaben löschen" else if (teil is Rahmen || teil is Strahl) "Zurücksetzen" else "Leeren") { zustand.leeren(teil.id); onZu() }
            if (teil is Feld) TextKnopf("Alle wenden") { zustand.wenden(teil.id); onZu() }
            TextKnopf("Verdoppeln") { zustand.duplizieren(teil.id); onZu() }
            TextKnopf("Entfernen") { zustand.entfernen(teil.id); onZu() }
        }
    }
}

@Composable
fun KleinZaehler(wert: Int, bereich: IntRange, onAendern: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RundKnopf("weniger", { onAendern((wert - 1).coerceIn(bereich)) }) { Text("−", fontSize = 32.sp, color = SymbolFarbe) }
        Text("$wert", fontSize = 40.sp, fontFamily = Grundschrift, fontWeight = FontWeight.Bold, color = TextFarbe, modifier = Modifier.width(90.dp), textAlign = TextAlign.Center)
        RundKnopf("mehr", { onAendern((wert + 1).coerceIn(bereich)) }) { Text("+", fontSize = 32.sp, color = SymbolFarbe) }
    }
}

// ---------------------------------------------------------------- Aufgaben-Generator

private enum class AufgabenReiter(val name_: String) { RECHNEN("Rechenaufgaben"), HAUS("Zahlenhaus"), MAUER("Zahlenmauer"), STELLEN("Stellenwert") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AufgabenFenster(zustand: TafelZustand, onZu: () -> Unit) {
    var reiter by remember { mutableStateOf(AufgabenReiter.RECHNEN) }
    var art by remember { mutableStateOf(AufgabenArt.PLUS) }
    var raum by remember { mutableIntStateOf(20) }
    var uebergang by remember { mutableStateOf(Uebergangswahl.EGAL) }
    var anzahl by remember { mutableIntStateOf(6) }
    var mitFeld by remember { mutableStateOf(true) }
    var dach by remember { mutableIntStateOf(10) }
    var linksVorgabe by remember { mutableStateOf(true) }
    var stufen by remember { mutableIntStateOf(3) }
    var hinweis by remember { mutableStateOf("") }
    Fenster("Aufgaben erzeugen", onZu, breite = 1200) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AufgabenReiter.entries.forEach { r -> Wahl(r.name_, reiter == r) { reiter = r } }
        }
        Spacer(Modifier.height(18.dp))
        when (reiter) {
            AufgabenReiter.RECHNEN -> {
                Zeilentitel("Art")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AufgabenArt.entries.forEach { a -> Wahl(a.bezeichnung, art == a) { art = a } }
                }
                Zeilentitel("Zahlenraum")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(10, 20, 100).forEach { z -> Wahl("bis $z", raum == z) { raum = z } } }
                if (art in setOf(AufgabenArt.PLUS, AufgabenArt.MINUS, AufgabenArt.ERGAENZEN, AufgabenArt.GEMISCHT) && raum > 10) {
                    Zeilentitel("Zehnerübergang")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Uebergangswahl.entries.forEach { u -> Wahl(u.bezeichnung, uebergang == u) { uebergang = u } } }
                }
                Zeilentitel("Anzahl")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(4, 6, 8, 10).forEach { n -> Wahl("$n", anzahl == n) { anzahl = n } } }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mitFeld = !mitFeld }) {
                    Switch(checked = mitFeld, onCheckedChange = { mitFeld = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Mit ${if (raum <= 10) "Zehnerfeld" else if (raum <= 20) "Zwanzigerfeld" else "Hunderterfeld"} zum Legen", fontSize = 20.sp, color = TextFarbe)
                }
            }
            AufgabenReiter.HAUS -> {
                Zeilentitel("Dachzahl")
                KleinZaehler(dach, 2..20) { dach = it }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { linksVorgabe = !linksVorgabe }) {
                    Switch(checked = linksVorgabe, onCheckedChange = { linksVorgabe = it })
                    Spacer(Modifier.width(10.dp))
                    Text("Linke Zahlen vorgeben (0, 1, 2 …)", fontSize = 20.sp, color = TextFarbe)
                }
            }
            AufgabenReiter.MAUER -> {
                Zeilentitel("Stufen")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(3, 4).forEach { s -> Wahl("$s Stufen", stufen == s) { stufen = s } } }
                Zeilentitel("Deckstein höchstens")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(10, 20, 100).forEach { z -> Wahl("$z", raum == z) { raum = z } } }
            }
            AufgabenReiter.STELLEN -> {
                Zeilentitel("Zahlenraum")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { listOf(20, 50, 99).forEach { z -> Wahl("bis $z", raum == z) { raum = z } } }
                Text("Eine Stellentafel mit der Aufgabe „Lege …“ – die Kinder legen Zehnerstangen und Einerwürfel.", fontSize = 18.sp, color = TextFarbeSchwach)
            }
        }
        if (hinweis.isNotEmpty()) Text(hinweis, color = HinweisRot, fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextKnopf("Abbrechen", onClick = onZu)
            Spacer(Modifier.width(12.dp))
            TextKnopf("Erzeugen", hervorgehoben = true) {
                when (reiter) {
                    AufgabenReiter.RECHNEN -> {
                        val liste = Generator.aufgaben(art, raum, uebergang, anzahl)
                        if (liste.isEmpty()) { hinweis = "Mit dieser Auswahl gibt es keine passenden Aufgaben."; return@TextKnopf }
                        zustand.aufgabenSeite(liste, if (!mitFeld) null else if (raum <= 10) FeldArt.ZEHNER else if (raum <= 20) FeldArt.ZWANZIGER else FeldArt.HUNDERTER)
                    }
                    AufgabenReiter.HAUS -> zustand.neueSeiteMit(Seite(listOf(Zahlenhaus(0, 820f, 30f, dach, Generator.zahlenhaus(dach, linksVorgabe)))))
                    AufgabenReiter.MAUER -> zustand.neueSeiteMit(Seite(listOf(Zahlenmauer(0, 960f - stufen * 60f, 180f, stufen, Generator.zahlenmauer(stufen, raum)))))
                    AufgabenReiter.STELLEN -> {
                        val zahl = (11..raum).random()
                        zustand.neueSeiteMit(Seite(listOf(Stellentafel(0, 660f, 160f), Karte(0, 1320f, 200f, "Lege $zahl"))))
                    }
                }
                onZu()
            }
        }
    }
}

@Composable
private fun Zeilentitel(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = TextFarbeSchwach, fontSize = 18.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
fun Wahl(text: String, gewaehlt: Boolean, onClick: () -> Unit) = TextKnopf(text, hervorgehoben = gewaehlt, schrift = 21.sp, onClick = onClick)

// ---------------------------------------------------------------- Vorlagen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VorlagenFenster(zustand: TafelZustand, einstellungen: Einstellungen, onZu: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var eigene by remember { mutableStateOf(Vorlagen.lokal(context)) }
    var ordner by remember { mutableStateOf(einstellungen.vorlagenOrdner?.let { Uri.parse(it) }) }
    var imOrdner by remember { mutableStateOf<List<Vorlagen.Eintrag>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var meldung by remember { mutableStateOf("") }
    val ordnerWahl = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try { Vorlagen.ordnerMerken(context, uri) } catch (_: Exception) {}
            einstellungen.vorlagenOrdner = uri.toString()
            ordner = uri
        }
    }
    LaunchedEffect(ordner) {
        imOrdner = ordner?.let { o -> try { Vorlagen.imOrdner(context, o) } catch (_: Exception) { meldung = "Ordner nicht erreichbar – bitte neu wählen."; emptyList() } } ?: emptyList()
    }
    Fenster("Vorlagen", onZu, breite = 1300) {
        Column(Modifier.heightIn(max = 760.dp).verticalScroll(rememberScrollState())) {
            Zeilentitel("Fertige Beispiele (als neue Seite öffnen)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Beispiele.alle.forEach { b -> TextKnopf(b.name, schrift = 20.sp) { zustand.neueSeiteMit(b.seite()); onZu() } }
            }
            Zeilentitel("Eigene Vorlagen auf diesem Board")
            if (eigene.isEmpty()) Text("Noch keine – unten die aktuelle Seite speichern.", color = TextFarbeSchwach, fontSize = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                eigene.forEach { e ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White), verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, fontSize = 20.sp, color = TextFarbe, modifier = Modifier.clickable {
                            Vorlagen.lokalLaden(e)?.let { zustand.neueSeiteMit(it); onZu() } ?: run { meldung = "„${e.name}“ lässt sich nicht öffnen." }
                        }.padding(start = 22.dp, top = 12.dp, bottom = 12.dp, end = 8.dp))
                        Text("✕", fontSize = 20.sp, color = TextFarbeSchwach, modifier = Modifier.clickable { Vorlagen.lokalLoeschen(e); eigene = Vorlagen.lokal(context) }.padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                }
            }
            Zeilentitel("Geteilter Ordner (für alle Boards, z. B. Cloud-Ordner)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextKnopf(if (ordner == null) "Ordner wählen …" else "Anderen Ordner wählen …", schrift = 20.sp) { try { ordnerWahl.launch(ordner) } catch (_: Exception) { meldung = "Keine Ordnerauswahl auf diesem Board." } }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                imOrdner.forEach { e ->
                    TextKnopf("📁 ${e.name}", schrift = 20.sp) {
                        scope.launch { Vorlagen.ausOrdnerLaden(context, e)?.let { zustand.neueSeiteMit(it); onZu() } ?: run { meldung = "„${e.name}“ lässt sich nicht öffnen." } }
                    }
                }
            }
            Zeilentitel("Aktuelle Seite als Vorlage speichern")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it.take(50) }, Modifier.weight(1f), singleLine = true, label = { Text("Name", fontSize = 18.sp) }, textStyle = TextStyle(fontSize = 26.sp))
                Spacer(Modifier.width(12.dp))
                TextKnopf("Auf dem Board", schrift = 20.sp) {
                    if (name.isNotBlank()) { Vorlagen.lokalSpeichern(context, name, zustand.seite); eigene = Vorlagen.lokal(context); meldung = "Gespeichert."; name = "" }
                }
                Spacer(Modifier.width(10.dp))
                TextKnopf("Im Ordner", hervorgehoben = ordner != null, schrift = 20.sp) {
                    val o = ordner
                    if (o == null) { meldung = "Bitte zuerst einen Ordner wählen."; return@TextKnopf }
                    if (name.isBlank()) return@TextKnopf
                    scope.launch {
                        meldung = if (Vorlagen.inOrdnerSpeichern(context, o, name, zustand.seite)) "Im Ordner gespeichert." else "Speichern im Ordner fehlgeschlagen."
                        imOrdner = try { Vorlagen.imOrdner(context, o) } catch (_: Exception) { imOrdner }
                        name = ""
                    }
                }
            }
            if (meldung.isNotEmpty()) Text(meldung, color = TextFarbeSchwach, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ---------------------------------------------------------------- Prüf-Ergebnis

/** Rückmeldung nach „Prüfen“: freundlich, mit Sternen bei allem richtig. Verschwindet von selbst. */
@Composable
fun PruefErgebnis(richtig: Int, gesamt: Int, onZu: () -> Unit, modifier: Modifier = Modifier) {
    val groesse = remember { Animatable(0.6f) }
    LaunchedEffect(richtig, gesamt) {
        groesse.snapTo(0.6f)
        groesse.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        delay(3500)
        onZu()
    }
    val alles = gesamt > 0 && richtig == gesamt
    Column(
        modifier.scale(groesse.value).shadow(12.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(Color.White)
            .clickable(onClick = onZu).padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (alles) "★ ★ ★" else if (richtig * 2 >= gesamt) "★ ★" else "★", color = Color(0xFFF2B705), fontSize = 56.sp)
        Text(
            when {
                gesamt == 0 -> "Noch nichts eingetragen"
                alles -> "Alles richtig – super!"
                else -> "$richtig von $gesamt richtig"
            },
            color = TextFarbe, fontSize = 44.sp, fontFamily = Grundschrift, fontWeight = FontWeight.Bold
        )
        if (!alles && gesamt > 0) Text("Die roten Kästchen noch einmal anschauen.", color = TextFarbeSchwach, fontSize = 22.sp)
    }
}

// ---------------------------------------------------------------- Material-Vorschau

/** Kleine Vorschau eines Materials (mit demselben Zeichner wie die Tafel). */
@Composable
fun MaterialVorschau(teile: List<Teil>, modifier: Modifier) {
    val context = LocalContext.current
    val zeichner = remember { Zeichner(context.resources.getFont(R.font.grundschrift_regular), context.resources.getFont(R.font.grundschrift_bold)) }
    Canvas(modifier) {
        if (teile.isEmpty()) return@Canvas
        // Rahmen um alles bestimmen und einpassen
        var links = Float.MAX_VALUE; var oben = Float.MAX_VALUE; var rechts = -Float.MAX_VALUE; var unten = -Float.MAX_VALUE
        for (t in teile) {
            val m = Geometrie.masse(t)
            val x0 = if (Geometrie.mitteBezogen(t)) t.x - m.breite / 2 else t.x
            val y0 = if (Geometrie.mitteBezogen(t)) t.y - m.hoehe / 2 else t.y
            links = minOf(links, x0); oben = minOf(oben, y0); rechts = maxOf(rechts, x0 + m.breite); unten = maxOf(unten, y0 + m.hoehe)
        }
        val b = (rechts - links).coerceAtLeast(1f)
        val h = (unten - oben).coerceAtLeast(1f)
        val e = minOf(size.width / b, size.height / h) * 0.92f
        drawIntoCanvas { c ->
            val n = c.nativeCanvas
            n.save()
            n.translate((size.width - b * e) / 2f, (size.height - h * e) / 2f)
            n.scale(e, e)
            n.translate(-links, -oben)
            zeichner.zeichne(n, Seite(teile), terme = false, vorhang = false, mitVorrat = false, papierkorb = false) { 1f }
            n.restore()
        }
    }
}
