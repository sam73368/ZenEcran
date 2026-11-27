package com.zenecran.data

data class AppDef(val pkg: String, val label: String)

/**
 * Une fonctionnalité repérable à l'intérieur d'une appli. Elle est détectée si AU MOINS UN repère correspond :
 *  - ids : un élément visible avec cet identifiant (ex. "clips_viewer_view_pager"), cherché directement par Android
 *  - descs : un élément visible dont la description vaut exactement ce texte
 *  - selectedPrefixes : un élément SÉLECTIONNÉ (onglet actif) dont la description/le texte commence par ce texte
 *  - selectedLabels : un élément SÉLECTIONNÉ dont la description/le texte contient ce mot
 *  - activities : l'écran (activité Android) ouvert contient ce nom (ex. "StoryViewerActivity")
 *  - fullscreenVideo : un lecteur vidéo plein écran qui défile (structure des Reels Facebook)
 * Ces repères dépendent des versions des applis : le Mode diagnostic permet de les vérifier.
 */
data class FeatureDef(
    val id: String,
    val pkg: String,
    val title: String,
    val subtitle: String,
    val ids: List<String> = emptyList(),
    val descs: List<String> = emptyList(),
    val selectedPrefixes: List<String> = emptyList(),
    val selectedLabels: List<String> = emptyList(),
    val activities: List<String> = emptyList(),
    val fullscreenVideo: Boolean = false,
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
        // ---------------- Facebook
        FeatureDef(
            id = "fb_reels", pkg = "com.facebook.katana",
            title = "Reels", subtitle = "Onglet Reels et lecteur plein écran",
            descs = listOf(
                "FbShortsComposerAttachmentComponentSpec_STICKER",
                "FbShortsComposerAttachmentComponentSpec_GIF",
            ),
            selectedPrefixes = listOf("Reels,", "Reels"),
            fullscreenVideo = true,
        ),
        FeatureDef(
            id = "fb_stories", pkg = "com.facebook.katana",
            title = "Stories", subtitle = "Lecteur de stories",
            activities = listOf("StoryViewerActivity"),
        ),
        FeatureDef(
            id = "fb_video", pkg = "com.facebook.katana",
            title = "Vidéos (Watch)", subtitle = "Onglet Vidéo",
            selectedPrefixes = listOf("Video,", "Vidéo,", "Vidéos,", "Watch,"),
            experimental = true,
        ),
        FeatureDef(
            id = "fbl_video", pkg = "com.facebook.lite",
            title = "Vidéos", subtitle = "Lecteur vidéo de Facebook Lite (Reels compris)",
            ids = listOf("video_view"),
            experimental = true,
        ),
        // ---------------- Instagram
        FeatureDef(
            id = "ig_reels", pkg = "com.instagram.android",
            title = "Reels", subtitle = "Lecteur Reels (onglet, fil, Explorer)",
            ids = listOf("clips_viewer_view_pager"),
        ),
        FeatureDef(
            id = "ig_stories", pkg = "com.instagram.android",
            title = "Stories", subtitle = "Lecteur de stories",
            ids = listOf("reel_viewer_root"),
        ),
        // ---------------- Snapchat
        FeatureDef(
            id = "sc_spotlight", pkg = "com.snapchat.android",
            title = "Spotlight", subtitle = "Fil de vidéos Spotlight",
            ids = listOf("spotlight_container"),
        ),
        FeatureDef(
            id = "sc_stories", pkg = "com.snapchat.android",
            title = "Stories", subtitle = "Lecteur de stories (amis et Discover)",
            ids = listOf("opera_viewer"),
        ),
        // ---------------- YouTube
        FeatureDef(
            id = "yt_shorts", pkg = "com.google.android.youtube",
            title = "Shorts", subtitle = "Lecteur Shorts (les vidéos normales restent permises)",
            ids = listOf("reel_player_page_container"),
        ),
        // ---------------- TikTok
        FeatureDef(
            id = "tt_videos", pkg = "com.zhiliaoapp.musically",
            title = "Fil de vidéos", subtitle = "Toutes les vidéos (Pour toi, Abonnements)",
            ids = listOf("player_view"),
        ),
        FeatureDef(
            id = "ttl_videos", pkg = "com.zhiliaoapp.musically.go",
            title = "Fil de vidéos", subtitle = "Toutes les vidéos de TikTok Lite",
            ids = listOf("simplayer_api_player_view"),
        ),
        // ---------------- WhatsApp
        FeatureDef(
            id = "wa_status", pkg = "com.whatsapp",
            title = "Statuts / Actus", subtitle = "Onglet Actus (statuts et chaînes)",
            selectedLabels = listOf("actus", "updates", "statut", "status"),
            experimental = true,
        ),
    )

    fun appLabel(pkg: String): String? = apps.firstOrNull { it.pkg == pkg }?.label
}
