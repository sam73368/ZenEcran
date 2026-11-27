package com.zenecran.service

import android.view.accessibility.AccessibilityNodeInfo
import com.zenecran.data.FeatureDef

/** Parcourt l'arbre d'accessibilité de la fenêtre active pour repérer des fonctionnalités (Reels, Shorts…). */
object Detector {
    private const val MAX_NODES = 1500

    fun detect(root: AccessibilityNodeInfo, defs: List<FeatureDef>): List<FeatureDef> {
        val found = LinkedHashSet<FeatureDef>()
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var count = 0
        while (stack.isNotEmpty() && count < MAX_NODES) {
            val n = stack.removeLast()
            count++
            val id = n.viewIdResourceName?.substringAfter('/')
            val desc = n.contentDescription?.toString()?.lowercase()
            val txt = n.text?.toString()?.lowercase()
            for (d in defs) {
                if (d in found) continue
                if (id != null && d.ids.contains(id) && n.isVisibleToUser) {
                    found += d
                } else if (n.isSelected && d.selectedLabels.any { l ->
                        (desc != null && desc.contains(l)) || (txt != null && txt.contains(l))
                    }
                ) {
                    found += d
                }
            }
            if (found.size == defs.size) break
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return found.toList()
    }

    /** Résumé lisible de ce que le service « voit » (pour le mode diagnostic). */
    fun dump(root: AccessibilityNodeInfo, maxLines: Int = 400): String {
        val sb = StringBuilder()
        sb.append("pkg=").append(root.packageName).append('\n')
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var lines = 0
        while (stack.isNotEmpty() && lines < maxLines) {
            val (n, depth) = stack.removeLast()
            val id = n.viewIdResourceName?.substringAfter('/')
            val desc = n.contentDescription?.toString()
            val txt = n.text?.toString()
            if (id != null || !desc.isNullOrEmpty() || !txt.isNullOrEmpty()) {
                lines++
                sb.append("  ".repeat(minOf(depth, 12)))
                if (id != null) sb.append("id=").append(id).append(' ')
                if (!desc.isNullOrEmpty()) sb.append("desc=\"").append(desc.take(60)).append("\" ")
                if (!txt.isNullOrEmpty()) sb.append("text=\"").append(txt.take(60)).append("\" ")
                if (n.isSelected) sb.append("[sélectionné] ")
                if (!n.isVisibleToUser) sb.append("[caché]")
                sb.append('\n')
            }
            for (i in n.childCount - 1 downTo 0) {
                n.getChild(i)?.let { stack.addLast(it to depth + 1) }
            }
        }
        return sb.toString()
    }
}
