/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzahlen.BuildConfig
import de.oejendorferdamm.dammzahlen.data.Ablage
import de.oejendorferdamm.dammzahlen.data.Einstellungen
import de.oejendorferdamm.dammzahlen.model.Karte
import de.oejendorferdamm.dammzahlen.model.Uebergang
import de.oejendorferdamm.dammzahlen.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Panel { MATERIAL, STIFT, BLITZ }

@OptIn(FlowPreview::class)
@Composable
fun DammZahlenApp(einstellungen: Einstellungen) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zustand = remember {
        val gespeichert = Ablage.laden(context)
        TafelZustand(gespeichert?.seiten ?: emptyList(), gespeichert?.aktiv ?: 0).also { it.terme = einstellungen.terme }
    }
    var hintergrund by remember { mutableIntStateOf(einstellungen.hintergrund) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var uebergangOffen by remember { mutableStateOf(false) }
    var einstellungenOffen by remember { mutableStateOf(false) }
    var karte by remember { mutableStateOf<Karte?>(null) }
    var neueKarte by remember { mutableStateOf(false) }
    var pinFrage by remember { mutableStateOf(false) }
    var leerenFrage by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<Update.Info?>(null) }

    // Still sichern, eine Sekunde nach der letzten Änderung
    LaunchedEffect(zustand) {
        snapshotFlow { zustand.seiten to zustand.aktiv }.debounce(1000).collect { (s, a) ->
            withContext(Dispatchers.IO) { Ablage.speichern(context.applicationContext, s, a) }
        }
    }
    // Updates: kurz nach dem Start prüfen (roter Punkt am Menü)
    LaunchedEffect(Unit) {
        if (einstellungen.autoUpdate) {
            delay(6000)
            Update.pruefen()?.takeIf { Update.istNeuer(it.version, BuildConfig.VERSION_NAME) }?.let { updateInfo = it }
        }
    }

    BackHandler {
        when {
            panel != null -> panel = null
            uebergangOffen -> uebergangOffen = false
            einstellungenOffen -> einstellungenOffen = false
            zustand.ablauf != null -> zustand.ablaufBeenden()
            else -> Unit // die Tafel schließt sich nicht versehentlich
        }
    }

    Box(Modifier.fillMaxSize()) {
        TafelAnsicht(zustand, hintergrund, onKarteBearbeiten = { karte = it }, modifier = Modifier.fillMaxSize())

        VorbildRaster(faktor = 1f) {
            Box(Modifier.fillMaxSize()) {
                // Kindermodus-Hinweis oben links
                if (zustand.kindermodus) {
                    Box(Modifier.align(Alignment.TopStart).padding(20.dp).clip(RoundedCornerShape(50)).background(Color(0xCCFFFFFF)).padding(horizontal = 22.dp, vertical = 10.dp)) {
                        Text("Kindermodus", color = TextFarbe, fontSize = 22.sp, fontFamily = Grundschrift, fontWeight = FontWeight.Bold)
                    }
                }

                // Übergangs-Ablauf: Schritt-Karte oben
                zustand.ablauf?.let { schritte ->
                    AblaufKarte(schritte[zustand.schritt].titel, zustand.schritt, schritte.size,
                        onZurueck = { zustand.schrittZurueck() }, onWeiter = { zustand.schrittVor() }, onFertig = { zustand.ablaufBeenden() },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
                }

                Leiste(
                    zustand = zustand, panel = panel, updatePunkt = updateInfo != null,
                    onPanel = { panel = if (panel == it) null else it },
                    onMenue = { panel = null; einstellungenOffen = true },
                    onUebergang = { panel = null; uebergangOffen = true },
                    onSchloss = {
                        panel = null
                        if (!zustand.kindermodus) zustand.kindermodusAn()
                        else if (einstellungen.hatPin) pinFrage = true else zustand.kindermodusAus()
                    },
                    onLeeren = { panel = null; leerenFrage = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                when (panel) {
                    Panel.MATERIAL -> MaterialPanel(
                        onWahl = { art -> panel = null; if (art == "karte") neueKarte = true else zustand.hinzufuegen(art) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp)
                    )
                    Panel.STIFT -> StiftPanel(zustand, onZu = { panel = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
                    Panel.BLITZ -> BlitzPanel(onWahl = { sek ->
                        panel = null
                        scope.launch {
                            // Augen auf: kurz zeigen, dann zudecken – „Wie viele?“
                            zustand.vorhang = true
                            delay(900)
                            zustand.vorhang = false
                            delay(sek * 1000L)
                            zustand.vorhang = true
                        }
                    }, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
                    null -> Unit
                }

                if (uebergangOffen) UebergangFenster(onAbbrechen = { uebergangOffen = false }) { a, b, plus ->
                    uebergangOffen = false
                    zustand.uebergangStarten(a, b, plus)
                }
                if (neueKarte) TextFenster("Karte / Aufgabe", "", onAbbrechen = { neueKarte = false }) { text ->
                    neueKarte = false
                    if (text.isNotBlank()) zustand.karteHinzufuegen(text)
                }
                karte?.let { k ->
                    TextFenster("Karte ändern (leer = löschen)", k.text, onAbbrechen = { karte = null }) { text ->
                        karte = null
                        zustand.karteAendern(k.id, text)
                    }
                }
                if (pinFrage) PinFenster("PIN eingeben", onAbbrechen = { pinFrage = false }) { pin ->
                    val ok = einstellungen.pinStimmt(pin)
                    if (ok) { pinFrage = false; zustand.kindermodusAus() }
                    ok
                }
                if (leerenFrage) Frage("Diese Seite leeren?", "Alles auf dieser Seite wird entfernt (Rückgängig geht).", "Leeren",
                    onNein = { leerenFrage = false }, onJa = { leerenFrage = false; zustand.seiteLeeren() })
                if (einstellungenOffen) EinstellungenFenster(
                    einstellungen, zustand, hintergrund, updateInfo,
                    onHintergrund = { hintergrund = it; einstellungen.hintergrund = it },
                    onSchliessen = { einstellungenOffen = false },
                    onUpdateGefunden = { updateInfo = it }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Leiste

@Composable
private fun Leiste(
    zustand: TafelZustand, panel: Panel?, updatePunkt: Boolean,
    onPanel: (Panel) -> Unit, onMenue: () -> Unit, onUebergang: () -> Unit, onSchloss: () -> Unit, onLeeren: () -> Unit,
    modifier: Modifier
) {
    val kind = zustand.kindermodus
    Row(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        // links: Menü, Kindermodus
        Row(horizontalArrangement = Arrangement.spacedBy(KnopfAbstand)) {
            if (!kind) Box {
                RundKnopf("Menü", onMenue) { SymbolBild(Symbol.MENUE, SymbolFarbe) }
                if (updatePunkt) Box(Modifier.align(Alignment.TopEnd).size(18.dp).background(HinweisRot, CircleShape))
            }
            RundKnopf(if (kind) "Kindermodus beenden" else "Kindermodus", onSchloss, ausgewaehlt = kind) {
                SymbolBild(if (kind) Symbol.SCHLOSS else Symbol.SCHLOSS_OFFEN, SymbolFarbe)
            }
        }
        Spacer(Modifier.weight(1f))
        // Mitte: Werkzeuge
        Row(horizontalArrangement = Arrangement.spacedBy(KnopfAbstand)) {
            if (!kind) {
                RundKnopf("Material", { onPanel(Panel.MATERIAL) }, ausgewaehlt = panel == Panel.MATERIAL) { SymbolBild(Symbol.MATERIAL, SymbolFarbe) }
                RundKnopf("Zehnerübergang", onUebergang) { SymbolBild(Symbol.UEBERGANG, SymbolFarbe) }
                RundKnopf("Blitzblick", { onPanel(Panel.BLITZ) }, ausgewaehlt = panel == Panel.BLITZ, aktiviert = zustand.hatBehaelter()) {
                    SymbolBild(Symbol.BLITZ, if (zustand.hatBehaelter()) SymbolFarbe else SymbolFarbeSchwach)
                }
                RundKnopf("Rechnung zeigen", { zustand.terme = !zustand.terme }, ausgewaehlt = zustand.terme) { SymbolBild(Symbol.TERM, SymbolFarbe) }
            }
            RundKnopf("Stift", {
                if (zustand.werkzeug == Werkzeug.STIFT && panel != Panel.STIFT) onPanel(Panel.STIFT)
                else zustand.werkzeug = if (zustand.werkzeug == Werkzeug.STIFT) Werkzeug.HAND else Werkzeug.STIFT
            }, ausgewaehlt = zustand.werkzeug == Werkzeug.STIFT) { SymbolBild(Symbol.STIFT, SymbolFarbe) }
            if (!kind) RundKnopf("Seite leeren", onLeeren) { SymbolBild(Symbol.PAPIERKORB, SymbolFarbe) }
            RundKnopf("Rückgängig", { zustand.rueckgaengig() }, aktiviert = zustand.kannZurueck) {
                SymbolBild(Symbol.RUECKGAENGIG, if (zustand.kannZurueck) SymbolFarbe else SymbolFarbeSchwach)
            }
            if (kind) RundKnopf("Zurücksetzen", { zustand.zuruecksetzen() }) { SymbolBild(Symbol.ZURUECKSETZEN, SymbolFarbe) }
        }
        Spacer(Modifier.weight(1f))
        // rechts: Seiten
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(50)).background(KnopfFarbe)) {
            Box(Modifier.size(KnopfDurchmesser).clickable(enabled = zustand.aktiv > 0) { zustand.zuSeite(zustand.aktiv - 1) }, contentAlignment = Alignment.Center) {
                SymbolBild(Symbol.LINKS, if (zustand.aktiv > 0) SymbolFarbe else SymbolFarbeSchwach)
            }
            Text("${zustand.aktiv + 1}/${zustand.seiten.size}", color = TextFarbe, fontSize = 24.sp, fontFamily = Grundschrift, modifier = Modifier.padding(horizontal = 6.dp))
            Box(Modifier.size(KnopfDurchmesser).clickable(enabled = zustand.aktiv < zustand.seiten.lastIndex) { zustand.zuSeite(zustand.aktiv + 1) }, contentAlignment = Alignment.Center) {
                SymbolBild(Symbol.RECHTS, if (zustand.aktiv < zustand.seiten.lastIndex) SymbolFarbe else SymbolFarbeSchwach)
            }
        }
        if (!kind) {
            Spacer(Modifier.width(KnopfAbstand))
            RundKnopf("Neue Seite", { zustand.neueSeite() }) { SymbolBild(Symbol.PLUS, SymbolFarbe) }
        }
    }
}

// ---------------------------------------------------------------- Panels

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialPanel(onWahl: (String) -> Unit, modifier: Modifier) {
    val eintraege = listOf(
        "zehner" to "Zehnerfeld", "zwanziger" to "Zwanzigerfeld", "hunderter" to "Hunderterfeld",
        "bus" to "Bus mit 10 Kindern", "plaettchen10" to "10 Plättchen", "kinder10" to "10 Kinder",
        "rahmen20" to "Rechenrahmen 20", "rahmen100" to "Rechenrahmen 100",
        "strahl20" to "Zahlenstrahl 0–20", "strahl100" to "Zahlenstrahl 0–100", "strich" to "Rechenstrich", "karte" to "Karte / Aufgabe …"
    )
    PanelRahmen(modifier.widthIn(max = 1100.dp)) {
        Column {
            Text("Material auf die Tafel legen", color = TextFarbe, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Plättchen und Kinder liegen links im Vorrat – einfach herausziehen. Zurück in den Vorrat = weg.", color = TextFarbeSchwach, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                eintraege.forEach { (art, name) -> TextKnopf(name, schrift = 21.sp) { onWahl(art) } }
            }
        }
    }
}

@Composable
private fun StiftPanel(zustand: TafelZustand, onZu: () -> Unit, modifier: Modifier) {
    val farben = listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFFE53935, 0xFF1E6FD9, 0xFF43A047, 0xFF212121).map { it.toInt() }
    PanelRahmen(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            farben.forEach { f ->
                Box(
                    Modifier.size(54.dp).clip(CircleShape).background(Color(f))
                        .border(if (zustand.stiftFarbe == f) 5.dp else 2.dp, if (zustand.stiftFarbe == f) AkzentBlau else Color(0xFF9E9E9E), CircleShape)
                        .clickable { zustand.stiftFarbe = f; onZu() }
                )
            }
            Spacer(Modifier.width(10.dp))
            TextKnopf("Zeichnung löschen") { zustand.zeichnungLoeschen(); onZu() }
        }
    }
}

@Composable
private fun BlitzPanel(onWahl: (Int) -> Unit, modifier: Modifier) {
    PanelRahmen(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Blitzblick: kurz zeigen, dann zudecken – „Wie viele?“", color = TextFarbe, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Antippen deckt wieder auf.", color = TextFarbeSchwach, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 2, 3, 5).forEach { s -> TextKnopf("$s Sek.", hervorgehoben = s == 2) { onWahl(s) } }
            }
        }
    }
}

@Composable
private fun AblaufKarte(titel: String, schritt: Int, anzahl: Int, onZurueck: () -> Unit, onWeiter: () -> Unit, onFertig: () -> Unit, modifier: Modifier) {
    Row(
        modifier.shadow(6.dp, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RundKnopf("Schritt zurück", onZurueck, aktiviert = schritt > 0) { SymbolBild(Symbol.LINKS, if (schritt > 0) SymbolFarbe else SymbolFarbeSchwach) }
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Schritt ${schritt + 1} von $anzahl", color = TextFarbeSchwach, fontSize = 18.sp)
            Text(titel, color = TextFarbe, fontSize = 34.sp, fontFamily = Grundschrift, fontWeight = FontWeight.Bold)
        }
        if (schritt < anzahl - 1) TextKnopf("Weiter", hervorgehoben = true, schrift = 24.sp, onClick = onWeiter)
        else TextKnopf("Fertig", hervorgehoben = true, schrift = 24.sp, onClick = onFertig)
        Spacer(Modifier.width(10.dp))
        RundKnopf("Ablauf schließen", onFertig) { SymbolBild(Symbol.SCHLIESSEN, SymbolFarbe) }
    }
}

// ---------------------------------------------------------------- Fenster

@Composable
fun Fenster(titel: String, onAbbrechen: () -> Unit, breite: Int = 900, inhalt: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null, onClick = onAbbrechen),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = breite.dp).fillMaxWidth(0.9f).shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(PanelFarbe).clickable(remember { MutableInteractionSource() }, null) {}.padding(32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titel, color = TextFarbe, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RundKnopf("Schließen", onAbbrechen) { SymbolBild(Symbol.SCHLIESSEN, SymbolFarbe) }
            }
            Spacer(Modifier.height(16.dp))
            inhalt()
        }
    }
}

@Composable
private fun Zaehler(wert: Int, bereich: IntRange, onAendern: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RundKnopf("weniger", { onAendern((wert - 1).coerceIn(bereich)) }) { Text("−", fontSize = 34.sp, color = SymbolFarbe) }
        Text("$wert", color = TextFarbe, fontSize = 64.sp, fontFamily = Grundschrift, fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp).padding(horizontal = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RundKnopf("mehr", { onAendern((wert + 1).coerceIn(bereich)) }) { Text("+", fontSize = 34.sp, color = SymbolFarbe) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UebergangFenster(onAbbrechen: () -> Unit, onStart: (Int, Int, Boolean) -> Unit) {
    var a by remember { mutableIntStateOf(8) }
    var b by remember { mutableIntStateOf(5) }
    var plus by remember { mutableStateOf(true) }
    Fenster("Zehnerübergang zeigen", onAbbrechen, breite = 1100) {
        Text("Schritt für Schritt: erst bis zum vollen Zehner, dann der Rest – mit Plättchen und Rechenstrich. Bis 20 im Zwanzigerfeld, darüber im Hunderterfeld.",
            color = TextFarbeSchwach, fontSize = 19.sp)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Zaehler(a, 0..100) { a = it }
            Spacer(Modifier.width(20.dp))
            Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White)) {
                for ((zeichen, p) in listOf("+" to true, "−" to false)) {
                    Box(Modifier.size(70.dp).clip(CircleShape).background(if (plus == p) AkzentBlau else Color.Transparent).clickable { plus = p }, contentAlignment = Alignment.Center) {
                        Text(zeichen, fontSize = 40.sp, color = if (plus == p) Color.White else TextFarbe, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.width(20.dp))
            Zaehler(b, 1..100) { b = it }
        }
        Spacer(Modifier.height(14.dp))
        val gueltig = Uebergang.gueltig(a, b, plus)
        Text(
            when {
                !gueltig -> "Ergebnis muss zwischen 0 und 100 liegen."
                Uebergang.mitUebergang(a, b, plus) -> "mit Zehnerübergang ✓"
                else -> "ohne Zehnerübergang"
            },
            color = if (gueltig) TextFarbe else HinweisRot, fontSize = 22.sp, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text("Beispiele", color = TextFarbeSchwach, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple(8, 5, true), Triple(7, 6, true), Triple(9, 4, true), Triple(13, 5, false), Triple(15, 7, false), Triple(38, 7, true), Triple(52, 4, false), Triple(27, 15, true)).forEach { (x, y, p) ->
                TextKnopf("$x ${if (p) "+" else "−"} $y", schrift = 22.sp) { a = x; b = y; plus = p }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextKnopf("Abbrechen", onClick = onAbbrechen)
            Spacer(Modifier.width(12.dp))
            TextKnopf("Zeigen", hervorgehoben = gueltig) { if (gueltig) onStart(a, b, plus) }
        }
    }
}

@Composable
private fun TextFenster(titel: String, start: String, onAbbrechen: () -> Unit, onFertig: (String) -> Unit) {
    var text by remember { mutableStateOf(start) }
    Fenster(titel, onAbbrechen) {
        OutlinedTextField(text, { text = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("z. B. 8 + 5 = ?", fontSize = 18.sp) }, textStyle = TextStyle(fontSize = 34.sp, fontFamily = Grundschrift))
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextKnopf("Abbrechen", onClick = onAbbrechen)
            Spacer(Modifier.width(12.dp))
            TextKnopf("Fertig", hervorgehoben = true) { onFertig(text) }
        }
    }
}

/** PIN abfragen; [onPin] gibt zurück, ob sie stimmte. */
@Composable
private fun PinFenster(titel: String, onAbbrechen: () -> Unit, onPin: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var falsch by remember { mutableStateOf(false) }
    Fenster(titel, onAbbrechen, breite = 700) {
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); falsch = false }, Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(fontSize = 34.sp))
        if (falsch) Text("Falsche PIN", color = HinweisRot, fontSize = 20.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextKnopf("Abbrechen", onClick = onAbbrechen)
            Spacer(Modifier.width(12.dp))
            TextKnopf("OK", hervorgehoben = true) { if (!onPin(pin)) { falsch = true; pin = "" } }
        }
    }
}

@Composable
private fun Frage(titel: String, text: String, ja: String, onNein: () -> Unit, onJa: () -> Unit) {
    Fenster(titel, onNein, breite = 800) {
        Text(text, color = TextFarbe, fontSize = 22.sp)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextKnopf("Abbrechen", onClick = onNein)
            Spacer(Modifier.width(12.dp))
            TextKnopf(ja, hervorgehoben = true, onClick = onJa)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EinstellungenFenster(
    einstellungen: Einstellungen, zustand: TafelZustand, hintergrund: Int, updateInfo: Update.Info?,
    onHintergrund: (Int) -> Unit, onSchliessen: () -> Unit, onUpdateGefunden: (Update.Info?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoUpdate by remember { mutableStateOf(einstellungen.autoUpdate) }
    var hatPin by remember { mutableStateOf(einstellungen.hatPin) }
    var pinSetzen by remember { mutableStateOf(false) }
    var updateText by remember { mutableStateOf("") }
    var fortschritt by remember { mutableFloatStateOf(-1f) }
    Fenster("Einstellungen", onSchliessen, breite = 1000) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Tafel", color = AkzentBlau, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Einstellungen.HINTERGRUENDE.forEach { f ->
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color(f))
                        .border(if (f == hintergrund) 5.dp else 2.dp, if (f == hintergrund) AkzentBlau else Color(0xFF9E9E9E), CircleShape)
                        .clickable { onHintergrund(f) })
                }
            }
            Spacer(Modifier.height(10.dp))
            Zeile("Rechnung unter Feldern und Bus zeigen (z. B. „8 + 5 = 13“)", zustand.terme) { zustand.terme = it; einstellungen.terme = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextKnopf("Diese Seite löschen") { zustand.seiteLoeschen() }
            }

            Spacer(Modifier.height(24.dp))
            Text("Kindermodus", color = AkzentBlau, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Im Kindermodus können Kinder Plättchen und Figuren schieben und wenden – sonst nichts. „Zurücksetzen“ stellt die Aufgabe wieder her. " +
                if (hatPin) "Zum Beenden ist eine PIN nötig." else "Ohne PIN kann jede/r den Kindermodus beenden.", color = TextFarbeSchwach, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextKnopf(if (hatPin) "PIN ändern …" else "PIN festlegen …") { pinSetzen = true }
                if (hatPin) TextKnopf("PIN entfernen") { einstellungen.setzePin(null); hatPin = false }
            }

            Spacer(Modifier.height(24.dp))
            Text("Updates", color = AkzentBlau, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Zeile("Beim Start nach Updates suchen", autoUpdate) { autoUpdate = it; einstellungen.autoUpdate = it }
            Text("Installiert: ${BuildConfig.VERSION_NAME}" + (updateInfo?.let { " · Neu: ${it.version}" } ?: "") + (if (updateText.isNotEmpty()) " · $updateText" else ""),
                color = TextFarbeSchwach, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextKnopf("Jetzt suchen") {
                    updateText = "Sucht …"
                    scope.launch {
                        val i = Update.pruefen()
                        val neu = i?.takeIf { Update.istNeuer(it.version, BuildConfig.VERSION_NAME) }
                        onUpdateGefunden(neu)
                        updateText = when { i == null -> "GitHub nicht erreichbar"; neu == null -> "Auf dem neuesten Stand"; else -> "Neue Version ${neu.version}" }
                    }
                }
                updateInfo?.let { info ->
                    TextKnopf(if (fortschritt >= 0f) "Lädt … ${(fortschritt * 100).toInt()} %" else "Version ${info.version} installieren", hervorgehoben = true) {
                        if (!Update.darfInstallieren(context)) { Update.erlaubnisOeffnen(context); updateText = "Bitte „Apps installieren“ erlauben, dann noch einmal tippen"; return@TextKnopf }
                        if (fortschritt >= 0f) return@TextKnopf
                        fortschritt = 0f
                        scope.launch {
                            try {
                                val apk = Update.laden(context, info) { p -> fortschritt = p }
                                Update.installieren(context, apk)
                            } catch (e: Exception) {
                                updateText = e.message ?: "Fehler"
                            }
                            fortschritt = -1f
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Über DammZahlen", color = AkzentBlau, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Mathe-Legetafel für Klasse 1 und 2 (Bildungsplan Hamburg). Freie Software unter der GNU GPL v3, Quelltext: github.com/Teyro/DammZahlen.\n" +
                    "Schrift: Grundschrift von Christian Urff (www.urff.info), Lizenz CC BY 3.0.\n" +
                    "Es werden keine Namen oder Lernstände gespeichert – nur die Tafel selbst, auf diesem Gerät.",
                color = TextFarbeSchwach, fontSize = 18.sp
            )
        }
    }
    if (pinSetzen) {
        var erste by remember { mutableStateOf<String?>(null) }
        PinFenster(if (erste == null) "Neue PIN (4–8 Ziffern)" else "PIN wiederholen", onAbbrechen = { pinSetzen = false }) { pin ->
            when {
                pin.length < 4 -> false
                erste == null -> { erste = pin; true }
                erste == pin -> { einstellungen.setzePin(pin); hatPin = true; pinSetzen = false; true }
                else -> { erste = null; false }
            }
        }
    }
}

@Composable
private fun Zeile(text: String, an: Boolean, onAendern: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onAendern(!an) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = TextFarbe, fontSize = 20.sp, modifier = Modifier.weight(1f))
        Switch(checked = an, onCheckedChange = onAendern)
    }
}
