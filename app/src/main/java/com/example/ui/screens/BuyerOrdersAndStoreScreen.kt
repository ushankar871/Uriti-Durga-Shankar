package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.OrderStatus
import com.example.model.SellerOrder
import com.example.model.UiState
import com.example.ui.BuyerViewModel
import com.example.ui.OrderHistoryFilter
import com.example.ui.components.AnimatedOrderProgressTracker
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BuyerOrdersScreen(
    viewModel: BuyerViewModel,
    onOpenShop: () -> Unit = {},
    onOpenStoreReviews: () -> Unit = {},
    onTrackOrder: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ordersState by viewModel.buyerOrdersState.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.orderHistoryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.orderSearchQuery.collectAsStateWithLifecycle()

    var expandedOrderId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Purchase History", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Track active deliveries and review past orders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val allOrders = (ordersState as? UiState.Success)?.data ?: emptyList()
                if (allOrders.any { it.statusEnum() == OrderStatus.PENDING }) {
                    OutlinedButton(
                        onClick = { viewModel.requestClearPendingOrdersWithOtp() },
                        modifier = Modifier.testTag("buyer_cancel_pending_otp_button")
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Pending (OTP)")
                    }
                }
            }

            when (val state = ordersState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                is UiState.Success -> {
                    val orders = state.data
                    if (orders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("No purchase history yet", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Place your first order from the Shop tab to track live delivery milestones, dates, and receipts here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenShop,
                                    modifier = Modifier.testTag("empty_history_shop_button")
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Browse Store")
                                }
                            }
                        }
                    } else {
                        val activeCount = remember(orders) { orders.count { it.statusEnum() != OrderStatus.DELIVERED } }
                        val deliveredCount = remember(orders) { orders.count { it.statusEnum() == OrderStatus.DELIVERED } }
                        val totalSpent = remember(orders) { orders.sumOf { it.totalInr } }

                        val filteredOrders = remember(orders, selectedFilter, searchQuery) {
                            val q = searchQuery.trim()
                            orders.filter { order ->
                                val matchesFilter = selectedFilter.matches(order.statusEnum())
                                val matchesQuery = q.isEmpty() ||
                                    order.storeName.contains(q, ignoreCase = true) ||
                                    order.itemsSummary.contains(q, ignoreCase = true) ||
                                    order.neighborhood.contains(q, ignoreCase = true) ||
                                    order.id.contains(q, ignoreCase = true)
                                matchesFilter && matchesQuery
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "history_summary") {
                                PurchaseSummaryBanner(
                                    totalOrders = orders.size,
                                    activeCount = activeCount,
                                    deliveredCount = deliveredCount,
                                    totalSpentInr = totalSpent
                                )
                            }

                            val latestActiveOrder = orders.firstOrNull { it.statusEnum() != OrderStatus.DELIVERED }
                            if (latestActiveOrder != null) {
                                item(key = "active_live_map_banner") {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onTrackOrder(latestActiveOrder.id) }
                                            .testTag("orders_active_live_map_banner"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Live Delivery Tracking • ${latestActiveOrder.statusEnum().label}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "${latestActiveOrder.storeName} -> ${latestActiveOrder.neighborhood}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { onTrackOrder(latestActiveOrder.id) },
                                                modifier = Modifier.testTag("orders_banner_track_map_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Map,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Track Live")
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "history_search") {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setOrderSearchQuery(it) },
                                    placeholder = { Text("Search by item, store, or area...") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setOrderSearchQuery("") }) {
                                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("buyer_order_search_input")
                                )
                            }

                            item(key = "history_filters") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(OrderHistoryFilter.entries, key = { it.name }) { filter ->
                                        val count = orders.count { filter.matches(it.statusEnum()) }
                                        FilterChip(
                                            selected = selectedFilter == filter,
                                            onClick = { viewModel.setOrderHistoryFilter(filter) },
                                            label = { Text("${filter.label} ($count)") },
                                            modifier = Modifier.testTag("order_filter_${filter.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            if (filteredOrders.isEmpty()) {
                                item(key = "no_matching_orders") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ReceiptLong,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "No matching orders found",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            TextButton(
                                                onClick = {
                                                    viewModel.setOrderHistoryFilter(OrderHistoryFilter.ALL)
                                                    viewModel.setOrderSearchQuery("")
                                                }
                                            ) {
                                                Text("Reset filters")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(filteredOrders, key = { it.id }) { order ->
                                    val isExpanded = expandedOrderId == order.id
                                    PurchaseHistoryOrderCard(
                                        order = order,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            expandedOrderId = if (isExpanded) null else order.id
                                        },
                                        onShopAgain = {
                                            viewModel.selectStore(order.sellerId)
                                            onOpenShop()
                                        },
                                        onRateStore = {
                                            viewModel.selectStore(order.sellerId)
                                            onOpenStoreReviews()
                                        },
                                        onTrackLiveMap = { onTrackOrder(order.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseSummaryBanner(
    totalOrders: Int,
    activeCount: Int,
    deliveredCount: Int,
    totalSpentInr: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryMetric(label = "Total", value = totalOrders.toString(), modifier = Modifier.weight(1f))
            SummaryMetric(label = "Active", value = activeCount.toString(), modifier = Modifier.weight(1f))
            SummaryMetric(label = "Delivered", value = deliveredCount.toString(), modifier = Modifier.weight(1f))
            SummaryMetric(label = "Spent", value = "₹${totalSpentInr.toInt()}", modifier = Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun PurchaseHistoryOrderCard(
    order: SellerOrder,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onShopAgain: () -> Unit,
    onRateStore: () -> Unit,
    onTrackLiveMap: () -> Unit
) {
    val status = order.statusEnum()
    val shortRef = remember(order.id) {
        order.id.takeLast(6).uppercase(Locale.US).ifBlank { "ORDER" }
    }
    val placedDate = remember(order.createdAt) { formatOrderTimestamp(order.createdAt) }
    val updatedDate = remember(order.updatedAt) { formatOrderTimestamp(order.updatedAt) }

    val statusBg = when (status) {
        OrderStatus.DELIVERED -> MaterialTheme.colorScheme.tertiaryContainer
        OrderStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.primaryContainer
        OrderStatus.PACKED -> MaterialTheme.colorScheme.secondaryContainer
        OrderStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusFg = when (status) {
        OrderStatus.DELIVERED -> MaterialTheme.colorScheme.onTertiaryContainer
        OrderStatus.OUT_FOR_DELIVERY -> MaterialTheme.colorScheme.onPrimaryContainer
        OrderStatus.PACKED -> MaterialTheme.colorScheme.onSecondaryContainer
        OrderStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .testTag("buyer_order_card_${order.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = order.storeName.ifBlank { "Lakshmi Store" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$placedDate • #$shortRef",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = order.itemsSummary,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = order.neighborhood,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "₹${order.totalInr.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OrderTrackingTimeline(currentStatus = status)

            if (status != OrderStatus.DELIVERED && order.deliveryOtp.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Password,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Delivery Handoff OTP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = order.deliveryOtp,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (status == OrderStatus.DELIVERED && order.deliveryMethod.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (order.deliveryMethod == "PHOTO") Icons.Filled.PhotoCamera else Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (order.deliveryMethod == "PHOTO") {
                                    "Confirmed via Delivery Photograph"
                                } else {
                                    "Confirmed via Buyer OTP"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (order.deliveryProofUri.isNotBlank()) {
                            AsyncImage(
                                model = order.deliveryProofUri,
                                contentDescription = "Delivery confirmation photo proof",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Order Ref",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "#$shortRef",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Recipient",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = order.customerName.ifBlank { "Customer" },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Placed On",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = placedDate, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Last Status Update",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(text = updatedDate, style = MaterialTheme.typography.labelMedium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onShopAgain,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reorder_button_${order.id}")
                        ) {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shop Store")
                        }
                        if (status == OrderStatus.DELIVERED) {
                            Button(
                                onClick = onRateStore,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rate_order_button_${order.id}")
                            ) {
                                Icon(Icons.Filled.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rate Store")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onTrackLiveMap,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("track_order_map_btn_${order.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (status == OrderStatus.DELIVERED) "View Route Map" else "Track Live Map")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Hide order details" else "Show order details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isExpanded) "Hide details" else "Receipt details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderTrackingTimeline(currentStatus: OrderStatus) {
    AnimatedOrderProgressTracker(
        currentStatus = currentStatus,
        compact = false
    )
}

private fun formatOrderTimestamp(timestamp: Timestamp?): String {
    if (timestamp == null) return "Just now"
    val formatter = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
    return formatter.format(timestamp.toDate())
}

@Composable
fun BuyerStoreInfoScreen(
    viewModel: BuyerViewModel,
    onTrackOrder: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val activeStore by viewModel.activeStore.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val ordersState by viewModel.buyerOrdersState.collectAsStateWithLifecycle()
    var showFeedbackSheet by remember { mutableStateOf(false) }

    val feedbackList = (feedbackState as? UiState.Success)?.data ?: emptyList()
    val avgRating = if (feedbackList.isNotEmpty()) feedbackList.map { it.rating.toDouble() }.average() else 0.0
    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val store = activeStore
        if (store == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select or open a store to view map & reviews.")
            }
        } else {
            val activeOrderForStore = orders.firstOrNull {
                it.sellerId == store.userId && it.statusEnum() != OrderStatus.DELIVERED
            }
            val pos = LatLng(store.storeLat, store.storeLng)
            val cameraState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(pos, 13f)
            }
            LaunchedEffect(store.storeLat, store.storeLng) {
                cameraState.animate(CameraUpdateFactory.newLatLngZoom(pos, 13f))
            }
            var isMapGestureEnabled by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${store.storeName} • Delivery Map & Reviews",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )

                if (activeOrderForStore != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackOrder(activeOrderForStore.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Active Order • ${activeOrderForStore.statusEnum().label}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeOrderForStore.itemsSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(onClick = { onTrackOrder(activeOrderForStore.id) }) {
                                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Live Map")
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraState,
                        uiSettings = com.google.maps.android.compose.MapUiSettings(
                            zoomControlsEnabled = true,
                            scrollGesturesEnabled = isMapGestureEnabled,
                            zoomGesturesEnabled = isMapGestureEnabled,
                            rotationGesturesEnabled = isMapGestureEnabled,
                            tiltGesturesEnabled = isMapGestureEnabled
                        )
                    ) {
                        Marker(
                            state = MarkerState(position = pos),
                            title = store.storeName,
                            snippet = store.address
                        )
                        Circle(
                            center = pos,
                            radius = store.deliveryRadiusKm * 1000.0,
                            fillColor = Color(0x281B6B46),
                            strokeColor = Color(0xFF1B6B46),
                            strokeWidth = 4f
                        )
                        if (activeOrderForStore != null) {
                            val liveLat = activeOrderForStore.riderLat ?: store.storeLat
                            val liveLng = activeOrderForStore.riderLng ?: store.storeLng
                            val livePos = LatLng(liveLat, liveLng)
                            val destPos = LatLng(activeOrderForStore.lat, activeOrderForStore.lng)
                            Polyline(
                                points = listOf(pos, livePos, destPos),
                                color = Color(0xFF1E88E5),
                                width = 7f
                            )
                            Marker(
                                state = MarkerState(position = livePos),
                                title = "Your Order (${activeOrderForStore.statusEnum().label})",
                                snippet = activeOrderForStore.neighborhood,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                            Marker(
                                state = MarkerState(position = destPos),
                                title = "Drop Pin",
                                snippet = activeOrderForStore.neighborhood,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isMapGestureEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                        },
                        shadowElevation = 3.dp,
                        onClick = { isMapGestureEnabled = !isMapGestureEnabled },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .testTag("toggle_buyer_store_map_gestures_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isMapGestureEnabled) Icons.Filled.PanTool else Icons.Filled.SwipeVertical,
                                contentDescription = null,
                                tint = if (isMapGestureEnabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isMapGestureEnabled) "Panning Map (Tap to Scroll)" else "Move Map",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isMapGestureEnabled) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (feedbackList.isEmpty()) "No Reviews Yet" else String.format(Locale.US, "%.1f ★", avgRating),
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Text(
                                text = "${feedbackList.size} shopper ratings",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Button(onClick = { showFeedbackSheet = true }) {
                            Icon(Icons.Filled.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Write Review")
                        }
                    }
                }

                feedbackList.forEach { fb ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(fb.authorName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${fb.rating} ★ • ${fb.categoryEnum().label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(fb.comment, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showFeedbackSheet) {
        FeedbackBottomSheet(
            recentFeedback = feedbackList.take(5),
            onDismiss = { showFeedbackSheet = false },
            onSubmit = { name, rating, cat, comment ->
                viewModel.submitFeedback(name, rating, cat, comment)
                showFeedbackSheet = false
            }
        )
    }
}
