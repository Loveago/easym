package com.easymomo.app.ussd

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.easymomo.app.ussd.capture.CapturedNodeSnapshot
import com.easymomo.app.ussd.capture.UssdCaptureBridge

/**
 * Narrowly scoped opt-in AccessibilityService for interacting with Android system USSD dialogs.
 *
 * Security & Policy Guardrails:
 * - Only inspects known telephony/dialer USSD host packages.
 * - Never reads EditText contents (to avoid ever observing a user's PIN).
 * - Never enters or submits PINs/OTPs.
 * - Ignores transient "USSD code running..." loading dialogs.
 * - Debounces duplicate window content events.
 */
class EasyMoMoAccessibilityService : AccessibilityService() {

    private var lastObservedText: String = ""
    private var lastObservedAtMs: Long = 0L
    @Volatile
    private var lastSubmittedAtMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        UssdDiagnosticLog.log("ACCESSIBILITY", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        val captureActive = UssdCaptureBridge.isCaptureActive()
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            !(captureActive && eventType == AccessibilityEvent.TYPE_VIEW_CLICKED)
        ) {
            return
        }

        // Fast-path filter before Binder IPC (rootInActiveWindow) so non-USSD apps incur zero overhead.
        val eventPkg = event.packageName?.toString().orEmpty()
        if (eventPkg.isNotEmpty() && eventPkg !in ALLOWED_USSD_PACKAGES) return

        val now = System.currentTimeMillis()

        val root = rootInActiveWindow ?: return
        val pkg = eventPkg.ifEmpty { root.packageName?.toString().orEmpty() }
        if (pkg !in ALLOWED_USSD_PACKAGES) return

        // Single-pass tree traversal for prompt text, editable node, and dialog buttons
        val inspection = inspectDialogTree(root, collectButtonLabels = captureActive)
        val hasButtonOrInput = inspection.editableNode != null || inspection.hasDialogButton
        if (!hasButtonOrInput && pkg != "com.android.phone" && pkg != "com.android.server.telecom") {
            return
        }

        val dialogText = inspection.promptText

        // Capture mode is isolated from built-in USSD automation and never reads EditText contents.
        if (captureActive) {
            if (dialogText.isNotBlank() && isTransientLoadingDialog(dialogText)) {
                return
            }
            val editableNode = inspection.editableNode
            val clickedLabel = extractClickedButtonLabel(event)
            val visibleButtons = inspection.buttonLabels
            val isPassword = isPasswordInputNode(editableNode)
            val rootCls = root.className?.toString()
            val inputCls = editableNode?.className?.toString()
            val summary = buildString {
                append("pkg=").append(pkg)
                append(", buttons=").append(visibleButtons.size)
                append(", hasInput=").append(editableNode != null)
                if (isPassword) append(", passwordField=true")
            }
            UssdCaptureBridge.onSnapshot(
                CapturedNodeSnapshot(
                    packageName = pkg,
                    promptText = dialogText,
                    visibleButtons = visibleButtons,
                    hasEditableInput = editableNode != null,
                    isPasswordInput = isPassword,
                    inputNodeClass = inputCls,
                    rootNodeClass = rootCls,
                    nodeMetadataSummary = summary,
                    clickedButtonLabel = clickedLabel
                )
            )
            return
        }

        if (dialogText.isBlank()) return

        // Ignore transient Android spinner dialogs while waiting for the carrier menu
        if (isTransientLoadingDialog(dialogText)) {
            UssdDiagnosticLog.log("ACCESSIBILITY", "Ignored transient loading dialog")
            return
        }

        // Suppress identical re-emitted content for 2.5s after clicking Send, but never drop a NEW prompt
        if (dialogText == lastObservedText &&
            ((now - lastObservedAtMs) < 1200L || (now - lastSubmittedAtMs) < 2500L)
        ) {
            return
        }
        lastObservedText = dialogText
        lastObservedAtMs = now

        UssdDiagnosticLog.log("ACCESSIBILITY", "Observed prompt from $pkg (${dialogText.length} chars)")
        UssdPromptObserver.onPromptObserved(
            dialogText,
            UssdPromptObserver.PromptSource.ACCESSIBILITY_SERVICE
        )
    }

    override fun onInterrupt() {
        UssdDiagnosticLog.log("ACCESSIBILITY", "Service interrupted")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        UssdDiagnosticLog.log("ACCESSIBILITY", "Service disconnected")
        super.onDestroy()
    }

    fun sendUssdReply(reply: String): Boolean {
        for (attempt in 1..5) {
            val root = findUssdWindowRoot()
            if (root == null) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: no active USSD window")
                if (attempt < 5) Thread.sleep(90L)
                continue
            }
            root.refresh()

            val inputNode = findEditableNode(root)
            if (inputNode == null) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: no editable input in ${root.packageName}")
                if (attempt < 5) Thread.sleep(90L)
                continue
            }

            inputNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
            }
            val setOk = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!setOk) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: ACTION_SET_TEXT failed")
                if (attempt < 5) Thread.sleep(90L)
                continue
            }

            // Re-query and refresh root after setting text in case the Send button enables asynchronously
            val refreshedRoot = findUssdWindowRoot()?.also { it.refresh() } ?: root
            val sendButton = findSendButton(refreshedRoot) ?: findFallbackPositiveButton(refreshedRoot)
            if (sendButton == null) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: no Send/OK button found")
                if (attempt < 5) Thread.sleep(90L)
                continue
            }

            val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            UssdDiagnosticLog.log("ACCESSIBILITY", "Submitted non-sensitive USSD step (attempt $attempt): clicked=$clicked")
            if (clicked) {
                lastSubmittedAtMs = System.currentTimeMillis()
                return true
            }
            if (attempt < 5) Thread.sleep(90L)
        }
        return false
    }

    private fun findUssdWindowRoot(): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        val activePkg = active?.packageName?.toString().orEmpty()
        if (active != null && activePkg in ALLOWED_USSD_PACKAGES) {
            return active
        }
        val windowList = windows ?: return active
        for (window in windowList) {
            val root = window.root ?: continue
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg in ALLOWED_USSD_PACKAGES &&
                (findEditableNode(root) != null || findAnyDialogButton(root) != null)
            ) {
                return root
            }
        }
        return active
    }

    private class DialogInspectionResult(
        val promptText: String,
        val editableNode: AccessibilityNodeInfo?,
        val hasDialogButton: Boolean,
        val buttonLabels: List<String>
    )

    private fun inspectDialogTree(
        root: AccessibilityNodeInfo,
        collectButtonLabels: Boolean
    ): DialogInspectionResult {
        val sb = StringBuilder(128)
        val buttons = if (collectButtonLabels) LinkedHashSet<String>(4) else null
        var editableNode: AccessibilityNodeInfo? = null
        var hasDialogButton = false

        fun walk(node: AccessibilityNodeInfo) {
            val cls = node.className
            val isEditText = node.isEditable || (cls != null && cls.contains("EditText", ignoreCase = true))
            if (isEditText) {
                if (editableNode == null) editableNode = node
                // Never read EditText contents (may contain sensitive user input)
                return
            }

            val isButtonClass = cls != null && cls.contains("Button", ignoreCase = true)
            val isButton = isButtonClass || node.isClickable
            if (isButtonClass && node.isClickable) {
                hasDialogButton = true
            }

            val rawText = node.text
            if (rawText != null && rawText.isNotBlank()) {
                val trimmed = rawText.trim().toString()
                if (isButton) {
                    buttons?.add(trimmed)
                } else if (trimmed.lowercase() !in IGNORED_BUTTON_LABELS) {
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(trimmed)
                }
            }

            val count = node.childCount
            for (i in 0 until count) {
                node.getChild(i)?.let { walk(it) }
            }
        }

        walk(root)
        return DialogInspectionResult(
            promptText = sb.toString().trim(),
            editableNode = editableNode,
            hasDialogButton = hasDialogButton,
            buttonLabels = buttons?.toList() ?: emptyList()
        )
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cls = node.className
        if (node.isEditable || (cls != null && cls.contains("EditText", ignoreCase = true))) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findEditableNode(it) }
            if (found != null) return found
        }
        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && node.isEnabled) {
            val text = extractNodeAndChildText(node).lowercase()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            val isPositiveViewId = viewId.endsWith("button1") ||
                viewId.contains("send") ||
                viewId.contains("positive") ||
                viewId.contains("ok_button") ||
                viewId.contains("btn_ok") ||
                viewId.contains("submit")
            if (text in POSITIVE_BUTTON_LABELS || desc in POSITIVE_BUTTON_LABELS || isPositiveViewId) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findSendButton(it) }
            if (found != null) return found
        }
        return null
    }

    private fun findFallbackPositiveButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = ArrayList<AccessibilityNodeInfo>(4)
        collectClickableButtons(root, buttons)
        return buttons.lastOrNull { btn ->
            val label = extractNodeAndChildText(btn).lowercase()
            val desc = btn.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            label !in NEGATIVE_BUTTON_LABELS && desc !in NEGATIVE_BUTTON_LABELS
        }
    }

    private fun collectClickableButtons(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        val cls = node.className
        if (cls != null && cls.contains("Button", ignoreCase = true) && node.isClickable && node.isEnabled) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectClickableButtons(it, out) }
        }
    }

    private fun extractNodeAndChildText(node: AccessibilityNodeInfo): String {
        val own = node.text?.toString()?.trim().orEmpty()
        if (own.isNotEmpty()) return own
        for (i in 0 until node.childCount) {
            val childText = node.getChild(i)?.text?.toString()?.trim().orEmpty()
            if (childText.isNotEmpty()) return childText
        }
        return ""
    }

    private fun findAnyDialogButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val cls = node.className
        if (cls != null && cls.contains("Button", ignoreCase = true) && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findAnyDialogButton(it) }
            if (found != null) return found
        }
        return null
    }

    private fun isTransientLoadingDialog(text: String): Boolean {
        val norm = text.lowercase()
        return norm.contains("ussd code running") ||
            norm == "please wait..." ||
            norm == "please wait" ||
            norm == "running..."
    }

    private fun extractClickedButtonLabel(event: AccessibilityEvent): String? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) return null
        val cls = event.className
        if (cls != null && cls.contains("EditText", ignoreCase = true)) return null
        val source = event.source
        val sourceCls = source?.className
        if (sourceCls != null && sourceCls.contains("EditText", ignoreCase = true)) return null
        val fromSource = source?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        if (fromSource != null) return fromSource
        return event.text.firstOrNull()?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isPasswordInputNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.isPassword) return true
        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    companion object {
        @Volatile
        var instance: EasyMoMoAccessibilityService? = null
            private set

        private val ALLOWED_USSD_PACKAGES = setOf(
            "com.android.phone",
            "com.android.server.telecom",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.incallui",
            "com.android.incallui",
            "com.android.stk",
            "com.transsion.dialer",
            "com.mediatek.stk"
        )

        private val POSITIVE_BUTTON_LABELS = setOf(
            "send", "ok", "reply", "submit", "confirm", "yes", "next", "continue", "proceed"
        )
        private val NEGATIVE_BUTTON_LABELS = setOf("cancel", "dismiss", "close", "no", "back")
        private val IGNORED_BUTTON_LABELS = setOf("send", "ok", "cancel", "dismiss", "reply", "submit")
    }
}
