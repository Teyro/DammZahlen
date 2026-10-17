/*
 * DammZahlen – Copyright (C) 2026 Teyro – GNU GPL v3 oder neuer, siehe LICENSE
 */
package de.oejendorferdamm.dammzahlen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oejendorferdamm.dammzahlen.R

/*
 * Farben und Bausteine wie bei DammBoard (dem Vorbild der CTOUCH-Tafel-App): runde, hellgraue
 * Knöpfe ohne Rahmen, das aktive Werkzeug dunkler grau, Panels hellgrau mit leichtem Schatten.
 * Alle Maße sind Vorbild-Pixel einer 1920 × 1080-Tafel (siehe Skalierung.kt).
 */
val KnopfFarbe = Color(0xFFEBEBEB)
val KnopfAktiv = Color(0xFFA7A7A7)
val SymbolFarbe = Color(0xFF3F3F3F)
val SymbolFarbeSchwach = Color(0xFFB0B0B0)
val TextFarbe = Color(0xFF333333)
val TextFarbeSchwach = Color(0xFF777777)
val PanelFarbe = Color(0xFFEDEDED)
val PanelAuswahl = Color(0xFFBDBDBD)
val AkzentBlau = Color(0xFF1E6FD9)
val SchliessenRot = Color(0xFFC62828)
val HinweisRot = Color(0xFFE0402E)

val KnopfDurchmesser = 67.dp
val KnopfAbstand = 18.dp

/** Die Grundschrift (Christian Urff, CC BY 3.0) – so schreiben die Kinder in Hamburg. */
val Grundschrift = FontFamily(Font(R.font.grundschrift_regular), Font(R.font.grundschrift_bold, FontWeight.Bold))

@Composable
fun RundKnopf(
    beschreibung: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ausgewaehlt: Boolean = false,
    aktiviert: Boolean = true,
    inhalt: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(KnopfDurchmesser)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(if (ausgewaehlt) KnopfAktiv else KnopfFarbe)
            .clickable(enabled = aktiviert, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
        content = inhalt
    )
}

/** Panel: hellgrau, abgerundet, fängt Berührungen ab (sonst landen sie auf der Tafel). */
@Composable
fun PanelRahmen(modifier: Modifier = Modifier, inhalt: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(PanelFarbe)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .padding(20.dp)
    ) { inhalt() }
}

/** Breiter Knopf mit Text (in Panels und Fenstern). */
@Composable
fun TextKnopf(text: String, modifier: Modifier = Modifier, hervorgehoben: Boolean = false, schrift: TextUnit = 20.sp, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (hervorgehoben) AkzentBlau else Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (hervorgehoben) Color.White else TextFarbe, fontSize = schrift, fontWeight = FontWeight.SemiBold)
    }
}

enum class Symbol { MENUE, SCHLOSS, SCHLOSS_OFFEN, MATERIAL, UEBERGANG, BLITZ, TERM, STIFT, PAPIERKORB, RUECKGAENGIG, ZURUECKSETZEN, LINKS, RECHTS, PLUS, SCHLIESSEN }

/** Schlichte Symbole, selbst gezeichnet – keine zusätzliche Bibliothek. */
@Composable
fun SymbolBild(symbol: Symbol, farbe: Color, modifier: Modifier = Modifier.size(36.dp)) {
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
        val dick = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        fun linie(a: Offset, b: Offset) = drawLine(farbe, a, b, s * 0.09f, StrokeCap.Round)
        when (symbol) {
            Symbol.MENUE -> { linie(p(0.18f, 0.28f), p(0.82f, 0.28f)); linie(p(0.18f, 0.5f), p(0.82f, 0.5f)); linie(p(0.18f, 0.72f), p(0.82f, 0.72f)) }
            Symbol.SCHLOSS, Symbol.SCHLOSS_OFFEN -> {
                drawRoundRect(farbe, p(0.2f, 0.45f), Size(s * 0.6f, s * 0.42f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f))
                val bogen = Path().apply {
                    moveTo(p(0.32f, 0.45f).x, p(0.32f, 0.45f).y)
                    lineTo(p(0.32f, 0.3f).x, p(0.32f, 0.3f).y)
                    cubicTo(p(0.32f, 0.1f).x, p(0.32f, 0.1f).y, p(0.68f, 0.1f).x, p(0.68f, 0.1f).y, p(0.68f, 0.3f).x, p(0.68f, 0.3f).y)
                    if (symbol == Symbol.SCHLOSS) lineTo(p(0.68f, 0.45f).x, p(0.68f, 0.45f).y)
                }
                drawPath(bogen, farbe, style = dick)
            }
            Symbol.MATERIAL -> {
                for (r in 0..1) for (c in 0..2) drawCircle(farbe, s * 0.09f, p(0.25f + c * 0.25f, 0.38f + r * 0.25f))
                drawRoundRect(farbe, p(0.1f, 0.24f), Size(s * 0.8f, s * 0.53f), androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = Stroke(s * 0.05f))
            }
            Symbol.UEBERGANG -> {
                val bogen = Path().apply { moveTo(p(0.15f, 0.7f).x, p(0.15f, 0.7f).y); cubicTo(p(0.25f, 0.2f).x, p(0.25f, 0.2f).y, p(0.65f, 0.2f).x, p(0.65f, 0.2f).y, p(0.8f, 0.62f).x, p(0.8f, 0.62f).y) }
                drawPath(bogen, farbe, style = dick)
                linie(p(0.8f, 0.62f), p(0.66f, 0.55f)); linie(p(0.8f, 0.62f), p(0.86f, 0.46f))
                linie(p(0.1f, 0.85f), p(0.9f, 0.85f))
            }
            Symbol.BLITZ -> drawPath(Path().apply {
                moveTo(p(0.58f, 0.08f).x, p(0.58f, 0.08f).y); lineTo(p(0.25f, 0.55f).x, p(0.25f, 0.55f).y); lineTo(p(0.48f, 0.55f).x, p(0.48f, 0.55f).y)
                lineTo(p(0.4f, 0.92f).x, p(0.4f, 0.92f).y); lineTo(p(0.76f, 0.42f).x, p(0.76f, 0.42f).y); lineTo(p(0.53f, 0.42f).x, p(0.53f, 0.42f).y); close()
            }, farbe)
            Symbol.TERM -> {
                linie(p(0.14f, 0.5f), p(0.42f, 0.5f)); linie(p(0.28f, 0.36f), p(0.28f, 0.64f))
                linie(p(0.58f, 0.42f), p(0.86f, 0.42f)); linie(p(0.58f, 0.58f), p(0.86f, 0.58f))
            }
            Symbol.STIFT -> {
                linie(p(0.25f, 0.75f), p(0.75f, 0.25f))
                drawPath(Path().apply { moveTo(p(0.18f, 0.82f).x, p(0.18f, 0.82f).y); lineTo(p(0.22f, 0.66f).x, p(0.22f, 0.66f).y); lineTo(p(0.34f, 0.78f).x, p(0.34f, 0.78f).y); close() }, farbe)
            }
            Symbol.PAPIERKORB -> {
                linie(p(0.18f, 0.28f), p(0.82f, 0.28f)); linie(p(0.4f, 0.18f), p(0.6f, 0.18f))
                drawPath(Path().apply { moveTo(p(0.26f, 0.32f).x, p(0.26f, 0.32f).y); lineTo(p(0.32f, 0.86f).x, p(0.32f, 0.86f).y); lineTo(p(0.68f, 0.86f).x, p(0.68f, 0.86f).y); lineTo(p(0.74f, 0.32f).x, p(0.74f, 0.32f).y) }, farbe, style = dick)
            }
            Symbol.RUECKGAENGIG, Symbol.ZURUECKSETZEN -> {
                val bogen = Path().apply { moveTo(p(0.3f, 0.4f).x, p(0.3f, 0.4f).y); cubicTo(p(0.55f, 0.22f).x, p(0.55f, 0.22f).y, p(0.9f, 0.4f).x, p(0.9f, 0.4f).y, p(0.78f, 0.72f).x, p(0.78f, 0.72f).y) }
                drawPath(bogen, farbe, style = dick)
                linie(p(0.3f, 0.4f), p(0.42f, 0.22f)); linie(p(0.3f, 0.4f), p(0.5f, 0.48f))
                if (symbol == Symbol.ZURUECKSETZEN) drawCircle(farbe, s * 0.07f, p(0.3f, 0.78f))
            }
            Symbol.LINKS -> { linie(p(0.62f, 0.2f), p(0.35f, 0.5f)); linie(p(0.35f, 0.5f), p(0.62f, 0.8f)) }
            Symbol.RECHTS -> { linie(p(0.38f, 0.2f), p(0.65f, 0.5f)); linie(p(0.65f, 0.5f), p(0.38f, 0.8f)) }
            Symbol.PLUS -> { linie(p(0.5f, 0.2f), p(0.5f, 0.8f)); linie(p(0.2f, 0.5f), p(0.8f, 0.5f)) }
            Symbol.SCHLIESSEN -> { linie(p(0.25f, 0.25f), p(0.75f, 0.75f)); linie(p(0.75f, 0.25f), p(0.25f, 0.75f)) }
        }
    }
}
