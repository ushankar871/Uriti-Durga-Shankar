package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.OrderStatus

private data class DeliveryStageUi(
    val status: OrderStatus,
    val label: String
)

private val DELIVERY_STAGES = listOf(
    DeliveryStageUi(OrderStatus.PENDING, "Ordered"),
    DeliveryStageUi(OrderStatus.PACKED, "Packed"),
    DeliveryStageUi(OrderStatus.OUT_FOR_DELIVERY, "Out for Delivery"),
    DeliveryStageUi(OrderStatus.DELIVERED, "Delivered")
)

@Composable
fun AnimatedOrderProgressTracker(
    currentStatus: OrderStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val activeIndex = DELIVERY_STAGES.indexOfFirst { it.status == currentStatus }.coerceAtLeast(0)
    val targetFraction = when (activeIndex) {
        0 -> 0.08f
        1 -> 0.36f
        2 -> 0.69f
        else -> 1f
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "order_progress_fraction"
    )

    val isDelivered = currentStatus == OrderStatus.DELIVERED
    val activeTrackColor by animateColorAsState(
        targetValue = if (isDelivered) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        animationSpec = tween(durationMillis = 400),
        label = "order_track_color"
    )
    val trackBgColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val pulseTransition = rememberInfiniteTransition(label = "order_stage_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isDelivered) 1f else 1.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = if (isDelivered) 0f else 0.42f,
        targetValue = if (isDelivered) 0f else 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val shimmerOffset by pulseTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "track_shimmer"
    )

    if (compact) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("animated_order_progress_compact"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            ) {
                val corner = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackBgColor, cornerRadius = corner)
                val fillW = (size.width * animatedFraction).coerceIn(0f, size.width)
                if (fillW > 0f) {
                    drawRoundRect(
                        color = activeTrackColor,
                        size = Size(fillW, size.height),
                        cornerRadius = corner
                    )
                    if (!isDelivered) {
                        val glowX = fillW * shimmerOffset.coerceIn(0f, 1f)
                        drawCircle(
                            color = activeTrackColor.copy(alpha = 0.35f),
                            radius = size.height * 1.4f,
                            center = Offset(glowX, size.height / 2f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DELIVERY_STAGES.forEachIndexed { idx, stage ->
                    val reached = idx <= activeIndex
                    val isCurrent = idx == activeIndex
                    val textColor by animateColorAsState(
                        targetValue = when {
                            isCurrent -> activeTrackColor
                            reached -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 300),
                        label = "compact_label_color_$idx"
                    )
                    Text(
                        text = stage.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("animated_order_progress_timeline"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Continuous smooth animated connector track aligned behind stage nodes
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(5.dp)
                        .align(Alignment.TopCenter)
                        .padding(top = 0.dp)
                ) {
                    val yCenter = 15.dp.toPx()
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(
                        color = trackBgColor,
                        topLeft = Offset(0f, yCenter - size.height / 2f),
                        size = Size(size.width, size.height),
                        cornerRadius = corner
                    )
                    val fillWidth = (size.width * animatedFraction).coerceIn(0f, size.width)
                    if (fillWidth > 0f) {
                        val brush = if (!isDelivered) {
                            Brush.horizontalGradient(
                                colors = listOf(
                                    activeTrackColor,
                                    activeTrackColor.copy(alpha = 0.72f),
                                    activeTrackColor
                                ),
                                startX = 0f,
                                endX = fillWidth.coerceAtLeast(1f)
                            )
                        } else {
                            Brush.horizontalGradient(
                                colors = listOf(activeTrackColor, activeTrackColor)
                            )
                        }
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(0f, yCenter - size.height / 2f),
                            size = Size(fillWidth, size.height),
                            cornerRadius = corner
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    DELIVERY_STAGES.forEachIndexed { index, stage ->
                        val completed = index <= activeIndex
                        val isCurrent = index == activeIndex
                        val nodeSize by animateDpAsState(
                            targetValue = if (isCurrent) 30.dp else if (completed) 24.dp else 22.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "stage_node_size_$index"
                        )
                        val nodeColor by animateColorAsState(
                            targetValue = when {
                                completed -> activeTrackColor
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            animationSpec = tween(durationMillis = 350),
                            label = "stage_node_color_$index"
                        )
                        val labelColor by animateColorAsState(
                            targetValue = when {
                                isCurrent -> activeTrackColor
                                completed -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(durationMillis = 350),
                            label = "stage_label_color_$index"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCurrent && !isDelivered) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .scale(pulseScale)
                                            .clip(CircleShape)
                                            .background(activeTrackColor.copy(alpha = pulseAlpha))
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(nodeSize)
                                        .clip(CircleShape)
                                        .background(nodeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val icon = when (stage.status) {
                                        OrderStatus.PENDING -> Icons.Filled.ReceiptLong
                                        OrderStatus.PACKED -> Icons.Filled.Inventory2
                                        OrderStatus.OUT_FOR_DELIVERY -> Icons.Filled.LocalShipping
                                        OrderStatus.DELIVERED -> Icons.Filled.Check
                                    }
                                    if (completed) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = stage.label,
                                            tint = if (isDelivered) {
                                                MaterialTheme.colorScheme.onTertiary
                                            } else {
                                                MaterialTheme.colorScheme.onPrimary
                                            },
                                            modifier = Modifier.size(if (isCurrent) 16.dp else 13.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.outline)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stage.label,
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = labelColor,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
