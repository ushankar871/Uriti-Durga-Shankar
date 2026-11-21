package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.SellerOrder
import java.util.Locale

private enum class AnalyticsChartTab(val label: String) {
    MOST_PURCHASED("Top Purchased"),
    NEARING_STOCK("Stock Risk")
}

private data class PurchasedItemStat(
    val name: String,
    val unitsOrdered: Int,
    val orderCount: Int,
    val category: ProductCategory?,
    val currentStock: Long?
)

private data class StockRiskItemStat(
    val product: Product,
    val stockQty: Long
)

@Composable
fun SellerInventoryChartCard(
    products: List<Product>,
    orders: List<SellerOrder>,
    lowStockThreshold: Long,
    onQuickRestock: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(AnalyticsChartTab.MOST_PURCHASED) }
    var selectedBarKey by remember { mutableStateOf<String?>(null) }

    val topPurchased = remember(orders, products) {
        computeMostPurchasedBreakdown(orders = orders, products = products).take(6)
    }
    val stockRiskItems = remember(products, lowStockThreshold) {
        val watchCeiling = (lowStockThreshold * 2L).coerceAtLeast(lowStockThreshold + 4L)
        products
            .filter { !it.inStock || it.stockQty <= watchCeiling }
            .sortedWith(compareBy<Product> { if (!it.inStock) 0L else it.stockQty }.thenBy { it.name.lowercase() })
            .take(6)
            .map { StockRiskItemStat(product = it, stockQty = if (it.inStock) it.stockQty else 0L) }
    }

    val totalUnitsSold = remember(orders, products) {
        computeMostPurchasedBreakdown(orders = orders, products = products).sumOf { it.unitsOrdered }
    }
    val atRiskCount = remember(products, lowStockThreshold) {
        products.count { it.isLowStock(lowStockThreshold) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("seller_analytics_chart_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Inventory & Sales Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (activeTab == AnalyticsChartTab.MOST_PURCHASED) {
                            "$totalUnitsSold total units ordered across ${orders.size} orders"
                        } else {
                            "$atRiskCount items at/below $lowStockThreshold-unit alert line"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = if (activeTab == AnalyticsChartTab.MOST_PURCHASED) {
                            "Top ${topPurchased.size}"
                        } else {
                            "Threshold ≤ $lowStockThreshold"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analytics_chart_tab_row")
            ) {
                AnalyticsChartTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = activeTab == tab,
                        onClick = {
                            activeTab = tab
                            selectedBarKey = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = AnalyticsChartTab.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (tab == AnalyticsChartTab.MOST_PURCHASED) {
                                    Icons.Filled.TrendingUp
                                } else {
                                    Icons.Filled.BarChart
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag(
                            if (tab == AnalyticsChartTab.MOST_PURCHASED) {
                                "chart_tab_most_purchased"
                            } else {
                                "chart_tab_stock_risk"
                            }
                        )
                    ) {
                        Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            when (activeTab) {
                AnalyticsChartTab.MOST_PURCHASED -> {
                    MostPurchasedBarChart(
                        items = topPurchased,
                        selectedName = selectedBarKey,
                        onSelectBar = { name ->
                            selectedBarKey = if (selectedBarKey == name) null else name
                        }
                    )
                }

                AnalyticsChartTab.NEARING_STOCK -> {
                    LowStockThresholdBarChart(
                        items = stockRiskItems,
                        threshold = lowStockThreshold,
                        selectedId = selectedBarKey,
                        onSelectBar = { id ->
                            selectedBarKey = if (selectedBarKey == id) null else id
                        },
                        onQuickRestock = onQuickRestock
                    )
                }
            }
        }
    }
}

@Composable
private fun MostPurchasedBarChart(
    items: List<PurchasedItemStat>,
    selectedName: String?,
    onSelectBar: (String) -> Unit
) {
    if (items.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "No purchase data yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "When buyers place orders (or you log a test order in Seller Hub), top-selling puja & grocery bars appear here live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val maxUnits = remember(items) { (items.maxOfOrNull { it.unitsOrdered } ?: 1).coerceAtLeast(1) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(color = primaryColor, label = "Puja Needs")
            ChartLegendDot(color = secondaryColor, label = "Fancy Items")
            ChartLegendDot(color = tertiaryColor, label = "Groceries")
        }

        items.forEach { stat ->
            val fractionTarget = (stat.unitsOrdered.toFloat() / maxUnits.toFloat()).coerceIn(0.06f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fractionTarget,
                animationSpec = tween(durationMillis = 550),
                label = "purchased_bar_${stat.name}"
            )
            val barColor = when (stat.category) {
                ProductCategory.PUJA -> primaryColor
                ProductCategory.FANCY -> secondaryColor
                ProductCategory.GROCERY -> tertiaryColor
                null -> MaterialTheme.colorScheme.outline
            }
            val isSelected = selectedName == stat.name

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) barColor.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectBar(stat.name) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stat.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stat.unitsOrdered} sold (${stat.orderCount} ord)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        drawRoundRect(
                            color = trackColor,
                            size = size,
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        drawRoundRect(
                            color = barColor,
                            size = Size(width = size.width * animatedFraction, height = size.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }

                    if (isSelected) {
                        Text(
                            text = buildString {
                                append("${stat.category?.displayName ?: "Catalog"} • ")
                                append("${stat.unitsOrdered} units across ${stat.orderCount} order(s)")
                                stat.currentStock?.let { append(" • $it currently in stock") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LowStockThresholdBarChart(
    items: List<StockRiskItemStat>,
    threshold: Long,
    selectedId: String?,
    onSelectBar: (String) -> Unit,
    onQuickRestock: (Product) -> Unit
) {
    if (items.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Healthy stock across all items! Everything is well above the $threshold-unit low-stock threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        return
    }

    val chartCeiling = remember(items, threshold) {
        val maxQty = items.maxOfOrNull { it.stockQty } ?: 0L
        maxOf(maxQty, threshold * 2L, threshold + 4L).coerceAtLeast(1L)
    }
    val thresholdRatio = remember(threshold, chartCeiling) {
        (threshold.toFloat() / chartCeiling.toFloat()).coerceIn(0.05f, 0.95f)
    }

    val errorColor = MaterialTheme.colorScheme.error
    val warningColor = Color(0xFFE65100)
    val safeWatchColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChartLegendDot(color = errorColor, label = "Out (0)")
            ChartLegendDot(color = warningColor, label = "Low (≤$threshold)")
            ChartLegendDot(color = safeWatchColor, label = "Nearing")
        }

        items.forEach { entry ->
            val product = entry.product
            val qty = entry.stockQty
            val fractionTarget = if (qty <= 0L) 0.04f else (qty.toFloat() / chartCeiling.toFloat()).coerceIn(0.06f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fractionTarget,
                animationSpec = tween(durationMillis = 550),
                label = "risk_bar_${product.id}"
            )
            val barColor = when {
                !product.inStock || qty <= 0L -> errorColor
                qty <= threshold -> warningColor
                else -> safeWatchColor
            }
            val isSelected = selectedId == product.id

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) barColor.copy(alpha = 0.12f) else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectBar(product.id) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (qty <= threshold) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = barColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = product.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (qty <= 0L) "0 left (Out)" else "$qty / $threshold thr",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = barColor
                        )
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        drawRoundRect(
                            color = trackColor,
                            size = size,
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        drawRoundRect(
                            color = barColor,
                            size = Size(width = size.width * animatedFraction, height = size.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                        // Dashed threshold marker line across the bar
                        val thresholdX = size.width * thresholdRatio
                        drawLine(
                            color = Color(0xFF1F1F1F).copy(alpha = 0.65f),
                            start = Offset(thresholdX, 0f),
                            end = Offset(thresholdX, size.height),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    }

                    if (isSelected) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${product.categoryEnum().displayName} • ${product.unit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { onQuickRestock(product) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("chart_quick_restock_${product.id}")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+5 Stock", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Dashed marker shows your $threshold-unit low-stock threshold. Tap any bar to inspect or restock.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun computeMostPurchasedBreakdown(
    orders: List<SellerOrder>,
    products: List<Product>
): List<PurchasedItemStat> {
    if (orders.isEmpty()) return emptyList()

    val productsByLowerName = products.associateBy { it.name.trim().lowercase(Locale.US) }
    val itemPattern = Regex("""^(?:(\d+)\s*[xX]\s+)?(.+)$""")

    data class Accumulator(
        val displayName: String,
        var units: Int = 0,
        var orderCount: Int = 0
    )

    val counts = linkedMapOf<String, Accumulator>()

    orders.forEach { order ->
        val seenInThisOrder = hashSetOf<String>()
        order.itemsSummary
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { token ->
                val match = itemPattern.matchEntire(token)
                val qty = match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val rawName = match?.groupValues?.getOrNull(2)?.trim()?.ifBlank { token } ?: token
                val normalized = rawName.lowercase(Locale.US)

                val matchedProduct = productsByLowerName[normalized]
                    ?: products.firstOrNull {
                        it.name.equals(rawName, ignoreCase = true) ||
                            it.name.contains(rawName, ignoreCase = true) ||
                            rawName.contains(it.name, ignoreCase = true)
                    }
                val canonicalKey = (matchedProduct?.name ?: rawName).trim().lowercase(Locale.US)
                val canonicalDisplay = matchedProduct?.name ?: rawName

                val acc = counts.getOrPut(canonicalKey) { Accumulator(displayName = canonicalDisplay) }
                acc.units += qty
                if (seenInThisOrder.add(canonicalKey)) {
                    acc.orderCount += 1
                }
            }
    }

    return counts.values
        .map { acc ->
            val matchedProduct = productsByLowerName[acc.displayName.lowercase(Locale.US)]
                ?: products.firstOrNull { it.name.equals(acc.displayName, ignoreCase = true) }
            PurchasedItemStat(
                name = acc.displayName,
                unitsOrdered = acc.units,
                orderCount = acc.orderCount,
                category = matchedProduct?.categoryEnum(),
                currentStock = matchedProduct?.let { if (it.inStock) it.stockQty else 0L }
            )
        }
        .sortedWith(compareByDescending<PurchasedItemStat> { it.unitsOrdered }.thenByDescending { it.orderCount })
}
