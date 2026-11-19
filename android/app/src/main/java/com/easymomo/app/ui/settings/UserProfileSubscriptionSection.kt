package com.easymomo.app.ui.settings

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easymomo.app.domain.model.SubscriptionStatus
import com.easymomo.app.ui.auth.AuthViewModel
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.OnEmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.StatusSuccess
import com.easymomo.app.ui.theme.StatusSuccessBg
import com.easymomo.app.ui.theme.StatusWarning
import com.easymomo.app.ui.theme.StatusWarningBg
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalBorder
import com.easymomo.app.ui.theme.TerminalOutlinedButton
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ui.theme.TrialSubscriptionBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PROFILE_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.US)

private fun formatIsoTimestamp(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        Instant.parse(iso)
            .atZone(ZoneId.systemDefault())
            .format(PROFILE_DATE_FORMATTER)
    }.getOrDefault(iso)
}

private fun buildUserInitials(fullName: String?): String {
    val parts = fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "AG"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.US)
        else -> "${parts.first().first()}${parts.last().first()}".uppercase(Locale.US)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSubscriptionSection(
    authViewModel: AuthViewModel,
    initialExpandBilling: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val user by authViewModel.currentUser.collectAsStateWithLifecycle()
    val subscription by authViewModel.subscription.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()

    val effectiveStatus = subscription.effectiveStatus()
    val effectiveDays = subscription.effectiveRemainingDays()
    val hasActiveAccess = subscription.hasActiveAccess()

    var billingExpanded by rememberSaveable(initialExpandBilling) {
        mutableStateOf(initialExpandBilling || !hasActiveAccess)
    }
    var webViewFallbackUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialExpandBilling, hasActiveAccess) {
        if (initialExpandBilling || !hasActiveAccess) {
            billingExpanded = true
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.loadSubscriptionDetails()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (authUiState.pendingReference != null) {
                    authViewModel.verifyPendingOrRefresh(authUiState.pendingReference)
                } else {
                    authViewModel.loadSubscriptionDetails()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(authUiState.pendingCheckoutUrl) {
        val url = authUiState.pendingCheckoutUrl ?: return@LaunchedEffect
        authViewModel.consumeCheckoutUrl()
        val launched = runCatching {
            val customTabs = CustomTabsIntent.Builder().setShowTitle(true).build()
            customTabs.launchUrl(context, Uri.parse(url))
            true
        }.getOrDefault(false)
        if (!launched) {
            webViewFallbackUrl = url
        }
    }

    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Person, contentDescription = null, tint = EmeraldPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "User Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            IconButton(
                onClick = { authViewModel.loadSubscriptionDetails() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Refresh profile & subscription",
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. User Identity Block
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buildUserInitials(user?.fullName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = OnEmeraldPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.fullName?.ifBlank { "Agent" } ?: "Agent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                user?.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                user?.email?.takeIf { it.isNotBlank() }?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            val accountActive = (user?.accountStatus ?: "ACTIVE").equals("ACTIVE", ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (accountActive) StatusSuccessBg else StatusErrorBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = (user?.accountStatus ?: "ACTIVE").uppercase(Locale.US),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (accountActive) StatusSuccess else StatusError
                )
            }
        }

        formatIsoTimestamp(user?.createdAt)?.let { memberSince ->
            Spacer(modifier = Modifier.height(6.dp))
            ProfileDetailRow(label = "Member Since", value = memberSince)
        }
        Spacer(modifier = Modifier.height(4.dp))
        ProfileDetailRow(label = "Active Device Limit", value = "Max 2 devices per account")

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = TerminalBorder)
        Spacer(modifier = Modifier.height(10.dp))

        // 3. Subscription & Trial Details Subsection
        Text(
            text = "Subscription & Trial Details",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = EmeraldPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        TrialSubscriptionBanner(
            subscription = subscription,
            onClick = { billingExpanded = !billingExpanded }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = TerminalBackground,
            border = BorderStroke(1.dp, TerminalBorder)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statusLabel = when (effectiveStatus) {
                    SubscriptionStatus.TRIAL -> "Active Free Trial"
                    SubscriptionStatus.ACTIVE -> "Active Subscription"
                    SubscriptionStatus.EXPIRED -> "Expired"
                    SubscriptionStatus.CANCELLED -> "Cancelled"
                    SubscriptionStatus.NONE -> "Inactive"
                }
                val statusColor = when (effectiveStatus) {
                    SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE -> StatusSuccess
                    SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED -> StatusError
                    SubscriptionStatus.NONE -> StatusWarning
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Access Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                val currentPlanDisplay = subscription.currentPlanName?.takeIf { it.isNotBlank() }
                    ?: if (effectiveStatus == SubscriptionStatus.TRIAL) "15-Day Free Trial" else "No active plan"
                ProfileDetailRow(label = "Current Plan", value = currentPlanDisplay)

                val remainingDisplay = when {
                    !hasActiveAccess -> "0 days (Inactive / Expired)"
                    effectiveDays <= 0 -> "Ends today"
                    effectiveDays == 1 -> "1 day remaining"
                    else -> "$effectiveDays days remaining"
                }
                ProfileDetailRow(label = "Time Remaining", value = remainingDisplay)

                formatIsoTimestamp(subscription.trialStartedAt)?.let { started ->
                    ProfileDetailRow(label = "Trial Started", value = started)
                }
                formatIsoTimestamp(subscription.trialExpiresAt)?.let { expires ->
                    ProfileDetailRow(label = "Trial Expires", value = expires)
                }
                formatIsoTimestamp(subscription.paidUntil)?.let { paidUntil ->
                    ProfileDetailRow(label = "Paid Until", value = paidUntil)
                }

                ProfileDetailRow(
                    label = "Billing Mode",
                    value = if (subscription.recurringAuthorized) "Auto-renew authorized" else "Manual renewal"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasActiveAccess) {
            TerminalActionButton(
                label = if (billingExpanded) "Hide Subscription Plans" else "Subscribe / Renew Plan (Paystack)",
                onClick = { billingExpanded = !billingExpanded },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            TerminalOutlinedButton(
                label = if (billingExpanded) "Hide Plans & Payment History" else "Manage Plan, Paystack & Payment History",
                onClick = { billingExpanded = !billingExpanded },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 4. Expandable Billing, Plans & Payment History Panel
        if (billingExpanded) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = TerminalBorder)
            Spacer(modifier = Modifier.height(12.dp))

            authUiState.checkoutStatusMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusSuccessBg)
                        .padding(10.dp)
                ) {
                    Text(msg, color = StatusSuccess, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            authUiState.errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusErrorBg)
                        .padding(10.dp)
                ) {
                    Text(err, color = StatusError, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (authUiState.pendingReference != null) {
                TerminalOutlinedButton(
                    label = "Verify Pending Payment (${authUiState.pendingReference})",
                    onClick = { authViewModel.verifyPendingOrRefresh(authUiState.pendingReference) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = "Available Plans (Paystack GHS)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (authUiState.plans.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = TerminalBackground,
                    border = BorderStroke(1.dp, TerminalBorder)
                ) {
                    Text(
                        text = "Connect to the internet to load subscription plans from the server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    authUiState.plans.forEach { plan ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalBackground,
                            border = BorderStroke(1.dp, TerminalBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plan.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${plan.currency} ${String.format(Locale.US, "%.2f", plan.price)} / ${plan.billingInterval}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = EmeraldPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TerminalActionButton(
                                    label = "Pay with Paystack",
                                    enabled = plan.active && !authUiState.isLoading,
                                    onClick = { authViewModel.startPaystackCheckout(plan.id) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Payment History",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (authUiState.paymentHistory.isEmpty()) {
                Text(
                    text = "No payments recorded yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    authUiState.paymentHistory.forEach { payment ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalBackground,
                            border = BorderStroke(1.dp, TerminalBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = payment.plan?.name ?: "Subscription",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Ref: ${payment.reference}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    formatIsoTimestamp(payment.paidAt)?.let { paidDate ->
                                        Text(
                                            text = paidDate,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${payment.currency} ${String.format(Locale.US, "%.2f", payment.amount)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${payment.status} • ${payment.channel ?: "Paystack"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    webViewFallbackUrl?.let { url ->
        Dialog(
            onDismissRequest = {
                webViewFallbackUrl = null
                authViewModel.verifyPendingOrRefresh()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Paystack Checkout") },
                        navigationIcon = {
                            TextButton(onClick = {
                                webViewFallbackUrl = null
                                authViewModel.verifyPendingOrRefresh()
                            }) {
                                Text("Done")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    requestUrl: String?
                                ): Boolean {
                                    if (requestUrl != null && requestUrl.startsWith("easymomo://")) {
                                        webViewFallbackUrl = null
                                        authViewModel.verifyPendingOrRefresh()
                                        return true
                                    }
                                    return false
                                }
                            }
                            loadUrl(url)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileDetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
