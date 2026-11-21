package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.UiState
import com.example.ui.SellerViewModel
import com.example.ui.components.generalItemImagePresets
import com.example.ui.components.resolveProductArtworkRes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductEditorDialog(
    initialProduct: Product?,
    viewModel: SellerViewModel,
    prefilledName: String = "",
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        category: ProductCategory,
        unit: String,
        price: Double,
        qty: Long,
        inStock: Boolean,
        desc: String,
        photoUri: String,
        imageTag: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(initialProduct?.name ?: prefilledName) }
    var category by remember { mutableStateOf(initialProduct?.categoryEnum() ?: ProductCategory.GROCERY) }
    var unit by remember { mutableStateOf(initialProduct?.unit ?: "1 pack") }
    var priceText by remember { mutableStateOf(initialProduct?.priceInr?.toInt()?.toString() ?: "90") }
    var qtyText by remember { mutableStateOf(initialProduct?.stockQty?.toString() ?: "10") }
    var inStock by remember { mutableStateOf(initialProduct?.inStock ?: true) }
    var description by remember { mutableStateOf(initialProduct?.description ?: "") }
    var photoUri by remember { mutableStateOf(initialProduct?.photoUri ?: "") }
    var imageTag by remember { mutableStateOf(initialProduct?.imageTag ?: "") }

    val presets = remember { generalItemImagePresets() }
    val previewRes = resolveProductArtworkRes(imageTag, category)
    val smartFillState by viewModel.smartFillState.collectAsStateWithLifecycle()

    LaunchedEffect(smartFillState) {
        val success = smartFillState as? UiState.Success
        if (success != null) {
            category = success.data.category
            unit = success.data.unit
            priceText = success.data.priceInr.toInt().toString()
            description = success.data.description
            imageTag = success.data.imageTag
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { photoUri = it.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialProduct == null) "Add Puja, Fancy or Grocery Item" else "Edit Item")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_editor_name_input")
                )

                OutlinedButton(
                    onClick = { viewModel.runSmartFill(name) },
                    enabled = name.isNotBlank() && smartFillState !is UiState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (smartFillState is UiState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Filling with Gemini...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Smart Fill Details")
                    }
                }

                if (smartFillState is UiState.Error) {
                    Text(
                        text = (smartFillState as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ProductCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) },
                            modifier = Modifier.testTag("editor_category_${cat.name.lowercase()}")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Price (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val next = ((qtyText.toLongOrNull() ?: 0L) - 1L).coerceAtLeast(0L)
                            qtyText = next.toString()
                            if (next == 0L) inStock = false
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Subtract stock")
                    }

                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { raw ->
                            val clean = raw.filter { it.isDigit() }.take(7)
                            qtyText = clean
                            val parsed = clean.toLongOrNull() ?: 0L
                            inStock = parsed > 0L
                        },
                        label = { Text("Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val next = ((qtyText.toLongOrNull() ?: 0L) + 1L).coerceAtMost(1_000_000L)
                            qtyText = next.toString()
                            if (next > 0L) inStock = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add stock")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("In Stock", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = inStock, onCheckedChange = { inStock = it })
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Short Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                if (photoUri.isNotBlank()) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Selected product image preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                        error = painterResource(id = previewRes),
                                        placeholder = painterResource(id = previewRes)
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = previewRes),
                                        contentDescription = "Selected AI item image preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI General Item Images",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (photoUri.isNotBlank()) {
                                        "Using uploaded device photo (or tap an AI image below)"
                                    } else {
                                        "Pick ready-made studio item art synced across buyer & seller devices"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets, key = { it.tag }) { preset ->
                                val isSelected = photoUri.isBlank() &&
                                    (imageTag.equals(preset.tag, ignoreCase = true) ||
                                        (imageTag.isBlank() && resolveProductArtworkRes("", category) == preset.drawableRes && preset.suggestedCategory == category))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(74.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            photoUri = ""
                                            imageTag = preset.tag
                                        }
                                        .testTag("preset_img_${preset.tag}")
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outlineVariant
                                            }
                                        ),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Image(
                                                painter = painterResource(id = preset.drawableRes),
                                                contentDescription = preset.label,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (isSelected) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(18.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = "Selected",
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("upload_device_photo_button")
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (photoUri.isBlank()) "Upload Device Photo" else "Change Photo")
                            }
                            if (photoUri.isNotBlank()) {
                                TextButton(onClick = { photoUri = "" }) {
                                    Text("Use AI Art")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceText.toDoubleOrNull() ?: 0.0
                    val qty = qtyText.toLongOrNull() ?: 0L
                    onSave(name, category, unit, price, qty, inStock, description, photoUri, imageTag)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("product_editor_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

