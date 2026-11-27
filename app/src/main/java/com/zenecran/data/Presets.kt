package com.zenecran.data

data class AppDef(val pkg: String, val label: String)

/**
 * Une fonctionnalité repérable à l'intérieur d'une appli.
 * Détection :
 *  - ids : un élément visible dont l'identifiant (après "id/") est exactement l'un de ces noms
 *  - selectedLabels : un élément SÉLECTIONNÉ (onglet actif) dont le texte ou la description contient l'un de ces mots (minuscules)
 * Ces repères dépendent de la version des applis : ils peuvent changer après une mise à jour.
 * Le « Mode diagnostic » dans les réglages permet de les ajuster.
 */
data class FeatureDef(
    val id: String,
    val pkg: String,
    val title: String,
    val subtitle: String,
    val ids: List<String> = emptyList(),
    val selectedLabels: List<String> = emptyList(),
    val experimental: Boolean = false,
)

object Presets {
    val apps = listOf(
        AppDef("com.facebook.katana", "Facebook"),
        AppDef("com.snapchat.android", "Snapchat"),
        AppDef("com.instagram.android", "Instagram"),
        AppDef("com.zhiliaoapp.musically", "TikTok"),
        AppDef("com.google.android.youtube", "YouTube"),
    )

    val features = listOf(
        FeatureDef(
            id = "fb_reels", pkg = "com.facebook.katana",
            title = "Reels", subtitle = "Onglet et lecteur Reels",
            selectedLabels = listOf("reels"),
        ),
        FeatureDef(
            id = "fb_video", pkg = "com.facebook.katana",
            title = "Vidéos (Watch)", subtitle = "Onglet Vidéo",
            selectedLabels = listOf("vidéo", "video", "watch"),
            experimental = true,
        ),
        FeatureDef(
            id = "sc_spotlight", pkg = "com.snapchat.android",
            title = "Spotlight", subtitle = "Fil de vidéos Spotlight",
            selectedLabels = listOf("spotlight"),
            experimental = true,
        ),
        FeatureDef(
            id = "ig_reels", pkg = "com.instagram.android",
            title = "Reels", subtitle = "Onglet et lecteur Reels",
            ids = listOf("clips_viewer_view_pager"),
            selectedLabels = listOf("reels"),
        ),
        FeatureDef(
            id = "yt_shorts", pkg = "com.google.android.youtube",
            title = "Shorts", subtitle = "Onglet et lecteur Shorts",
            ids = listOf("reel_recycler", "reel_player_page_container"),
            selectedLabels = listOf("shorts"),
        ),
    )

    fun appLabel(pkg: String): String? = apps.firstOrNull { it.pkg == pkg }?.label
}
