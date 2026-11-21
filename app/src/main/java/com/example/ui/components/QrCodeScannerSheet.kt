package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.SellerOrder
import com.example.qr.ParsedQrPayload
import com.example.qr.QrPayloadParser
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

enum class QrScannerMode(val label: String) {
    AUTO("Auto Scan"),
    INVENTORY_UPDATE("Inventory QR"),
    DELIVERY_CONFIRM("Delivery QR")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScannerSheet(
    initialMode: QrScannerMode = QrScannerMode.AUTO,
    products: List<Product>,
    orders: List<SellerOrder>,
    onDismiss: () -> Unit,
    onUpdateProductQty: (Product, Long) -> Unit,
    onToggleProductStock: (Product, Boolean) -> Unit,
    onConfirmDeliveryViaQr: (SellerOrder, String) -> Unit,
    onOpenOtpOrPhotoDialog: (SellerOrder) -> Unit,
    onCreateItemFromQr: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scannedRawCode by remember { mutableStateOf<String?>(null) }
    var manualCodeInput by remember { mutableStateOf("") }
    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    val parsedPayload = remember(scannedRawCode) {
        scannedRawCode?.let { QrPayloadParser.parse(it) }
    }
    val matchedProduct = remember(parsedPayload, orders, products, mode) {
        val payload = parsedPayload ?: return@remember null
        when (payload) {
            is ParsedQrPayload.ItemRef -> QrPayloadParser.matchProduct(payload, products)
            is ParsedQrPayload.OrderRef -> null
        }
    }
    val matchedOrder = remember(parsedPayload, orders, mode) {
        val raw = scannedRawCode ?: return@remember null
        when (val payload = parsedPayload) {
            is ParsedQrPayload.OrderRef -> QrPayloadParser.matchOrder(payload, orders)
            is ParsedQrPayload.ItemRef -> if (mode != QrScannerMode.INVENTORY_UPDATE) {
                QrPayloadParser.findOrderFromRaw(raw, orders)
            } else {
                null
            }
            null -> null
        }
    }

    var editQty by remember(matchedProduct?.id) {
        mutableLongStateOf(matchedProduct?.stockQty ?: 0L)
    }

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
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Seller QR Scanner",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scan item tags for +/- stock or order QR for delivery handoff",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_qr_scanner_button")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close QR scanner")
                }
            }

            // Scanner Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QrScannerMode.entries.forEach { entry ->
                    FilterChip(
                        selected = mode == entry,
                        onClick = { mode = entry },
                        label = { Text(entry.label) },
                        modifier = Modifier.testTag("qr_mode_${entry.name.lowercase()}")
                    )
                }
            }

            if (scannedRawCode == null) {
                if (hasCameraPermission) {
                    CameraQrPreviewBox(
                        torchEnabled = torchEnabled,
                        onToggleTorch = {
                            val cam = boundCamera
                            if (cam != null && cam.cameraInfo.hasFlashUnit()) {
                                val next = !torchEnabled
                                cam.cameraControl.enableTorch(next)
                                torchEnabled = next
                            }
                        },
                        onCameraBound = { boundCamera = it },
                        onQrDetected = { raw ->
                            if (scannedRawCode == null) {
                                scannedRawCode = raw
                                manualCodeInput = raw
                            }
                        },
                        lifecycleOwner = lifecycleOwner
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Camera permission needed for live QR scanning",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Grant camera access or paste/enter a QR code below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("grant_qr_camera_permission_button")
                            ) {
                                Text("Enable Camera")
                            }
                        }
                    }
                }

                // Manual / Emulator QR Lookup & Quick Test Chips
                OutlinedTextField(
                    value = manualCodeInput,
                    onValueChange = { manualCodeInput = it },
                    label = { Text("Or enter/paste QR code or SKU") },
                    placeholder = { Text("LAKSHMI:ITEM:<id> or LAKSHMI:ORDER:<id>:<otp>") },
                    leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_manual_input")
                )

                Button(
                    onClick = {
                        val trimmed = manualCodeInput.trim()
                        if (trimmed.isNotEmpty()) {
                            scannedRawCode = trimmed
                        }
                    },
                    enabled = manualCodeInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("qr_manual_decode_button")
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Process QR Payload")
                }

                val sampleProduct = products.firstOrNull { it.stockQty <= 5L } ?: products.firstOrNull()
                val sampleOrder = orders.firstOrNull { it.statusEnum() != OrderStatus.DELIVERED }
                if (sampleProduct != null || sampleOrder != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quick Sample QR Tags (QA / Emulator):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (sampleProduct != null) {
                                AssistChip(
                                    onClick = {
                                        val code = QrPayloadParser.encodeProductQr(sampleProduct)
                                        manualCodeInput = code
                                        scannedRawCode = code
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = {
                                        Text(
                                            text = "Item: ${sampleProduct.name}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("qr_sample_item_chip")
                                )
                            }
                            if (sampleOrder != null) {
                                AssistChip(
                                    onClick = {
                                        val code = QrPayloadParser.encodeOrderQr(sampleOrder)
                                        manualCodeInput = code
                                        scannedRawCode = code
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    label = {
                                        Text(
                                            text = "Order #${sampleOrder.id.takeLast(5)}",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("qr_sample_order_chip")
                                )
                            }
                        }
                    }
                }
            } else {
                // Scanned QR Result Action Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scanned QR Payload",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = scannedRawCode!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scannedRawCode = null
                                manualCodeInput = ""
                            },
                            modifier = Modifier.testTag("qr_rescan_button")
                        ) {
                            Text("Rescan")
                        }
                    }
                }

                when {
                    matchedOrder != null -> {
                        val order = matchedOrder
                        val qrOtp = (parsedPayload as? ParsedQrPayload.OrderRef)?.otp.orEmpty()
                        val otpMatches = order.deliveryOtp.isBlank() ||
                            (qrOtp.isNotBlank() && qrOtp == order.deliveryOtp.trim())

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_matched_order_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "Order Matched • ${order.customerName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${order.itemsSummary} • ₹${order.totalInr.toInt()} (${order.statusEnum().label})",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (order.statusEnum() == OrderStatus.DELIVERED) {
                                    Text(
                                        text = "This order has already been marked Delivered.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                } else if (otpMatches) {
                                    Button(
                                        onClick = {
                                            onConfirmDeliveryViaQr(order, qrOtp)
                                            onDismiss()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("qr_confirm_delivery_now_button")
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Confirm Delivery via QR")
                                    }
                                } else {
                                    Text(
                                        text = "Order QR verified, but handoff OTP wasn't embedded in this QR. Continue with OTP or Photo.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onOpenOtpOrPhotoDialog(order)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("qr_open_otp_photo_dialog_button")
                                    ) {
                                        Text("Enter Buyer OTP / Capture Photo")
                                    }
                                }
                            }
                        }
                    }

                    matchedProduct != null -> {
                        val product = matchedProduct
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("qr_matched_product_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = product.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${product.categoryEnum().displayName} • Current: ${product.stockQty} ${product.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            onToggleProductStock(product, !product.inStock)
                                            onDismiss()
                                        },
                                        modifier = Modifier.testTag("qr_toggle_stock_button")
                                    ) {
                                        Text(if (product.inStock) "Mark Out" else "Mark In Stock")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { editQty = (editQty - 1L).coerceAtLeast(0L) },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", modifier = Modifier.size(16.dp))
                                        Text("1")
                                    }
                                    OutlinedTextField(
                                        value = editQty.toString(),
                                        onValueChange = { raw ->
                                            val digits = raw.filter { it.isDigit() }
                                            editQty = digits.toLongOrNull()?.coerceIn(0L, 999_999L) ?: 0L
                                        },
                                        label = { Text("New Qty") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("qr_product_qty_input")
                                    )
                                    OutlinedButton(
                                        onClick = { editQty = (editQty + 1L).coerceAtMost(999_999L) },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase quantity", modifier = Modifier.size(16.dp))
                                        Text("1")
                                    }
                                    OutlinedButton(
                                        onClick = { editQty = (editQty + 5L).coerceAtMost(999_999L) },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    ) {
                                        Text("+5")
                                    }
                                }

                                Button(
                                    onClick = {
                                        onUpdateProductQty(product, editQty)
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("qr_apply_inventory_update_button")
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Apply Stock Update ($editQty ${product.unit})")
                                }
                            }
                        }
                    }

                    else -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "No matching item or active order found",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "You can add a new product using this scanned code or scan a different QR tag.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        val suggestedName = (parsedPayload as? ParsedQrPayload.ItemRef)?.queryText
                                            ?: scannedRawCode.orEmpty()
                                        onDismiss()
                                        onCreateItemFromQr(suggestedName)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("qr_create_new_item_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create New Item From Scan")
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
private fun CameraQrPreviewBox(
    torchEnabled: Boolean,
    onToggleTorch: () -> Unit,
    onCameraBound: (Camera) -> Unit,
    onQrDetected: (String) -> Unit,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var providerRef: ProcessCameraProvider? = null

        val listener = Runnable {
            try {
                val provider = cameraProviderFuture.get()
                providerRef = provider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.trim() }
                                if (!value.isNullOrEmpty()) {
                                    onQrDetected(value)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                onCameraBound(camera)
            } catch (_: Exception) {
                // Gracefully fallback to manual QR input if device has no back camera
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                providerRef?.unbindAll()
            } catch (_: Exception) {
            }
            barcodeScanner.close()
            analyzerExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(18.dp))
            .testTag("qr_camera_preview_box")
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Targeting Reticle Frame
        Box(
            modifier = Modifier
                .size(144.dp)
                .align(Alignment.Center)
                .border(
                    BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary),
                    RoundedCornerShape(16.dp)
                )
        )

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            IconButton(
                onClick = onToggleTorch,
                modifier = Modifier.testTag("qr_torch_toggle_button")
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = if (torchEnabled) "Turn off flashlight" else "Turn on flashlight"
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp)
        ) {
            Text(
                text = "Align QR code inside frame",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}
