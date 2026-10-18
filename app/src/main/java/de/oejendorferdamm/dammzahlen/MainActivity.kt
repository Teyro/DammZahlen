/*
 * DammZahlen – Mathe-Legetafel für die Grundschule
 * Copyright (C) 2026 Teyro
 *
 * Dieses Programm ist freie Software: Sie können es unter den Bedingungen der GNU General Public
 * License, wie von der Free Software Foundation veröffentlicht, weitergeben und/oder verändern,
 * entweder gemäß Version 3 der Lizenz oder (nach Ihrer Wahl) jeder späteren Version.
 * Es wird OHNE JEDE GEWÄHRLEISTUNG bereitgestellt. Siehe LICENSE.
 */
package de.oejendorferdamm.dammzahlen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.oejendorferdamm.dammzahlen.data.Einstellungen
import de.oejendorferdamm.dammzahlen.ui.AkzentBlau
import de.oejendorferdamm.dammzahlen.ui.DammZahlenApp

/** Die Tafel im Vollbild – auf dem Board sollen keine Systemleisten ablenken. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        vollbild()
        val einstellungen = Einstellungen(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = AkzentBlau)) {
                DammZahlenApp(einstellungen)
            }
        }
    }

    override fun onStop() {
        de.oejendorferdamm.dammzahlen.ui.Sicherung.jetzt?.invoke()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        vollbild()
    }

    private fun vollbild() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
