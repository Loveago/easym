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

    @Volatile
    private var lastObservedWindowId: Int = -1
    @Volatile
    private var lastObservedText: String = ""
    @Volatile
    private var lastObservedAtMs: Long = 0L
    @Volatile
    private var lastSubmittedAtMs: Long = 0L
    @Volatile
    private var lastSubmittedWindowId: Int = -1
    @Volatile
    private var lastSubmittedPromptText: String = ""
    @Volatile
    private var sawWindowTransitionSinceSubmit: Boolean = true
    @Volatile
    private var ussdWindowObservedInSession: Boolean = false
    @Volatile
    private var lastContentInspectAtMs: Long = 0L

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
        if (eventPkg == packageName &&
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            ussdWindowObservedInSession &&
            !captureActive
        ) {
            if (findUssdWindowRoot(AccessibilityEvent.INVALID_POSITION) == null) {
                ussdWindowObservedInSession = false
                UssdDiagnosticLog.log("ACCESSIBILITY", "USSD dialog closed; focus returned to EasyMoMo")
                UssdPromptObserver.onUssdWindowClosed()
            }
            return
        }
        if (eventPkg.isNotEmpty() && eventPkg !in ALLOWED_USSD_PACKAGES) return

        val now = System.currentTimeMillis()
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            !captureActive &&
            (now - lastContentInspectAtMs) < 120L
        ) {
            return
        }
        lastContentInspectAtMs = now

        val root = findUssdWindowRoot(event.windowId) ?: return
        val pkg = root.packageName?.toString().orEmpty()
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
            sawWindowTransitionSinceSubmit = true
            lastObservedWindowId = -1
            ussdWindowObservedInSession = true
            UssdPromptObserver.onUssdWindowOpened()
            UssdDiagnosticLog.log("ACCESSIBILITY", "Ignored transient loading dialog")
            return
        }

        // Ignore Dialer / InCallUI call-chrome screens flashing right after ACTION_CALL before the USSD AlertDialog opens
        if (inspection.editableNode == null && isDialerCallScreenChrome(dialogText)) {
            UssdDiagnosticLog.log("ACCESSIBILITY", "Ignored dialer/in-call chrome window")
            return
        }

        ussdWindowObservedInSession = true
        UssdPromptObserver.onUssdWindowOpened()

        if (root.windowId != -1 && root.windowId != lastSubmittedWindowId) {
            sawWindowTransitionSinceSubmit = true
        }

        // Ignore stale re-emission of the exact already-submitted USSD dialog window before carrier transition
        if (!sawWindowTransitionSinceSubmit &&
            dialogText == lastSubmittedPromptText &&
            (now - lastSubmittedAtMs) < 8_000L
        ) {
            UssdDiagnosticLog.log("ACCESSIBILITY", "Ignored already-submitted USSD dialog before carrier transition")
            return
        }

        // Light same-window duplicate guard
        if (root.windowId == lastObservedWindowId &&
            dialogText == lastObservedText &&
            (now - lastObservedAtMs) < 500L
        ) {
            return
        }
        lastObservedWindowId = root.windowId
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

    fun resetSessionObservation() {
        lastObservedWindowId = -1
        lastObservedText = ""
        lastObservedAtMs = 0L
        lastSubmittedAtMs = 0L
        lastSubmittedWindowId = -1
        lastSubmittedPromptText = ""
        sawWindowTransitionSinceSubmit = true
        ussdWindowObservedInSession = false
    }

    fun sendUssdReply(reply: String): Boolean {
        for (attempt in 1..12) {
            val root = findUssdWindowRoot()
            if (root == null) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: no active USSD window")
                if (attempt < 12) Thread.sleep(100L)
                continue
            }
            root.refresh()

            val inputNode = findEditableNode(root)
            if (inputNode == null) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: no editable input in ${root.packageName}")
                if (attempt < 12) Thread.sleep(100L)
                continue
            }

            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
            }
            val setOk = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!setOk) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: ACTION_SET_TEXT failed")
                if (attempt < 12) Thread.sleep(100L)
                continue
            }

            // Re-check Send button briefly without re-running ACTION_SET_TEXT in case TextWatcher enables it asynchronously
            var refreshedRoot: AccessibilityNodeInfo = root
            var sendButton: AccessibilityNodeInfo? = null
            for (btnCheck in 1..4) {
                refreshedRoot = findUssdWindowRoot()?.also { it.refresh() } ?: root
                sendButton = findSendButton(refreshedRoot) ?: findFallbackPositiveButton(refreshedRoot)
                if (sendButton != null) break
                if (btnCheck < 4) Thread.sleep(50L)
            }
            if (sendButton == null) {
                UssdDiagnosticLog.log("ACCESSIBILITY", "sendUssdReply attempt $attempt: no Send/OK button found")
                if (attempt < 12) Thread.sleep(100L)
                continue
            }

            val prevSubmittedPromptText = lastSubmittedPromptText
            val prevSubmittedAtMs = lastSubmittedAtMs
            lastSubmittedWindowId = refreshedRoot.windowId
            lastSubmittedPromptText = lastObservedText
            sawWindowTransitionSinceSubmit = false
            lastSubmittedAtMs = System.currentTimeMillis()

            val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            UssdDiagnosticLog.log("ACCESSIBILITY", "Submitted non-sensitive USSD step (attempt $attempt): clicked=$clicked")
            if (clicked) {
                return true
            }
            lastSubmittedWindowId = -1
            lastSubmittedPromptText = prevSubmittedPromptText
            sawWindowTransitionSinceSubmit = true
            lastSubmittedAtMs = prevSubmittedAtMs
            if (attempt < 12) Thread.sleep(100L)
        }
        return false
    }

    private fun findUssdWindowRoot(preferredWindowId: Int = -1): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        val activePkg = active?.packageName?.toString().orEmpty()
        val activeAllowed = if (active != null && activePkg in ALLOWED_USSD_PACKAGES) active else null

        if (preferredWindowId != -1) {
            if (activeAllowed != null && activeAllowed.windowId == preferredWindowId) {
                return activeAllowed
            }
            val matchingRoot = windows?.firstOrNull { it.id == preferredWindowId }?.root
            val matchingPkg = matchingRoot?.packageName?.toString().orEmpty()
            if (matchingRoot != null && matchingPkg in ALLOWED_USSD_PACKAGES) {
                return matchingRoot
            }
        }

        if (activeAllowed != null && findEditableNode(activeAllowed) != null) {
            return activeAllowed
        }

        val windowList = windows
        var buttonOnlyCandidate: AccessibilityNodeInfo? = null
        if (windowList != null) {
            for (window in windowList) {
                val root = window.root ?: continue
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg !in ALLOWED_USSD_PACKAGES) continue
                if (findEditableNode(root) != null) {
                    return root
                }
                if (buttonOnlyCandidate == null && findAnyDialogButton(root) != null) {
                    buttonOnlyCandidate = root
                }
            }
        }

        if (buttonOnlyCandidate != null) return buttonOnlyCandidate
        return activeAllowed
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
            val clsStr = cls?.toString().orEmpty()
            val isEditText = node.isEditable || clsStr.contains("EditText", ignoreCase = true)
            if (isEditText) {
                if (editableNode == null) editableNode = node
                // Never read EditText contents (may contain sensitive user input)
                return
            }

            val rawText = node.text
            val trimmed = rawText?.trim()?.toString().orEmpty()
            val lowerTrimmed = trimmed.lowercase()

            if (node.isClickable && trimmed.isEmpty() && node.childCount in 1..3) {
                val wrappedActionLabel = extractSingleWrappedActionLabel(node)
                if (wrappedActionLabel != null) {
                    hasDialogButton = true
                    buttons?.add(wrappedActionLabel)
                    return
                }
            }

            val isButtonClass = clsStr.contains("Button", ignoreCase = true)
            val isClickableTextButton = node.isClickable &&
                clsStr.contains("TextView", ignoreCase = true) &&
                isDialogActionTextOrViewId(node, lowerTrimmed)
            val isButton = isButtonClass || isClickableTextButton
            if ((isButtonClass && node.isClickable) || isClickableTextButton) {
                hasDialogButton = true
            }

            if (trimmed.isNotEmpty()) {
                if (isButton) {
                    buttons?.add(trimmed)
                } else if (lowerTrimmed !in IGNORED_BUTTON_LABELS) {
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

    private fun extractSingleWrappedActionLabel(node: AccessibilityNodeInfo): String? {
        val count = node.childCount
        if (count !in 1..3) return null
        var singleLabel: String? = null
        var labelNode: AccessibilityNodeInfo? = null
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            val childCls = child.className?.toString().orEmpty()
            if (child.isEditable || childCls.contains("EditText", ignoreCase = true)) return null
            if (child.childCount > 0) return null
            val childText = child.text?.trim()?.toString().orEmpty()
            if (childText.isNotEmpty()) {
                if (singleLabel != null) return null
                singleLabel = childText
                labelNode = child
            }
        }
        val found = singleLabel ?: return null
        val lower = found.lowercase()
        return if (isDialogActionTextOrViewId(node, lower) ||
            (labelNode != null && isDialogActionTextOrViewId(labelNode, lower))
        ) {
            found
        } else {
            null
        }
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
            val matchesPositiveLabel = text in POSITIVE_BUTTON_LABELS ||
                desc in POSITIVE_BUTTON_LABELS ||
                POSITIVE_BUTTON_LABELS.any { label ->
                    text.startsWith("$label ") || desc.startsWith("$label ")
                }
            if (matchesPositiveLabel || isPositiveViewId) {
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
        val clsStr = node.className?.toString().orEmpty()
        val isEditable = node.isEditable || clsStr.contains("EditText", ignoreCase = true)
        if (!isEditable && node.isClickable && node.isEnabled) {
            val isButtonClass = clsStr.contains("Button", ignoreCase = true) ||
                clsStr.contains("ImageButton", ignoreCase = true)
            val isActionTextButton = clsStr.contains("TextView", ignoreCase = true) &&
                isDialogActionTextOrViewId(node, extractNodeAndChildText(node).lowercase())
            val isWrappedActionButton = node.text.isNullOrBlank() && extractSingleWrappedActionLabel(node) != null
            if (isButtonClass || isActionTextButton || isWrappedActionButton) {
                out.add(node)
                if (isWrappedActionButton) return
            }
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
        val clsStr = node.className?.toString().orEmpty()
        val isButtonClass = clsStr.contains("Button", ignoreCase = true) ||
            clsStr.contains("ImageButton", ignoreCase = true)
        val isActionTextButton = clsStr.contains("TextView", ignoreCase = true) &&
            isDialogActionTextOrViewId(node, extractNodeAndChildText(node).lowercase())
        val isWrappedActionButton = node.isClickable &&
            node.text.isNullOrBlank() &&
            extractSingleWrappedActionLabel(node) != null
        if (node.isClickable && (isButtonClass || isActionTextButton || isWrappedActionButton)) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findAnyDialogButton(it) }
            if (found != null) return found
        }
        return null
    }

    private fun isDialogActionTextOrViewId(node: AccessibilityNodeInfo, lowerText: String): Boolean {
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val isButtonViewId = viewId.endsWith("button1") ||
            viewId.endsWith("button2") ||
            viewId.endsWith("button3") ||
            viewId.contains("btn_") ||
            viewId.contains("_btn") ||
            viewId.contains("send") ||
            viewId.contains("cancel")
        return isButtonViewId ||
            lowerText in POSITIVE_BUTTON_LABELS ||
            lowerText in NEGATIVE_BUTTON_LABELS ||
            lowerText in IGNORED_BUTTON_LABELS
    }

    private fun isTransientLoadingDialog(text: String): Boolean {
        val norm = text.lowercase()
        return norm.contains("ussd code running") ||
            norm == "please wait..." ||
            norm == "please wait" ||
            norm == "running..." ||
            norm == "loading..." ||
            norm == "loading" ||
            norm == "dialing..." ||
            norm == "dialing" ||
            norm == "calling..." ||
            norm == "calling" ||
            norm == "sending..." ||
            norm == "connecting..." ||
            norm.startsWith("dialing *") ||
            norm.startsWith("calling *")
    }

    private fun isDialerCallScreenChrome(text: String): Boolean {
        val norm = text.lowercase()
        return (norm.contains("keypad") &&
            (norm.contains("mute") ||
                norm.contains("speaker") ||
                norm.contains("recents") ||
                norm.contains("contacts") ||
                norm.contains("add call"))) ||
            (norm.contains("recents") && norm.contains("contacts") &&
                (norm.contains("favorites") || norm.contains("favourites")))
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
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.android.stk",
            "com.transsion.dialer",
            "com.mediatek.stk",
            "com.miui.phone",
            "com.oplus.dialer",
            "com.coloros.phone",
            "com.vivo.dialer",
            "com.huawei.contacts",
            "com.oneplus.dialer"
        )

        private val POSITIVE_BUTTON_LABELS = setOf(
            "send", "ok", "reply", "submit", "confirm", "yes", "next", "continue", "proceed"
        )
        private val NEGATIVE_BUTTON_LABELS = setOf("cancel", "dismiss", "close", "no", "back")
        private val IGNORED_BUTTON_LABELS = setOf("send", "ok", "cancel", "dismiss", "reply", "submit")
    }
}
