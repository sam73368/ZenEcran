package com.zenecran.data

data class AppDef(val pkg: String, val label: String)

/**
 * Une fonctionnalité repérable à l'intérieur d'une appli.
 * Détection :
 *  - ids : un élément visible dont l'identifiant (après "id/") est exactement l'un de ces noms
 *  - selectedLabels : un élément SÉLECTIONNÉ (onglet actif) dont le texte ou la description contient l'un de ces mots (minuscules)
 * Ces repères dépendent de la version des applis : ils peuvent changer après une mise à jour.
 * Le « Mode diagnostic » dans les réglages permet de les ajuster, et les règles personnalisées d'en créer.
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
    /** Applis proposées en suggestion (affichées seulement si elles sont installées). */
    val apps = listOf(
        AppDef("com.facebook.katana", "Facebook"),
        AppDef("com.snapchat.android", "Snapchat"),
        AppDef("com.instagram.android", "Instagram"),
        AppDef("com.zhiliaoapp.musically", "TikTok"),
        AppDef("com.google.android.youtube", "YouTube"),
        AppDef("com.facebook.orca", "Messenger"),
        AppDef("com.whatsapp", "WhatsApp"),
        AppDef("com.twitter.android", "X"),
        AppDef("com.reddit.frontpage", "Reddit"),
        AppDef("com.instagram.barcelona", "Threads"),
        AppDef("com.pinterest", "Pinterest"),
        AppDef("com.discord", "Discord"),
        AppDef("tv.twitch.android.app", "Twitch"),
        AppDef("com.netflix.mediaclient", "Netflix"),
        AppDef("org.telegram.messenger", "Telegram"),
        AppDef("com.linkedin.android", "LinkedIn"),
        AppDef("com.facebook.lite", "Facebook Lite"),
        AppDef("com.zhiliaoapp.musically.go", "TikTok Lite"),
        AppDef("com.google.android.apps.youtube.music", "YouTube Music"),
    )

    val features = listOf(
        // Facebook
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
            id = "fbl_reels", pkg = "com.facebook.lite",
            title = "Reels", subtitle = "Onglet Reels de Facebook Lite",
            selectedLabels = listOf("reels"),
            experimental = true,
        ),
        // Instagram
        FeatureDef(
            id = "ig_reels", pkg = "com.instagram.android",
            title = "Reels", subtitle = "Onglet et lecteur Reels",
            ids = listOf("clips_viewer_view_pager"),
            selectedLabels = listOf("reels"),
        ),
        FeatureDef(
            id = "ig_stories", pkg = "com.instagram.android",
            title = "Stories", subtitle = "Lecteur de stories",
            ids = listOf("reel_viewer_texture_view", "reel_viewer_media_layout", "reel_viewer_root"),
            experimental = true,
        ),
        // Snapchat
        FeatureDef(
            id = "sc_spotlight", pkg = "com.snapchat.android",
            title = "Spotlight", subtitle = "Fil de vidéos Spotlight",
            selectedLabels = listOf("spotlight"),
            experimental = true,
        ),
        FeatureDef(
            id = "sc_stories", pkg = "com.snapchat.android",
            title = "Stories", subtitle = "Onglet Stories",
            selectedLabels = listOf("stories"),
            experimental = true,
        ),
        // YouTube
        FeatureDef(
            id = "yt_shorts", pkg = "com.google.android.youtube",
            title = "Shorts", subtitle = "Onglet et lecteur Shorts",
            ids = listOf("reel_recycler", "reel_player_page_container"),
            selectedLabels = listOf("shorts"),
        ),
        // WhatsApp
        FeatureDef(
            id = "wa_status", pkg = "com.whatsapp",
            title = "Statuts", subtitle = "Onglet Actus / Statuts",
            selectedLabels = listOf("statuts", "status", "actus", "updates", "mises à jour"),
            experimental = true,
        ),
    )

    fun appLabel(pkg: String): String? = apps.firstOrNull { it.pkg == pkg }?.label
}
