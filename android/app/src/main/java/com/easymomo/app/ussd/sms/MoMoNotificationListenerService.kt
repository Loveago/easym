package com.easymomo.app.ussd.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.easymomo.app.EasyMoMoApplication
import com.easymomo.app.ussd.UssdDiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens to incoming Mobile Money SMS notifications via Android's NotificationListenerService
 * (without requiring READ_SMS / RECEIVE_SMS permissions) and auto-confirms or records live transactions.
 */
class MoMoNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return
        }
        val pkg = sbn.packageName.orEmpty()
        if (pkg == packageName) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .orEmpty()

        val candidates = buildList {
            val primary = bigText.ifBlank { text }
            if (primary.isNotBlank()) add(primary)
            lines.forEach { line ->
                if (line != primary) add(line)
            }
        }
        if (candidates.isEmpty()) return

        val app = applicationContext as? EasyMoMoApplication ?: return
        serviceScope.launch {
            candidates.forEach { body ->
                val parsed = MoMoSmsParser.parse(title, body) ?: return@forEach
                runCatching {
                    val saved = app.transactionRepository.recordIncomingSmsNotification(parsed)
                    if (saved != null) {
                        UssdDiagnosticLog.log(
                            "SMS",
                            "Captured ${parsed.network.shortName} ${parsed.transactionType.code} GHS ${parsed.amount} (ref=${parsed.providerReference.ifBlank { "none" }})"
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
