package com.facebook.katana

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Barre d'onglets comme celle de Facebook : la description de l'onglet actif commence par son nom. */
private fun Activity.tabs(selected: String, body: String) {
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.setBackgroundColor(Color.WHITE)
    val bar = LinearLayout(this)
    listOf("Accueil", "Reels", "Vidéo", "Profil").forEachIndexed { i, name ->
        val t = TextView(this)
        t.text = name
        t.textSize = 18f
        t.setPadding(30, 30, 30, 30)
        t.contentDescription = "$name, onglet ${i + 1} sur 4"
        t.isSelected = name == selected
        t.isClickable = true
        bar.addView(t)
    }
    col.addView(bar)
    val content = TextView(this)
    content.text = body
    content.textSize = 26f
    col.addView(content)
    setContentView(col)
}

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tabs("Accueil", "Faux Facebook : fil d'actualité")
    }
}

class ReelsTabActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tabs("Reels", "Faux Facebook : onglet Reels")
    }
}

class StoryViewerActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val t = TextView(this)
        t.text = "Faux Facebook : STORY"
        t.textSize = 28f
        t.setBackgroundColor(Color.rgb(200, 255, 200))
        setContentView(t)
    }
}

/** Liste plein écran qui défile, annoncée comme un RecyclerView (comme le lecteur de Reels). */
private class FakeRecycler(c: Context) : FrameLayout(c) {
    override fun getAccessibilityClassName(): CharSequence = "androidx.recyclerview.widget.RecyclerView"
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = true
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }
}

/** Élément plein écran appuyable longuement (un Reel). */
private class FakeItem(c: Context) : FrameLayout(c) {
    init {
        isClickable = true
        isLongClickable = true
        setOnLongClickListener { true }
        setOnClickListener { }
    }
    override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
}

class ReelsViewerActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val list = FakeRecycler(this)
        list.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val item = FakeItem(this)
        item.contentDescription = "Reel"
        val video = SurfaceView(this)
        video.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        video.contentDescription = "Vidéo"
        item.addView(video, FrameLayout.LayoutParams(-1, -1))
        list.addView(item, FrameLayout.LayoutParams(-1, -1))
        setContentView(list)
    }
}
