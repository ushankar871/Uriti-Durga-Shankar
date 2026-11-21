package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.FeedbackCategory
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.SellerOrder
import com.example.model.StoreFeedback
import com.example.model.UiState
import com.example.notifications.LowStockNotifier
import com.example.ui.SellerViewModel
import com.example.ui.StockFilter
import com.example.ui.components.DeliveryConfirmationDialog
import com.example.ui.components.LowStockThresholdDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.QrCodeScannerSheet
import com.example.ui.components.QrScannerMode
import com.example.ui.components.QuickStockStepper
import com.example.ui.components.SellerInventoryChartCard
import com.example.ui.components.SetStockQuantityDialog
import java.util.Locale

@Composable
fun StockDashboardScreen(
    viewModel: SellerViewModel,
    onOpenSellerInventory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val storeConfigState by viewModel.storeConfigState.collectAsStateWithLifecycle()
    val isSellerEditMode by viewModel.isSellerEditMode.collectAsStateWithLifecycle()
    val stockFilter by viewModel.stockFilter.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()

    var showFeedbackSheet by remember { mutableStateOf(false) }
    var exactQtyTarget by remember { mutableStateOf<Product?>(null) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    var prefilledProductName by remember { mutableStateOf("") }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var deliveryConfirmTarget by remember { mutableStateOf<SellerOrder?>(null) }
    var alertSoundEnabled by remember(viewModel.sellerId) {
        mutableStateOf(LowStockNotifier.isAlertSoundEnabled(context, viewModel.sellerId))
    }

    var hasNotificationPermission by remember {
        mutableStateOf(LowStockNotifier.hasNotificationPermission(context))
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            viewModel.showMessage("Push notifications enabled for low-stock alerts.")
        }
    }

    val storeConfig = (storeConfigState as? UiState.Success)?.data
    val storeName = storeConfig?.storeName ?: "Lakshmi"
    val showOutOfStock = storeConfig?.showOutOfStock ?: true
    val lowStockThreshold = (storeConfig?.lowStockThreshold ?: 5L).coerceIn(1L, 1000L)
    val lowStockAlertsEnabled = storeConfig?.lowStockAlertsEnabled ?: true
    val allOrders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val feedbackList = (feedbackState as? UiState.Success)?.data ?: emptyList()
    val avgRating = if (feedbackList.isNotEmpty()) {
        feedbackList.map { it.rating.toDouble() }.average()
    } else 0.0

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (val state = productsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            is UiState.Success -> {
                val allProducts = state.data
                val allLowStockItems = remember(allProducts, lowStockThreshold) {
                    allProducts.filter { it.isLowStock(lowStockThreshold) }
                        .sortedWith(compareBy<Product> { it.stockQty }.thenBy { it.name.lowercase() })
                }

                val filteredByCategoryAndSearch = remember(allProducts, selectedCategory, searchQuery) {
                    allProducts.filter { p ->
                        val matchesCat = selectedCategory == null || p.categoryEnum() == selectedCategory
                        val matchesQuery = searchQuery.isBlank() ||
                            p.name.contains(searchQuery, ignoreCase = true) ||
                            p.description.contains(searchQuery, ignoreCase = true)
                        matchesCat && matchesQuery
                    }
                }

                val presentItems = remember(filteredByCategoryAndSearch) {
                    filteredByCategoryAndSearch.filter { it.inStock }
                }
                val outOfStockItems = remember(filteredByCategoryAndSearch) {
                    filteredByCategoryAndSearch.filter { !it.inStock }
                }
                val filteredLowStockItems = remember(filteredByCategoryAndSearch, lowStockThreshold) {
                    filteredByCategoryAndSearch.filter { it.isLowStock(lowStockThreshold) }
                        .sortedWith(compareBy<Product> { it.stockQty }.thenBy { it.name.lowercase() })
                }

                val showLowOnlySection = stockFilter == StockFilter.LOW_STOCK_ONLY
                val showPresentSection = stockFilter == StockFilter.ALL || stockFilter == StockFilter.PRESENT_ONLY
                val showOutSection = showOutOfStock && (stockFilter == StockFilter.ALL || stockFilter == StockFilter.OUT_OF_STOCK_ONLY)

                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header Banner Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = storeName,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = if (isSellerEditMode) {
                                                "Quick +/- Inventory Mode Active"
                                            } else {
                                                "Buyer-Ready Live Stock Visibility"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier
                                            .clickable { showFeedbackSheet = true }
                                            .testTag("open_feedback_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = "Store rating",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (feedbackList.isEmpty()) {
                                                    "Reviews"
                                                } else {
                                                    String.format(Locale.US, "%.1f (%d)", avgRating, feedbackList.size)
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                if (searchQuery.isBlank() && allProducts.isNotEmpty()) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_puja_banner),
                                        contentDescription = "Fresh Groceries and Puja Samagri",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isTtsSpeaking) viewModel.stopSpeaking() else viewModel.speakStockSummary()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("speak_stock_summary_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isTtsSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Listen stock summary",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isTtsSpeaking) "Stop" else "Listen",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { showQrScanner = true },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("dashboard_scan_qr_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QrCodeScanner,
                                            contentDescription = "Scan item or delivery QR code",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Scan QR", maxLines = 1)
                                    }

                                    FilterChip(
                                        selected = isSellerEditMode,
                                        onClick = { viewModel.setSellerEditMode(!isSellerEditMode) },
                                        label = {
                                            Text(
                                                text = if (isSellerEditMode) "+/- ON" else "Stock +/-",
                                                maxLines = 1
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSellerEditMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("toggle_seller_edit_mode")
                                    )
                                }
                            }
                        }
                    }

                    // Automated Low-Stock Alert Highlight Card
                    if (allProducts.isNotEmpty()) {
                        item {
                            LowStockAlertHighlightBanner(
                                lowStockItems = allLowStockItems,
                                threshold = lowStockThreshold,
                                alertsEnabled = lowStockAlertsEnabled,
                                alertSoundEnabled = alertSoundEnabled,
                                isLowFilterActive = stockFilter == StockFilter.LOW_STOCK_ONLY,
                                hasNotificationPermission = hasNotificationPermission,
                                onToggleLowFilter = {
                                    viewModel.setStockFilter(
                                        if (stockFilter == StockFilter.LOW_STOCK_ONLY) {
                                            StockFilter.ALL
                                        } else {
                                            StockFilter.LOW_STOCK_ONLY
                                        }
                                    )
                                },
                                onToggleAlertSound = {
                                    val next = !alertSoundEnabled
                                    alertSoundEnabled = next
                                    LowStockNotifier.setAlertSoundEnabled(context, viewModel.sellerId, next)
                                    if (next) {
                                        LowStockNotifier.playSubtleAlertChime()
                                    }
                                    viewModel.showMessage(
                                        if (next) "Subtle low-stock alert sound ON." else "Low-stock alert sound muted."
                                    )
                                },
                                onOpenThresholdDialog = { showThresholdDialog = true },
                                onQuickRestockItem = { product -> viewModel.adjustStockQty(product, 5L) },
                                onRequestPushPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onSendPushAlertNow = {
                                    val posted = LowStockNotifier.showLowStockAlert(
                                        context = context,
                                        storeName = storeName,
                                        lowStockItems = allLowStockItems,
                                        threshold = lowStockThreshold,
                                        playChime = alertSoundEnabled
                                    )
                                    if (posted) {
                                        viewModel.showMessage("Low-stock alert notification sent.")
                                    } else if (alertSoundEnabled) {
                                        viewModel.showMessage("Played subtle low-stock alert chime.")
                                    } else {
                                        viewModel.showMessage("Grant notification permission to receive push alerts.")
                                    }
                                }
                            )
                        }
                    }

                    // Visual Dashboard Chart: Most Frequently Purchased & Nearing Out-of-Stock Threshold
                    if (allProducts.isNotEmpty() || allOrders.isNotEmpty()) {
                        item(key = "seller_analytics_chart") {
                            SellerInventoryChartCard(
                                products = allProducts,
                                orders = allOrders,
                                lowStockThreshold = lowStockThreshold,
                                onQuickRestock = { product -> viewModel.adjustStockQty(product, 5L) }
                            )
                        }
                    }

                    // Dual Visibility Summary Cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            VisibilityCounterCard(
                                title = "Present Stock",
                                count = presentItems.size,
                                subtitle = "Visible & ready",
                                isSelected = stockFilter == StockFilter.PRESENT_ONLY,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                icon = Icons.Filled.CheckCircle,
                                onClick = {
                                    viewModel.setStockFilter(
                                        if (stockFilter == StockFilter.PRESENT_ONLY) StockFilter.ALL else StockFilter.PRESENT_ONLY
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("present_stock_card")
                            )

                            VisibilityCounterCard(
                                title = "Out of Stock",
                                count = outOfStockItems.size,
                                subtitle = if (showOutOfStock) "Visible to buyers" else "Hidden when 0 stock",
                                isSelected = stockFilter == StockFilter.OUT_OF_STOCK_ONLY,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                icon = Icons.Filled.RemoveCircleOutline,
                                onClick = {
                                    if (!showOutOfStock) {
                                        viewModel.setShowOutOfStock(true)
                                    }
                                    viewModel.setStockFilter(
                                        if (stockFilter == StockFilter.OUT_OF_STOCK_ONLY) StockFilter.ALL else StockFilter.OUT_OF_STOCK_ONLY
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("out_of_stock_card")
                            )
                        }
                    }

                    // Out-of-stock visibility switch
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (showOutOfStock) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Show Out-of-Stock Items",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = if (showOutOfStock) {
                                                "No-stock items are visible on Buyer & Seller boards"
                                            } else {
                                                "No-stock items hidden from buyers (OTP-protected)"
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = showOutOfStock,
                                    onCheckedChange = { viewModel.setShowOutOfStock(it) },
                                    modifier = Modifier.testTag("show_out_of_stock_switch")
                                )
                            }
                        }
                    }

                    // Search & Category Filters
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                label = { Text("Search puja, fancy or grocery items") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("stock_search_input")
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedCategory == null && stockFilter != StockFilter.LOW_STOCK_ONLY,
                                        onClick = {
                                            viewModel.setSelectedCategory(null)
                                            if (stockFilter == StockFilter.LOW_STOCK_ONLY) {
                                                viewModel.setStockFilter(StockFilter.ALL)
                                            }
                                        },
                                        label = { Text("All (${allProducts.size})") }
                                    )
                                }
                                if (allLowStockItems.isNotEmpty()) {
                                    item {
                                        FilterChip(
                                            selected = stockFilter == StockFilter.LOW_STOCK_ONLY,
                                            onClick = {
                                                viewModel.setStockFilter(
                                                    if (stockFilter == StockFilter.LOW_STOCK_ONLY) {
                                                        StockFilter.ALL
                                                    } else {
                                                        StockFilter.LOW_STOCK_ONLY
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Warning,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text("Low ≤ $lowStockThreshold (${allLowStockItems.size})") },
                                            modifier = Modifier.testTag("filter_low_stock_chip")
                                        )
                                    }
                                }
                                items(ProductCategory.entries) { category ->
                                    val count = allProducts.count { it.categoryEnum() == category }
                                    FilterChip(
                                        selected = selectedCategory == category,
                                        onClick = {
                                            viewModel.setSelectedCategory(
                                                if (selectedCategory == category) null else category
                                            )
                                        },
                                        label = { Text("${category.displayName} ($count)") }
                                    )
                                }
                            }
                        }
                    }

                    // Empty catalog state
                    if (allProducts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Inventory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "Your Store Stock Board is Empty",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = "Load our curated grocery & puja starter catalog (including present & out-of-stock items) or add your own products.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Button(
                                            onClick = { viewModel.seedStarterCatalog() },
                                            modifier = Modifier.testTag("load_starter_catalog_button")
                                        ) {
                                            Text("Load Starter Catalog")
                                        }
                                        OutlinedButton(onClick = { showAddProductDialog = true }) {
                                            Text("Add Item")
                                        }
                                    }
                                }
                            }
                        }
                    } else if (showLowOnlySection) {
                        item {
                            SectionHeader(
                                title = "Low Inventory Alerts (${filteredLowStockItems.size})",
                                badgeText = "≤ $lowStockThreshold UNITS",
                                isPositive = false
                            )
                        }
                        if (filteredLowStockItems.isEmpty()) {
                            item {
                                EmptySectionCard("Great news! All items in this view are stocked above $lowStockThreshold units.")
                            }
                        } else {
                            items(filteredLowStockItems, key = { "low_${it.id}" }) { product ->
                                StockVisibilityItemCard(
                                    product = product,
                                    isSellerEditMode = isSellerEditMode,
                                    lowStockThreshold = lowStockThreshold,
                                    lowStockAlertsEnabled = lowStockAlertsEnabled,
                                    onToggleStock = { viewModel.toggleProductStock(product, !product.inStock) },
                                    onAdjustQty = { delta -> viewModel.adjustStockQty(product, delta) },
                                    onOpenExactQty = { exactQtyTarget = product },
                                    onSpeakItem = {
                                        viewModel.speakText(
                                            "${product.name}, ${product.categoryEnum().displayName}, " +
                                                "low stock alert with ${product.stockQty} units remaining, below your threshold of $lowStockThreshold."
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        if (showPresentSection) {
                            item {
                                SectionHeader(
                                    title = "Present Stock (${presentItems.size})",
                                    badgeText = "AVAILABLE NOW",
                                    isPositive = true
                                )
                            }
                            if (presentItems.isEmpty()) {
                                item {
                                    EmptySectionCard("No matching items currently in stock.")
                                }
                            } else {
                                items(presentItems, key = { "present_${it.id}" }) { product ->
                                    StockVisibilityItemCard(
                                        product = product,
                                        isSellerEditMode = isSellerEditMode,
                                        lowStockThreshold = lowStockThreshold,
                                        lowStockAlertsEnabled = lowStockAlertsEnabled,
                                        onToggleStock = { viewModel.toggleProductStock(product, !product.inStock) },
                                        onAdjustQty = { delta -> viewModel.adjustStockQty(product, delta) },
                                        onOpenExactQty = { exactQtyTarget = product },
                                        onSpeakItem = {
                                            viewModel.speakText(
                                                "${product.name}, ${product.categoryEnum().displayName}, " +
                                                    "currently in stock with ${product.stockQty} units, " +
                                                    "priced at ${product.priceInr.toInt()} rupees per ${product.unit}. ${product.description}"
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        if (showOutSection) {
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                SectionHeader(
                                    title = "Out of Stock (${outOfStockItems.size})",
                                    badgeText = "CURRENTLY UNAVAILABLE",
                                    isPositive = false
                                )
                            }
                            if (outOfStockItems.isEmpty()) {
                                item {
                                    EmptySectionCard("Everything in this view is currently in stock!")
                                }
                            } else {
                                items(outOfStockItems, key = { "out_${it.id}" }) { product ->
                                    StockVisibilityItemCard(
                                        product = product,
                                        isSellerEditMode = isSellerEditMode,
                                        lowStockThreshold = lowStockThreshold,
                                        lowStockAlertsEnabled = lowStockAlertsEnabled,
                                        onToggleStock = { viewModel.toggleProductStock(product, true) },
                                        onAdjustQty = { delta -> viewModel.adjustStockQty(product, delta) },
                                        onOpenExactQty = { exactQtyTarget = product },
                                        onSpeakItem = {
                                            viewModel.speakText(
                                                "${product.name} is currently out of stock. Last price was ${product.priceInr.toInt()} rupees per ${product.unit}."
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Footer shortcut to Seller Inventory & Reviews
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Store Feedback & Reviews",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "View ratings or manage full catalog & OTP safety settings in Seller Hub.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { showFeedbackSheet = true },
                                    modifier = Modifier.testTag("leave_feedback_footer_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.RateReview,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Reviews")
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddProductDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("dashboard_add_item_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add new inventory item")
        }
    }

    exactQtyTarget?.let { target ->
        SetStockQuantityDialog(
            product = target,
            onDismiss = { exactQtyTarget = null },
            onConfirmQty = { newQty ->
                viewModel.setExactStockQty(target, newQty)
                exactQtyTarget = null
            }
        )
    }

    if (showAddProductDialog) {
        ProductEditorDialog(
            initialProduct = null,
            viewModel = viewModel,
            prefilledName = prefilledProductName,
            onDismiss = {
                viewModel.clearSmartFill()
                prefilledProductName = ""
                showAddProductDialog = false
            },
            onSave = { name, category, unit, price, qty, inStock, desc, photoUri, imageTag ->
                viewModel.saveProduct(
                    editingId = null,
                    name = name,
                    category = category,
                    unit = unit,
                    priceInr = price,
                    stockQty = qty,
                    inStock = inStock,
                    description = desc,
                    photoUri = photoUri,
                    imageTag = imageTag
                )
                viewModel.clearSmartFill()
                prefilledProductName = ""
                showAddProductDialog = false
            }
        )
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

    if (showThresholdDialog) {
        val currentAll = (productsState as? UiState.Success)?.data ?: emptyList()
        LowStockThresholdDialog(
            currentThreshold = lowStockThreshold,
            currentAlertsEnabled = lowStockAlertsEnabled,
            allProducts = currentAll,
            onDismiss = { showThresholdDialog = false },
            onSave = { newThreshold, enabled ->
                viewModel.updateLowStockSettings(newThreshold, enabled)
                showThresholdDialog = false
            }
        )
    }

    if (showQrScanner) {
        val currentAll = (productsState as? UiState.Success)?.data ?: emptyList()
        QrCodeScannerSheet(
            initialMode = QrScannerMode.AUTO,
            products = currentAll,
            orders = allOrders,
            onDismiss = { showQrScanner = false },
            onUpdateProductQty = { product, newQty ->
                viewModel.setExactStockQty(product, newQty)
            },
            onToggleProductStock = { product, inStock ->
                viewModel.toggleProductStock(product, inStock)
            },
            onConfirmDeliveryViaQr = { order, _ ->
                viewModel.confirmOrderDelivery(order, "QR", "")
            },
            onOpenOtpOrPhotoDialog = { order ->
                deliveryConfirmTarget = order
            },
            onCreateItemFromQr = { scannedName ->
                prefilledProductName = scannedName.take(60)
                showAddProductDialog = true
            }
        )
    }

    deliveryConfirmTarget?.let { targetOrder ->
        DeliveryConfirmationDialog(
            order = targetOrder,
            sellerUid = viewModel.sellerId,
            onDismiss = { deliveryConfirmTarget = null },
            onConfirmed = { method, proofUri ->
                viewModel.confirmOrderDelivery(targetOrder, method, proofUri)
                deliveryConfirmTarget = null
            },
            onScanQrClick = {
                deliveryConfirmTarget = null
                showQrScanner = true
            }
        )
    }
}

@Composable
private fun LowStockAlertHighlightBanner(
    lowStockItems: List<Product>,
    threshold: Long,
    alertsEnabled: Boolean,
    alertSoundEnabled: Boolean,
    isLowFilterActive: Boolean,
    hasNotificationPermission: Boolean,
    onToggleLowFilter: () -> Unit,
    onToggleAlertSound: () -> Unit,
    onOpenThresholdDialog: () -> Unit,
    onQuickRestockItem: (Product) -> Unit,
    onRequestPushPermission: () -> Unit,
    onSendPushAlertNow: () -> Unit
) {
    val pujaLowCount = remember(lowStockItems) {
        lowStockItems.count { it.categoryEnum() == ProductCategory.PUJA }
    }
    val fancyLowCount = remember(lowStockItems) {
        lowStockItems.count { it.categoryEnum() == ProductCategory.FANCY }
    }
    val groceryLowCount = remember(lowStockItems) {
        lowStockItems.count { it.categoryEnum() == ProductCategory.GROCERY }
    }

    if (!alertsEnabled || lowStockItems.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (!alertsEnabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("low_stock_healthy_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (alertsEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        tint = if (alertsEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = if (alertsEnabled) {
                                "All Inventory Healthy (> $threshold units)"
                            } else {
                                "Low-Stock Push Alerts Paused"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Alert threshold: ≤ $threshold units",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onOpenThresholdDialog,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("configure_threshold_button")
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("≤ $threshold")
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("low_stock_alert_banner"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = "Low inventory alert",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Low Inventory Alert • ${lowStockItems.size} item${if (lowStockItems.size == 1) "" else "s"} ≤ $threshold",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = listOfNotNull(
                                if (groceryLowCount > 0) "$groceryLowCount Groceries" else null,
                                if (pujaLowCount > 0) "$pujaLowCount Puja Needs" else null,
                                if (fancyLowCount > 0) "$fancyLowCount Fancy Items" else null
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onToggleAlertSound,
                        modifier = Modifier.testTag("toggle_low_stock_sound_button")
                    ) {
                        Icon(
                            imageVector = if (alertSoundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (alertSoundEnabled) "Mute alert chime" else "Enable alert chime",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenThresholdDialog,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("configure_threshold_button")
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("≤ $threshold")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lowStockItems.take(3).forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.categoryEnum().displayName} • " + if (!item.inStock || item.stockQty <= 0L) {
                                        "OUT OF STOCK"
                                    } else {
                                        "${item.stockQty} ${item.unit} left"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            TextButton(
                                onClick = { onQuickRestockItem(item) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("quick_restock_${item.id}")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("+5 Stock")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onToggleLowFilter,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLowFilterActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp)
                        .testTag("highlight_filter_low_stock_button")
                ) {
                    Text(if (isLowFilterActive) "Show All Items" else "View All ${lowStockItems.size} Low")
                }

                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(
                        onClick = onRequestPushPermission,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("enable_push_alerts_button")
                    ) {
                        Text("Enable Push")
                    }
                } else {
                    OutlinedButton(
                        onClick = onSendPushAlertNow,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("send_push_now_button")
                    ) {
                        Text("Push Alert")
                    }
                }
            }
        }
    }
}

@Composable
private fun VisibilityCounterCard(
    title: String,
    count: Int,
    subtitle: String,
    isSelected: Boolean,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = if (isSelected) BorderStroke(2.dp, contentColor) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    badgeText: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isPositive) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EmptySectionCard(message: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun StockVisibilityItemCard(
    product: Product,
    isSellerEditMode: Boolean,
    lowStockThreshold: Long,
    lowStockAlertsEnabled: Boolean,
    onToggleStock: () -> Unit,
    onAdjustQty: (Long) -> Unit,
    onOpenExactQty: () -> Unit,
    onSpeakItem: () -> Unit
) {
    val isPresentLow = lowStockAlertsEnabled && product.isPresentButLow(lowStockThreshold)
    val isAnyLow = lowStockAlertsEnabled && product.isLowStock(lowStockThreshold)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !product.inStock -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                isPresentLow -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isAnyLow) {
            BorderStroke(1.25.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isPresentLow) 0.65f else 0.35f))
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (product.inStock) 1.5.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProductThumbnail(
                    product = product,
                    modifier = Modifier.size(84.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onSpeakItem,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak ${product.name} availability",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (product.categoryEnum()) {
                                ProductCategory.PUJA -> MaterialTheme.colorScheme.secondaryContainer
                                ProductCategory.FANCY -> MaterialTheme.colorScheme.tertiaryContainer
                                ProductCategory.GROCERY -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = product.categoryEnum().displayName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Text(
                            text = "₹${product.priceInr.toInt()} / ${product.unit}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (product.description.isNotBlank()) {
                        Text(
                            text = product.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = when {
                                !product.inStock -> MaterialTheme.colorScheme.errorContainer
                                isPresentLow -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isAnyLow) {
                                    Icon(
                                        imageVector = Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        !product.inStock -> "OUT OF STOCK"
                                        isPresentLow -> "LOW STOCK • ${product.stockQty} left (≤ $lowStockThreshold)"
                                        else -> "IN STOCK (${product.stockQty} left)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!product.inStock || isPresentLow) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }

                        if (isSellerEditMode) {
                            TextButton(onClick = onToggleStock) {
                                Text(if (product.inStock) "Mark Out" else "Restock")
                            }
                        }
                    }
                }
            }

            if (isSellerEditMode) {
                QuickStockStepper(
                    product = product,
                    onAdjustQty = onAdjustQty,
                    onOpenExactQtyDialog = onOpenExactQty
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackBottomSheet(
    recentFeedback: List<StoreFeedback>,
    onDismiss: () -> Unit,
    onSubmit: (authorName: String, rating: Int, category: FeedbackCategory, comment: String) -> Unit
) {
    var authorName by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(5) }
    var selectedCategory by remember { mutableStateOf(FeedbackCategory.GENERAL) }
    var comment by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Rate & Share Feedback",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..5).forEach { star ->
                    IconButton(
                        onClick = { rating = star },
                        modifier = Modifier.testTag("rating_star_$star")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "$star stars",
                            tint = if (star <= rating) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = "$rating / 5",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FeedbackCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) }
                    )
                }
            }

            OutlinedTextField(
                value = authorName,
                onValueChange = { authorName = it },
                label = { Text("Your Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("feedback_name_input")
            )

            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Comments on stock visibility, groceries, fancy or puja items") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("feedback_comment_input")
            )

            Button(
                onClick = { onSubmit(authorName, rating, selectedCategory, comment) },
                enabled = comment.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_feedback_button")
            ) {
                Text("Submit Rating & Feedback")
            }

            if (recentFeedback.isNotEmpty()) {
                Text(
                    text = "Recent Reviews",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recentFeedback.take(2).forEach { fb ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${fb.authorName} • ${"★".repeat(fb.rating.toInt().coerceIn(1, 5))}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = fb.comment,
                                style = MaterialTheme.typography.bodyMedium,
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
