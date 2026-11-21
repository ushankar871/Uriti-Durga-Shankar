package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.SellerOrder
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeliveryConfirmationDialog(
    order: SellerOrder,
    sellerUid: String,
    onDismiss: () -> Unit,
    onConfirmed: (method: String, proofUri: String) -> Unit,
    onScanQrClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usePhotoMethod by remember { mutableStateOf(false) }
    var enteredOtp by remember { mutableStateOf("") }
    var proofPhotoUri by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val expectedOtp = remember(order) { order.deliveryOtp.trim() }
    val isSelfTestOrder = remember(order, sellerUid) { order.buyerId == sellerUid }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            proofPhotoUri = uri.toString()
            errorMessage = null
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            scope.launch {
                val savedUri = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "delivery_proofs").apply { mkdirs() }
                    val file = File(dir, "proof_${order.id}_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    Uri.fromFile(file).toString()
                }
                proofPhotoUri = savedUri
                errorMessage = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Confirm Delivery • ${order.customerName}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Verify handoff for ${order.itemsSummary} (₹${order.totalInr.toInt()}) using the buyer's 6-digit OTP or a delivery photograph.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !usePhotoMethod,
                        onClick = {
                            usePhotoMethod = false
                            errorMessage = null
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Buyer OTP") },
                        modifier = Modifier.testTag("delivery_method_otp_chip")
                    )
                    FilterChip(
                        selected = usePhotoMethod,
                        onClick = {
                            usePhotoMethod = true
                            errorMessage = null
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Photograph") },
                        modifier = Modifier.testTag("delivery_method_photo_chip")
                    )
                }

                if (onScanQrClick != null) {
                    OutlinedButton(
                        onClick = onScanQrClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("delivery_dialog_scan_qr_button")
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Delivery QR Code Instead")
                    }
                }

                if (!usePhotoMethod) {
                    if (expectedOtp.isBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Legacy order without preset buyer PIN: enter any 4–6 digit handoff confirmation code from customer.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else if (isSelfTestOrder) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Self-logged sample order OTP: $expectedOtp",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Ask the buyer for the 6-digit Delivery OTP shown on their Lakshmi Orders screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = { input ->
                            enteredOtp = input.filter { it.isDigit() }.take(6)
                            errorMessage = null
                        },
                        label = { Text("Enter Buyer Delivery OTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delivery_otp_input")
                    )
                } else {
                    Text(
                        text = "Capture a doorstep/parcel photo or attach a delivery proof image from your gallery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { cameraPreviewLauncher.launch(null) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("delivery_take_photo_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera")
                        }
                        OutlinedButton(
                            onClick = {
                                galleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("delivery_pick_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery")
                        }
                    }

                    if (proofPhotoUri.isNotBlank()) {
                        AsyncImage(
                            model = proofPhotoUri,
                            contentDescription = "Delivery confirmation photograph preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!usePhotoMethod) {
                        val clean = enteredOtp.trim()
                        if (expectedOtp.isNotBlank()) {
                            if (clean != expectedOtp) {
                                errorMessage = "Incorrect Delivery OTP. Check the 6-digit code with the buyer."
                                return@Button
                            }
                        } else if (clean.length < 4) {
                            errorMessage = "Enter at least a 4-digit delivery confirmation code."
                            return@Button
                        }
                        onConfirmed("OTP", "")
                    } else {
                        if (proofPhotoUri.isBlank()) {
                            errorMessage = "Please capture or select a delivery photograph first."
                            return@Button
                        }
                        onConfirmed("PHOTO", proofPhotoUri)
                    }
                },
                modifier = Modifier.testTag("confirm_delivery_submit_button")
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Confirm Delivery")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
