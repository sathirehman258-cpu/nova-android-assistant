package com.nova.assistant.accessibility

import com.nova.assistant.data.remote.ScreenContextDto
import com.nova.assistant.data.remote.ScreenElementDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a minimal structured snapshot of the current screen for the AI backend — only text,
 * content descriptions, and clickable/editable flags. Never includes screenshots or raw pixel
 * data by default; that would require an explicit, separately-gated user action.
 */
@Singleton
class ScreenContextManager @Inject constructor() {

    fun captureCurrent(maxElements: Int = 40): ScreenContextDto? {
        val service = AssistantAccessibilityService.instance ?: return null
        val root = service.rootInActiveWindow ?: return null

        val elements = mutableListOf<ScreenElementDto>()
        collect(root, elements, maxElements)

        return ScreenContextDto(
            packageName = root.packageName?.toString() ?: "unknown",
            activityName = root.className?.toString(),
            elements = elements
        )
    }

    private fun collect(
        node: android.view.accessibility.AccessibilityNodeInfo,
        out: MutableList<ScreenElementDto>,
        max: Int
    ) {
        if (out.size >= max) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable) {
            out.add(ScreenElementDto(text, desc, node.isClickable, node.isEditable))
        }
        for (i in 0 until node.childCount) {
            if (out.size >= max) return
            node.getChild(i)?.let { collect(it, out, max) }
        }
    }
}
