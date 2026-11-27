package com.zenecran.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.zenecran.data.FeatureDef

/** Ce que le service sait de l'écran au moment du scan. */
class ScreenCtx(
    val pkg: String,
    val roots: List<AccessibilityNodeInfo>,
    val activity: String?,
    val titles: List<String>,
    val screenW: Int,
    val screenH: Int,
)

/** Repère les fonctionnalités (Reels, Stories, Shorts…) dans les fenêtres de l'appli au premier plan. */
object Detector {
    private const val MAX_NODES = 4000

    fun detect(ctx: ScreenCtx, defs: List<FeatureDef>): List<FeatureDef> {
        val found = LinkedHashSet<FeatureDef>()

        // 1) Nom de l'écran (activité) ou titre de la fenêtre
        for (d in defs) {
            if (d.activities.any { a ->
                    ctx.activity?.contains(a, ignoreCase = true) == true ||
                        ctx.titles.any { it.contains(a, ignoreCase = true) }
                }
            ) found += d
        }

        // 2) Identifiants : recherche directe par Android (rapide et fiable)
        for (d in defs) {
            if (d in found || d.ids.isEmpty()) continue
            if (ctx.roots.any { r -> d.ids.any { id -> visibleById(r, fullId(ctx.pkg, id)) } }) found += d
        }

        // 2 bis) Onglet actif repéré par son identifiant
        for (d in defs) {
            if (d in found || d.selectedIds.isEmpty()) continue
            if (ctx.roots.any { r -> d.selectedIds.any { id -> selectedById(r, fullId(ctx.pkg, id)) } }) found += d
        }

        // 3) Descriptions, titres, onglets sélectionnés, structure vidéo : un seul parcours de l'arbre
        val rest = defs.filter {
            it !in found && (it.descs.isNotEmpty() || it.selectedPrefixes.isNotEmpty() ||
                it.selectedLabels.isNotEmpty() || it.fullscreenVideo || it.titles.isNotEmpty())
        }
        val rect = Rect()
        if (rest.isEmpty()) return found.toList()

        val wantVideo = rest.filter { it.fullscreenVideo }
        var count = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        ctx.roots.forEach { queue.addLast(it) }
        while (queue.isNotEmpty() && count < MAX_NODES) {
            val n = queue.removeFirst()
            count++
            if (n.isVisibleToUser) {
                val desc = n.contentDescription?.toString()
                val txt = n.text?.toString()
                for (d in rest) {
                    if (d in found) continue
                    if (desc != null && d.descs.any { desc.equals(it, ignoreCase = true) }) {
                        found += d
                    } else if (txt != null && d.titles.any { txt.trim().equals(it, ignoreCase = true) } && isTop(n, ctx, rect)) {
                        found += d
                    } else if (n.isSelected && (
                            d.selectedPrefixes.any { p -> desc?.startsWith(p, true) == true || txt?.startsWith(p, true) == true } ||
                                d.selectedLabels.any { l -> desc?.contains(l, true) == true || txt?.contains(l, true) == true }
                            )
                    ) {
                        found += d
                    }
                }
                if (wantVideo.any { it !in found } && isVideoPager(n, ctx) && hasVideoItem(n, ctx)) {
                    wantVideo.forEach { found += it }
                }
            }
            if (rest.all { it in found }) break
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
        }
        return found.toList()
    }

    fun fullId(pkg: String, id: String): String = if (id.contains(":id/")) id else "$pkg:id/$id"

    private fun visibleById(root: AccessibilityNodeInfo, full: String): Boolean = try {
        root.findAccessibilityNodeInfosByViewId(full).any { it.isVisibleToUser }
    } catch (e: Exception) {
        false
    }

    private fun selectedById(root: AccessibilityNodeInfo, full: String): Boolean = try {
        root.findAccessibilityNodeInfosByViewId(full).any { it.isVisibleToUser && it.isSelected }
    } catch (e: Exception) {
        false
    }

    /** L'élément est dans le haut de l'écran (zone du titre de l'onglet). */
    private fun isTop(n: AccessibilityNodeInfo, ctx: ScreenCtx, r: Rect): Boolean {
        n.getBoundsInScreen(r)
        return r.top < ctx.screenH * 0.22f
    }

    /**
     * Appuie sur l'onglet d'accueil de l'appli : d'abord par identifiant, puis par libellé
     * (description « Accueil », « Accueil, onglet 1 sur 6 »…, puis texte). Renvoie false s'il n'est pas visible.
     */
    fun clickHome(ctx: ScreenCtx, d: FeatureDef): Boolean {
        for (id in d.homeIds) if (clickById(ctx, id)) return true
        if (d.homeDescs.isEmpty()) return false
        fun matches(s: String?): Boolean {
            val v = s?.trim() ?: return false
            return d.homeDescs.any { h ->
                v.equals(h, true) || v.startsWith("$h,", true) || v.startsWith("$h ", true)
            }
        }
        for (useText in listOf(false, true)) {
            var count = 0
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            ctx.roots.forEach { queue.addLast(it) }
            while (queue.isNotEmpty() && count < 3000) {
                val n = queue.removeFirst()
                count++
                val label = if (useText) n.text?.toString() else n.contentDescription?.toString()
                if (n.isVisibleToUser && matches(label) && clickNodeOrParent(n, 2)) return true
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.addLast(it) }
            }
        }
        return false
    }

    private fun clickNodeOrParent(n: AccessibilityNodeInfo, maxHops: Int): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var hops = 0
        while (cur != null && hops <= maxHops) {
            if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            cur = cur.parent
            hops++
        }
        return false
    }

    /** Appuie sur le bouton qui porte cet identifiant (ou sur son parent appuyable). */
    fun clickById(ctx: ScreenCtx, id: String): Boolean {
        for (r in ctx.roots) {
            val nodes = try {
                r.findAccessibilityNodeInfosByViewId(fullId(ctx.pkg, id))
            } catch (e: Exception) {
                emptyList()
            }
            for (n in nodes) {
                if (!n.isVisibleToUser) continue
                var cur: AccessibilityNodeInfo? = n
                var hops = 0
                while (cur != null && hops < 4) {
                    if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    cur = cur.parent
                    hops++
                }
            }
        }
        return false
    }

    private fun isBig(n: AccessibilityNodeInfo, ctx: ScreenCtx): Boolean {
        val r = Rect()
        n.getBoundsInScreen(r)
        return r.width() >= ctx.screenW * 0.9f && r.height() >= ctx.screenH * 0.70f
    }

    /** Liste qui défile en plein écran (le conteneur des Reels). */
    private fun isVideoPager(n: AccessibilityNodeInfo, ctx: ScreenCtx): Boolean {
        val cls = n.className?.toString() ?: return false
        return n.isScrollable && (cls.contains("RecyclerView") || cls.contains("ViewPager")) && isBig(n, ctx)
    }

    /** Dans ce conteneur : un grand élément appuyable longuement qui contient une grande surface vidéo. */
    private fun hasVideoItem(pager: AccessibilityNodeInfo, ctx: ScreenCtx): Boolean {
        var budget = 400
        fun search(n: AccessibilityNodeInfo, depth: Int, insideItem: Boolean): Boolean {
            if (budget-- <= 0 || depth > 8) return false
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                if (!c.isVisibleToUser) continue
                val big = isBig(c, ctx)
                val cls = c.className?.toString().orEmpty()
                if (insideItem && big && (cls.endsWith("SurfaceView") || cls.endsWith("TextureView") ||
                        cls.endsWith("VideoView") || cls.contains("PlayerView"))
                ) return true
                val item = insideItem || (big && c.isLongClickable)
                if (search(c, depth + 1, item)) return true
            }
            return false
        }
        return search(pager, 0, false)
    }

    /** Résumé lisible de ce que le service « voit » (pour le mode diagnostic). */
    fun dump(ctx: ScreenCtx, maxLines: Int = 600): String {
        val sb = StringBuilder()
        sb.append("appli=").append(ctx.pkg).append('\n')
        sb.append("activité=").append(ctx.activity ?: "?").append('\n')
        if (ctx.titles.isNotEmpty()) sb.append("fenêtres=").append(ctx.titles.joinToString(" | ")).append('\n')
        var lines = 0
        val r = Rect()
        for (root in ctx.roots) {
            val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            stack.addLast(root to 0)
            while (stack.isNotEmpty() && lines < maxLines) {
                val (n, depth) = stack.removeLast()
                val id = n.viewIdResourceName?.substringAfter(":id/")
                val desc = n.contentDescription?.toString()
                val txt = n.text?.toString()
                n.getBoundsInScreen(r)
                val wPct = if (ctx.screenW > 0) r.width() * 100 / ctx.screenW else 0
                val hPct = if (ctx.screenH > 0) r.height() * 100 / ctx.screenH else 0
                val bigNode = wPct >= 80 && hPct >= 60
                if (id != null || !desc.isNullOrEmpty() || !txt.isNullOrEmpty() || n.isSelected || bigNode) {
                    lines++
                    sb.append("  ".repeat(minOf(depth, 14)))
                    sb.append(n.className?.toString()?.substringAfterLast('.') ?: "?").append(' ')
                    if (id != null) sb.append("id=").append(id).append(' ')
                    if (!desc.isNullOrEmpty()) sb.append("desc=\"").append(desc.take(70).replace('\n', ' ')).append("\" ")
                    if (!txt.isNullOrEmpty()) sb.append("text=\"").append(txt.take(50).replace('\n', ' ')).append("\" ")
                    if (n.isSelected) sb.append("[SÉLECTIONNÉ] ")
                    if (n.isScrollable) sb.append("[défile] ")
                    if (n.isLongClickable) sb.append("[appui-long] ")
                    if (bigNode) sb.append("[${wPct}x${hPct}%] ")
                    if (!n.isVisibleToUser) sb.append("[caché]")
                    sb.append('\n')
                }
                for (i in n.childCount - 1 downTo 0) {
                    n.getChild(i)?.let { stack.addLast(it to depth + 1) }
                }
            }
        }
        if (lines >= maxLines) sb.append("… (tronqué)\n")
        return sb.toString()
    }
}
