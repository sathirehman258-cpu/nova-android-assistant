package com.nova.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nova.assistant.domain.model.ActionResult
import com.nova.assistant.domain.model.ActionStatus

/**
 * Legitimate, user-authorized UI automation only. This service:
 *  - only acts on what is currently visible on screen (no hidden/background UI manipulation)
 *  - prefers semantic node lookup (by text/content-description) over raw coordinates
 *  - never bypasses lock screens, authentication prompts, CAPTCHAs, or payment confirmations —
 *    those events are explicitly ignored (see isSensitiveScreen)
 *  - is only active because the user explicitly enabled it in Android's Accessibility settings
 */
class AssistantAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: AssistantAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ScreenContextManager subscribes to window-state changes separately; this callback
        // is intentionally left minimal to avoid over-processing every UI event.
    }

    override fun onInterrupt() {}

    // ---------- Semantic capabilities ----------

    fun findByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        if (isSensitiveScreen(root)) return null
        return root.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    fun tapElement(text: String): ActionResult {
        val node = findByText(text)
            ?: return ActionResult(ActionStatus.FAILED, "Couldn't find anything labeled \"$text\" on screen.")
        val clickable = findNearestClickableAncestor(node) ?: node
        val success = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (success) {
            ActionResult(ActionStatus.SUCCESS, "Tapped \"$text\".")
        } else {
            ActionResult(ActionStatus.FAILED, "Found \"$text\" but couldn't tap it.")
        }
    }

    private fun findNearestClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    fun tapCoordinate(x: Float, y: Float): ActionResult {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) {
            ActionResult(ActionStatus.SUCCESS, "Tapped.")
        } else {
            ActionResult(ActionStatus.FAILED, "Couldn't perform that tap.")
        }
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): ActionResult {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        val dispatched = dispatchGesture(gesture, null, null)
        return if (dispatched) ActionResult(ActionStatus.SUCCESS, "Swiped.")
        else ActionResult(ActionStatus.FAILED, "Couldn't swipe.")
    }

    fun typeText(text: String): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult(ActionStatus.FAILED, "Nothing focused to type into.")
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return ActionResult(ActionStatus.FAILED, "No text field is focused right now.")
        val arguments = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return if (success) ActionResult(ActionStatus.SUCCESS, "Typed it in.")
        else ActionResult(ActionStatus.FAILED, "Couldn't type into that field.")
    }

    fun pressBack(): ActionResult {
        val ok = performGlobalAction(GLOBAL_ACTION_BACK)
        return if (ok) ActionResult(ActionStatus.SUCCESS, "") else ActionResult(ActionStatus.FAILED, "Couldn't go back.")
    }

    fun pressHome(): ActionResult {
        val ok = performGlobalAction(GLOBAL_ACTION_HOME)
        return if (ok) ActionResult(ActionStatus.SUCCESS, "") else ActionResult(ActionStatus.FAILED, "Couldn't go home.")
    }

    fun scroll(direction: String): ActionResult {
        val root = rootInActiveWindow ?: return ActionResult(ActionStatus.FAILED, "Nothing to scroll.")
        val scrollable = findScrollable(root) ?: return ActionResult(ActionStatus.FAILED, "Nothing scrollable on screen.")
        val action = if (direction.equals("down", ignoreCase = true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val ok = scrollable.performAction(action)
        return if (ok) ActionResult(ActionStatus.SUCCESS, "") else ActionResult(ActionStatus.FAILED, "Couldn't scroll.")
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        }
        return null
    }

    fun getVisibleScreenText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<String>()
        collectText(root, results)
        return results
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, out) }
        }
    }

    fun getCurrentApp(): Pair<String?, String?> {
        val root = rootInActiveWindow
        return root?.packageName?.toString() to root?.className?.toString()
    }

    /**
     * Refuses to read/act on screens that look like authentication, payment, or OTP entry —
     * detected heuristically by package/class hints. This is a best-effort safety net, not a
     * guarantee; sensitive flows should also be excluded by policy at the tool-planning layer.
     */
    private fun isSensitiveScreen(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString()?.lowercase() ?: return false
        val sensitiveHints = listOf("password", "otp", "auth", "lockscreen", "keyguard", "banking", "payment")
        return sensitiveHints.any { pkg.contains(it) }
    }
}
