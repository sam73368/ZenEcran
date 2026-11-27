package com.snapchat.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Imite la structure de Snapchat : titre de l'onglet en haut, barre d'onglets en bas avec ses identifiants. */
class MainActivity : Activity() {
    private lateinit var header: TextView
    private lateinit var body: TextView
    private val tabs = linkedMapOf(
        "Carte" to R.id.ngs_map_icon_container,
        "Chat" to R.id.ngs_chat_icon_container,
        "Appareil" to R.id.ngs_camera_icon_container,
        "Stories" to R.id.ngs_community_icon_container,
        "Spotlight" to R.id.ngs_spotlight_icon_container,
    )
    private val buttons = HashMap<String, FrameLayout>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(Color.WHITE)
        header = TextView(this)
        header.textSize = 24f
        header.setPadding(30, 40, 30, 20)
        col.addView(header)
        body = TextView(this)
        body.textSize = 18f
        body.setPadding(30, 30, 30, 30)
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this)
        bar.setBackgroundColor(Color.LTGRAY)
        for ((name, id) in tabs) {
            val btn = FrameLayout(this)
            btn.id = id
            btn.contentDescription = name
            btn.isClickable = true
            btn.isLongClickable = true
            btn.setOnLongClickListener { true }
            btn.setOnClickListener { show(name) }
            val t = TextView(this)
            t.text = name.take(4)
            t.gravity = Gravity.CENTER
            t.importantForAccessibility = TextView.IMPORTANT_FOR_ACCESSIBILITY_NO
            btn.addView(t, FrameLayout.LayoutParams(-1, 120))
            bar.addView(btn, LinearLayout.LayoutParams(0, -2, 1f))
            buttons[name] = btn
        }
        col.addView(bar)
        setContentView(col)
        show(intent?.getStringExtra("tab") ?: "Chat")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("tab")?.let { show(it) }
    }

    private fun show(name: String) {
        header.text = name
        body.text = if (name == "Chat") "Mayaaa — Remis 2 h\nOlivier — Nouveau Snap" else "Contenu de l'onglet $name"
        buttons.forEach { (n, v) -> v.isSelected = n == name }
        Log.i("FakeSnap", "TAB=$name")
    }
}
