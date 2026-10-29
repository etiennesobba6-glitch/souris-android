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
            text = "1. Appuie sur le bouton ci-dessous.\n" +
                "2. Ouvre « Souris Android » dans la liste des services installés.\n" +
                "3. Active-le.\n\n" +
                "Un pointeur et un pavé tactile apparaîtront à l'écran."
            textSize = 16f
            setTextColor(Color.BLACK)
        }

        val button = Button(this).apply {
            text = "Ouvrir les réglages d'accessibilité"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(info)
        root.addView(button)
        setContentView(root)
    }
}
