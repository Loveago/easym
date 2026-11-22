package com.easymomo.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.SubscriptionInfo
import com.easymomo.app.domain.model.SubscriptionStatus
import com.easymomo.app.domain.model.TransactionStatus

@Composable
fun EasyMoMoCard(
    modifier: Modifier = Modifier,
    containerColor: Color = TerminalSurface,
    borderColor: Color = TerminalBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun TerminalActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = EmeraldPrimary,
    contentColor: Color = Color.White,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = TerminalSurfaceVariant,
            disabledContentColor = TextMuted
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TerminalOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (enabled) EmeraldPrimary else TerminalBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldPrimary),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun NetworkBadge(
    network: NetworkType,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (network) {
        NetworkType.MTN -> MtnYellow to MtnOnYellow
        NetworkType.AT -> AtBlue to AtOnBlue
        NetworkType.TELECEL -> TelecelRed to TelecelOnRed
        NetworkType.UNKNOWN -> TerminalSurfaceVariant to TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = network.displayName,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusPill(
    status: TransactionStatus,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (status) {
        TransactionStatus.SUCCESSFUL -> StatusSuccessBg to StatusSuccess
        TransactionStatus.FAILED, TransactionStatus.CANCELLED -> StatusErrorBg to StatusError
        TransactionStatus.PROCESSING, TransactionStatus.AWAITING_CUSTOMER, TransactionStatus.AWAITING_VERIFICATION -> StatusInfoBg to StatusInfo
        TransactionStatus.UNKNOWN -> StatusWarningBg to StatusWarning
        TransactionStatus.DRAFT -> TerminalSurfaceVariant to TextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.displayName,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TrialSubscriptionBanner(
    subscription: SubscriptionInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveStatus = subscription.effectiveStatus()
    val effectiveDays = subscription.effectiveRemainingDays()
    val (bg, fg, text) = when (effectiveStatus) {
        SubscriptionStatus.TRIAL -> {
            val label = when {
                effectiveDays <= 0 -> "Free Trial ends today"
                effectiveDays == 1 -> "Free Trial Active • 1 day remaining"
                else -> "Free Trial Active • $effectiveDays days remaining"
            }
            Triple(EmeraldPrimaryContainer, OnEmeraldPrimaryContainer, label)
        }
        SubscriptionStatus.ACTIVE -> Triple(
            StatusSuccessBg,
            StatusSuccess,
            "${subscription.currentPlanName ?: "Active Plan"} • ${effectiveDays}d left"
        )
        SubscriptionStatus.EXPIRED ->
            Triple(StatusErrorBg, StatusError, "Subscription Expired • Renew to continue")
        SubscriptionStatus.CANCELLED ->
            Triple(StatusErrorBg, StatusError, "Subscription Cancelled • Subscribe to continue")
        SubscriptionStatus.NONE ->
            Triple(StatusWarningBg, StatusWarning, "No Active Subscription • Subscribe to unlock features")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (!subscription.hasActiveAccess()) "SUBSCRIBE" else "MANAGE",
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(44.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}
