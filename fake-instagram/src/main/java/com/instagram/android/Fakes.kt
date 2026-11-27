package com.instagram.android

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

private fun Activity.screen(label: String, color: Int, viewId: Int = View.NO_ID) {
    val root = FrameLayout(this)
    root.id = viewId
    root.setBackgroundColor(color)
    root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    root.contentDescription = label
    val t = TextView(this)
    t.text = label
    t.textSize = 28f
    t.gravity = Gravity.CENTER
    root.addView(t, FrameLayout.LayoutParams(-1, -1))
    setContentView(root)
}

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen("Faux Instagram : accueil", Color.WHITE)
    }
}

class ReelsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen("Faux Instagram : REELS", Color.rgb(255, 200, 200), R.id.clips_viewer_view_pager)
        // Barre du bas avec l'onglet d'accueil, comme dans Instagram
        val home = TextView(this)
        home.id = R.id.feed_tab
        home.text = "Accueil"
        home.contentDescription = "Accueil"
        home.textSize = 20f
        home.setBackgroundColor(Color.LTGRAY)
        home.gravity = Gravity.CENTER
        home.setOnClickListener {
            android.util.Log.i("FakeIG", "TAB=Accueil")
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
        }
        addContentView(home, FrameLayout.LayoutParams(-1, 150, Gravity.BOTTOM))
    }
}

class StoryActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        screen("Faux Instagram : STORY", Color.rgb(200, 200, 255), R.id.reel_viewer_root)
    }
}
