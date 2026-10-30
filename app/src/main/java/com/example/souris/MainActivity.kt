package com.example.souris

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }

        val info = TextView(this).apply {
            text = "Activation :\n" +
                "1. Ouvre les réglages d'accessibilité (bouton ci-dessous).\n" +
                "2. Choisis « Souris Android » et active-le.\n\n" +
                "Utilisation :\n" +
                "• Bouton rond flottant : appui court = souris on/off, " +
                "appui long = arrêt complet, glisser = le déplacer.\n" +
                "• Pavé : glisser = déplacer, toucher = clic, appui long = clic long.\n" +
                "• La souris s'éteint seule après 2 minutes d'inactivité.\n\n"
            textSize = 16f
            setTextColor(Color.BLACK)
        }

        val settingsButton = Button(this).apply {
            text = "Ouvrir les réglages d'accessibilité"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val stopButton = Button(this).apply {
            text = "Arrêter la souris"
            setOnClickListener {
                val service = CursorService.instance
                if (service != null) {
                    service.stopMouse()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "La souris n'est pas active.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        root.addView(info)
        root.addView(settingsButton)
        root.addView(stopButton)
        setContentView(root)
    }
}
