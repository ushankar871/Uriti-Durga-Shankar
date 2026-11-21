package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.SellerOrder
import com.example.model.StoreFeedback
import com.example.model.UiState
import com.example.ui.SellerViewModel
import com.example.ui.components.DeliveryConfirmationDialog
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.QrCodeScannerSheet
import com.example.ui.components.QrScannerMode
import com.example.ui.components.QuickStockStepper
import com.example.ui.components.SetStockQuantityDialog
import java.util.Locale

private enum class HubSection(val label: String) {
    INVENTORY("Inventory +/-"),
    ORDERS("Orders"),
    BACKUP("Safety & Backup"),
    FEEDBACK("Reviews"),
    INSIGHTS("AI Insights")
}

@Composable
fun SellerHubScreen(
    viewModel: SellerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val storeConfigState by viewModel.storeConfigState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val insightsState by viewModel.insightsState.collectAsStateWithLifecycle()
    val broadcastingOrderId by viewModel.broadcastingOrderId.collectAsStateWithLifecycle()

    var pendingGpsOrder by remember { mutableStateOf<SellerOrder?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.any { it }
        val target = pendingGpsOrder
        pendingGpsOrder = null
        if (granted && target != null) {
            viewModel.startLiveLocationBroadcast(context, target)
        } else if (!granted) {
            viewModel.showMessage("Location permission is needed to broadcast live GPS delivery updates.")
        }
    }

    var activeSection by remember { mutableStateOf(HubSection.INVENTORY) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var prefilledProductName by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var exactQtyTarget by remember { mutableStateOf<Product?>(null) }
    var deliveryConfirmTarget by remember { mutableStateOf<SellerOrder?>(null) }
    var activeQrScannerMode by remember { mutableStateOf<QrScannerMode?>(null) }
    var showFeedbackSheet by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportBackupToUri(context, it) }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.restoreBackupFromUri(context, it) }
    }

    val products = (productsState as? UiState.Success)?.data ?: emptyList()
    val orders = (ordersState as? UiState.Success)?.data ?: emptyList()
    val feedbackList = (feedbackState as? UiState.Success)?.data ?: emptyList()
    val auditLogs = (auditLogsState as? UiState.Success)?.data ?: emptyList()
    val storeConfig = (storeConfigState as? UiState.Success)?.data
    val deleteProtectionEnabled = storeConfig?.deleteProtectionEnabled ?: true
    val otpProtectionEnabled = storeConfig?.otpProtectionEnabled ?: true
    val lowStockThreshold = (storeConfig?.lowStockThreshold ?: 5L).coerceIn(1L, 1000L)
    val lowStockAlertsEnabled = storeConfig?.lowStockAlertsEnabled ?: true

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(HubSection.entries) { section ->
                    FilterChip(
                        selected = activeSection == section,
                        onClick = { activeSection = section },
                        label = { Text(section.label) },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("hub_tab_${section.name.lowercase()}")
                    )
                }
            }

            when (activeSection) {
                HubSection.INVENTORY -> {
                    InventoryManagerPane(
                        productsState = productsState,
                        deleteProtectionEnabled = deleteProtectionEnabled,
                        lowStockThreshold = lowStockThreshold,
                        lowStockAlertsEnabled = lowStockAlertsEnabled,
                        onAddProduct = {
                            editingProduct = null
                            prefilledProductName = ""
                            showEditorDialog = true
                        },
                        onScanQr = { activeQrScannerMode = QrScannerMode.INVENTORY_UPDATE },
                        onEditProduct = { product ->
                            editingProduct = product
                            showEditorDialog = true
                        },
                        onToggleStock = { product ->
                            viewModel.toggleProductStock(product, !product.inStock)
                        },
                        onAdjustQty = { product, delta ->
                            viewModel.adjustStockQty(product, delta)
                        },
                        onOpenExactQty = { product ->
                            exactQtyTarget = product
                        },
                        onDeleteProduct = { product ->
                            viewModel.deleteProduct(product)
                        },
                        onSeedStarter = { viewModel.seedStarterCatalog() }
                    )
                }

                HubSection.ORDERS -> {
                    OrdersPane(
                        ordersState = ordersState,
                        broadcastingOrderId = broadcastingOrderId,
                        onCreateSampleOrder = { viewModel.createSampleOrder() },
                        onAdvanceStatus = { order -> viewModel.advanceOrderStatus(order) },
                        onToggleGpsBroadcast = { order ->
                            if (broadcastingOrderId == order.id) {
                                viewModel.stopLiveLocationBroadcast(notifyUser = true)
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    viewModel.startLiveLocationBroadcast(context, order)
                                } else {
                                    pendingGpsOrder = order
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        },
                        onStepRiderLocation = { order -> viewModel.moveRiderStepCloser(order) },
                        onConfirmDelivery = { order -> deliveryConfirmTarget = order },
                        onScanDeliveryQr = { activeQrScannerMode = QrScannerMode.DELIVERY_CONFIRM }
                    )
                }

                HubSection.BACKUP -> {
                    SellerSecurityAndBackupPane(
                        sellerId = viewModel.sellerId,
                        productCount = products.size,
                        orderCount = orders.size,
                        feedbackCount = feedbackList.size,
                        deleteProtectionEnabled = deleteProtectionEnabled,
                        otpProtectionEnabled = otpProtectionEnabled,
                        auditLogs = auditLogs,
                        onToggleDeleteProtection = { enabled ->
                            viewModel.updateSecuritySettings(
                                deleteProtectionEnabled = enabled,
                                otpProtectionEnabled = otpProtectionEnabled
                            )
                        },
                        onToggleOtpProtection = { enabled ->
                            viewModel.updateSecuritySettings(
                                deleteProtectionEnabled = deleteProtectionEnabled,
                                otpProtectionEnabled = enabled
                            )
                        },
                        onExportBackup = {
                            exportBackupLauncher.launch("lakshmi-backup-${System.currentTimeMillis()}.json")
                        },
                        onRestoreBackup = {
                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        onShareBackup = {
                            val json = viewModel.buildBackupJson()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Lakshmi Catalog & Store Backup")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Lakshmi Backup"))
                        },
                        onDeleteAllStoreData = {
                            viewModel.deleteAllSellerData()
                        },
                        onShowMessage = { msg -> viewModel.showMessage(msg) }
                    )
                }

                HubSection.FEEDBACK -> {
                    FeedbackPane(
                        feedbackList = feedbackList,
                        deleteProtectionEnabled = deleteProtectionEnabled,
                        onAddFeedback = { showFeedbackSheet = true },
                        onDeleteFeedback = { id -> viewModel.deleteFeedback(id) },
                        onSpeakSummary = {
                            val avg = if (feedbackList.isEmpty()) 0.0 else feedbackList.map { it.rating }.average()
                            val latest = feedbackList.firstOrNull()?.comment.orEmpty()
                            viewModel.speakText(
                                "You have ${feedbackList.size} customer reviews with an average rating of ${
                                    String.format(Locale.US, "%.1f", avg)
                                } out of 5 stars. Latest comment: $latest"
                            )
                        }
                    )
                }

                HubSection.INSIGHTS -> {
                    AiInsightsPane(
                        insightsState = insightsState,
                        onGenerateInsights = { viewModel.generateRestockInsights() },
                        onSpeakInsights = { text -> viewModel.speakText(text) }
                    )
                }
            }
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

    if (showEditorDialog) {
        ProductEditorDialog(
            initialProduct = editingProduct,
            viewModel = viewModel,
            prefilledName = prefilledProductName,
            onDismiss = {
                viewModel.clearSmartFill()
                prefilledProductName = ""
                showEditorDialog = false
            },
            onSave = { name, category, unit, price, qty, inStock, desc, photoUri, imageTag ->
                viewModel.saveProduct(
                    editingId = editingProduct?.id,
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
                showEditorDialog = false
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
                activeQrScannerMode = QrScannerMode.DELIVERY_CONFIRM
            }
        )
    }

    activeQrScannerMode?.let { scannerMode ->
        QrCodeScannerSheet(
            initialMode = scannerMode,
            products = products,
            orders = orders,
            onDismiss = { activeQrScannerMode = null },
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
                editingProduct = null
                prefilledProductName = scannedName.take(60)
                showEditorDialog = true
            }
        )
    }
}

@Composable
private fun InventoryManagerPane(
    productsState: UiState<List<Product>>,
    deleteProtectionEnabled: Boolean,
    lowStockThreshold: Long,
    lowStockAlertsEnabled: Boolean,
    onAddProduct: () -> Unit,
    onScanQr: () -> Unit,
    onEditProduct: (Product) -> Unit,
    onToggleStock: (Product) -> Unit,
    onAdjustQty: (Product, Long) -> Unit,
    onOpenExactQty: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onSeedStarter: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inventory & Quick +/-",
                    style = MaterialTheme.typography.titleLarge
                )
                if (lowStockAlertsEnabled) {
                    Text(
                        text = "Low-stock highlight active at ≤ $lowStockThreshold units",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("inventory_scan_qr_button")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan inventory QR", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("QR")
                }
                Button(
                    onClick = onAddProduct,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_product_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        when (productsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(
                    text = productsState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            is UiState.Success -> {
                val items = productsState.data
                if (items.isEmpty()) {
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
                                imageVector = Icons.Filled.Inventory2,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text("No products added yet.", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = onSeedStarter) {
                                Text("Load Starter Puja, Fancy & Grocery Catalog")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(items, key = { it.id }) { product ->
                            val isLow = lowStockAlertsEnabled && product.isLowStock(lowStockThreshold)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isLow) {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
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
                                            modifier = Modifier.size(72.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = product.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${product.categoryEnum().displayName} • ₹${product.priceInr.toInt()} / ${product.unit}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = when {
                                                    !product.inStock || product.stockQty <= 0L -> "Out of Stock"
                                                    isLow -> "LOW STOCK • ${product.stockQty} units (≤ $lowStockThreshold)"
                                                    else -> "Present (${product.stockQty} units)"
                                                },
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isLow) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    !product.inStock || product.stockQty <= 0L -> MaterialTheme.colorScheme.error
                                                    isLow -> MaterialTheme.colorScheme.error
                                                    else -> MaterialTheme.colorScheme.tertiary
                                                }
                                            )
                                        }
                                        Switch(
                                            checked = product.inStock,
                                            onCheckedChange = { onToggleStock(product) }
                                        )
                                        IconButton(
                                            onClick = { onEditProduct(product) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${product.name}")
                                        }
                                        IconButton(
                                            onClick = { onDeleteProduct(product) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (deleteProtectionEnabled) Icons.Filled.Lock else Icons.Filled.Delete,
                                                contentDescription = if (deleteProtectionEnabled) "Locked: Delete protection active" else "Delete ${product.name}",
                                                tint = if (deleteProtectionEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    QuickStockStepper(
                                        product = product,
                                        onAdjustQty = { delta -> onAdjustQty(product, delta) },
                                        onOpenExactQtyDialog = { onOpenExactQty(product) }
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
private fun OrdersPane(
    ordersState: UiState<List<SellerOrder>>,
    broadcastingOrderId: String?,
    onCreateSampleOrder: () -> Unit,
    onAdvanceStatus: (SellerOrder) -> Unit,
    onToggleGpsBroadcast: (SellerOrder) -> Unit,
    onStepRiderLocation: (SellerOrder) -> Unit,
    onConfirmDelivery: (SellerOrder) -> Unit,
    onScanDeliveryQr: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Customer Orders",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onScanDeliveryQr,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("orders_scan_qr_button")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan delivery QR", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan QR", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCreateSampleOrder,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("add_sample_order_button")
                ) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Order", maxLines = 1)
                }
            }
        }

        when (ordersState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(ordersState.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            is UiState.Success -> {
                val orders = ordersState.data
                if (orders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No orders yet. Orders placed from Buyer accounts or 'Log Test Order' appear here in real time.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(orders, key = { it.id }) { order ->
                            val status = order.statusEnum()
                            val next = status.next()
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(order.customerName, style = MaterialTheme.typography.titleMedium)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (status == OrderStatus.DELIVERED) {
                                                MaterialTheme.colorScheme.tertiaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = status.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${order.neighborhood} • ₹${order.totalInr.toInt()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = order.itemsSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (status == OrderStatus.DELIVERED && order.deliveryMethod.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (order.deliveryMethod == "PHOTO") {
                                                            Icons.Filled.PhotoCamera
                                                        } else {
                                                            Icons.Filled.VerifiedUser
                                                        },
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
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                                    )
                                                }
                                                if (order.deliveryProofUri.isNotBlank()) {
                                                    AsyncImage(
                                                        model = order.deliveryProofUri,
                                                        contentDescription = "Delivery confirmation photo",
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

                                    if (next != null) {
                                        val isBroadcasting = broadcastingOrderId == order.id
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { onToggleGpsBroadcast(order) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .defaultMinSize(minHeight = 44.dp)
                                                        .testTag("toggle_gps_broadcast_btn_${order.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isBroadcasting) Icons.Filled.GpsOff else Icons.Filled.GpsFixed,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isBroadcasting) "Stop GPS" else "Live GPS",
                                                        maxLines = 1
                                                    )
                                                }
                                                OutlinedButton(
                                                    onClick = { onStepRiderLocation(order) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .defaultMinSize(minHeight = 44.dp)
                                                        .testTag("step_rider_gps_btn_${order.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Navigation,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Move Pin +35%", maxLines = 1)
                                                }
                                            }
                                            if (next == OrderStatus.DELIVERED) {
                                                Button(
                                                    onClick = { onConfirmDelivery(order) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 48.dp)
                                                        .testTag("confirm_delivery_btn_${order.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Filled.VerifiedUser,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Confirm Delivery (OTP / Photo)")
                                                }
                                            } else {
                                                Button(
                                                    onClick = { onAdvanceStatus(order) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 48.dp)
                                                ) {
                                                    Text("Mark as ${next.label}")
                                                }
                                                OutlinedButton(
                                                    onClick = { onConfirmDelivery(order) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .defaultMinSize(minHeight = 48.dp)
                                                        .testTag("confirm_delivery_btn_${order.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PhotoCamera,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Confirm Delivery (OTP / Photo)")
                                                }
                                            }
                                        }
                                    }
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
private fun FeedbackPane(
    feedbackList: List<StoreFeedback>,
    deleteProtectionEnabled: Boolean,
    onAddFeedback: () -> Unit,
    onDeleteFeedback: (String) -> Unit,
    onSpeakSummary: () -> Unit
) {
    val avgRating = if (feedbackList.isEmpty()) 0.0 else feedbackList.map { it.rating.toDouble() }.average()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (feedbackList.isEmpty()) "No Ratings Yet" else String.format(Locale.US, "%.1f ★", avgRating),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${feedbackList.size} buyer reviews",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onSpeakSummary) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Listen")
                            }
                            Button(onClick = onAddFeedback) {
                                Icon(Icons.Filled.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rate")
                            }
                        }
                    }
                }
            }
        }

        if (feedbackList.isEmpty()) {
            item {
                Text(
                    text = "Collect customer ratings & reviews on stock visibility, grocery freshness, and puja samagri.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(feedbackList, key = { it.id }) { fb ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fb.authorName, style = MaterialTheme.typography.titleSmall)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = fb.categoryEnum().label,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row {
                                repeat(fb.rating.toInt().coerceIn(1, 5)) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = fb.comment,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(
                            onClick = { onDeleteFeedback(fb.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (deleteProtectionEnabled) Icons.Filled.Lock else Icons.Filled.Delete,
                                contentDescription = if (deleteProtectionEnabled) "Locked review" else "Delete feedback",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiInsightsPane(
    insightsState: UiState<String>?,
    onGenerateInsights: () -> Unit,
    onSpeakInsights: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Gemini Stock & Puja Bundle Intelligence",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Analyze your live Present Stock and Out-of-Stock items to discover top restock priorities and ready-to-sell festival bundles.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onGenerateInsights,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_insights_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze Catalog with Gemini")
                }
            }
        }

        when (insightsState) {
            null -> Unit
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(insightsState.message, color = MaterialTheme.colorScheme.error)
            }

            is UiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recommendations", style = MaterialTheme.typography.titleMedium)
                            OutlinedButton(onClick = { onSpeakInsights(insightsState.data) }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Speak")
                            }
                        }
                        Text(insightsState.data, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
