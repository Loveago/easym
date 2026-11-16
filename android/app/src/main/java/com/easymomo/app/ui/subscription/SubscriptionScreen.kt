package com.easymomo.app.ui.subscription

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.easymomo.app.ui.auth.AuthViewModel
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.StatusSuccess
import com.easymomo.app.ui.theme.StatusSuccessBg
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TerminalOutlinedButton
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import com.easymomo.app.ui.theme.TrialSubscriptionBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by authViewModel.uiState.collectAsState()
    val subscription by authViewModel.subscription.collectAsState()
    var webViewFallbackUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authViewModel.loadSubscriptionDetails()
    }

    // Verify pending Paystack checkout automatically when returning from Custom Tabs
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.pendingReference != null) {
                authViewModel.verifyPendingOrRefresh(uiState.pendingReference)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launch Paystack Checkout URL in Chrome Custom Tabs (with WebView fallback)
    LaunchedEffect(uiState.pendingCheckoutUrl) {
        val url = uiState.pendingCheckoutUrl ?: return@LaunchedEffect
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

    Scaffold(
        containerColor = TerminalBackground,
        topBar = {
            TopAppBar(
                title = { Text("Subscription & Paystack Billing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { authViewModel.verifyPendingOrRefresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh status")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TrialSubscriptionBanner(
                    subscription = subscription,
                    onClick = { authViewModel.verifyPendingOrRefresh() }
                )
            }

            item {
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Entitlement Details", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Status: ${subscription.status.code}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    subscription.trialExpiresAt?.let {
                        Text("Trial expires (UTC): $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    subscription.paidUntil?.let {
                        Text("Paid until (UTC): $it", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            uiState.checkoutStatusMessage?.let { msg ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusSuccessBg)
                            .padding(12.dp)
                    ) {
                        Text(msg, color = StatusSuccess, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            uiState.errorMessage?.let { err ->
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusErrorBg)
                            .padding(12.dp)
                    ) {
                        Text(err, color = StatusError, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (uiState.pendingReference != null) {
                item {
                    TerminalOutlinedButton(
                        label = "Verify Pending Payment (${uiState.pendingReference})",
                        onClick = { authViewModel.verifyPendingOrRefresh(uiState.pendingReference) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Text("Available Plans (Paystack GHS)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }

            if (uiState.plans.isEmpty()) {
                item {
                    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Connect to the internet to load subscription plans from the server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(uiState.plans) { plan ->
                    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    text = "${plan.currency} ${String.format("%.2f", plan.price)} / ${plan.billingInterval}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldPrimary
                                )
                            }
                            TerminalActionButton(
                                label = "Pay with Paystack",
                                enabled = plan.active && !uiState.isLoading,
                                onClick = { authViewModel.startPaystackCheckout(plan.id) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Payment History", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }

            if (uiState.paymentHistory.isEmpty()) {
                item {
                    Text(
                        text = "No payments recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                items(uiState.paymentHistory) { payment ->
                    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(payment.plan?.name ?: "Subscription", style = MaterialTheme.typography.titleMedium)
                                Text("Ref: ${payment.reference}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                payment.paidAt?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${payment.currency} ${String.format("%.2f", payment.amount)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    "${payment.status} • ${payment.channel ?: "Paystack"}",
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
                                override fun shouldOverrideUrlLoading(view: WebView?, requestUrl: String?): Boolean {
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
