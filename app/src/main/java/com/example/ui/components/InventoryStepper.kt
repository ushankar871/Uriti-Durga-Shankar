package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Product

@Composable
fun QuickStockStepper(
    product: Product,
    onAdjustQty: (Long) -> Unit,
    onOpenExactQtyDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onAdjustQty(-5L) },
            enabled = product.stockQty > 0L,
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("stock_minus_5_${product.id}")
        ) {
            Text("-5", fontWeight = FontWeight.Bold)
        }

        FilledTonalIconButton(
            onClick = { onAdjustQty(-1L) },
            enabled = product.stockQty > 0L,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("stock_minus_1_${product.id}")
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Subtract 1 ${product.name} from inventory"
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 48.dp)
                .clickable(onClick = onOpenExactQtyDialog)
                .testTag("stock_qty_pill_${product.id}")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${product.stockQty}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tap to set",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        FilledTonalIconButton(
            onClick = { onAdjustQty(1L) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("stock_plus_1_${product.id}")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add 1 ${product.name} to inventory"
            )
        }

        OutlinedButton(
            onClick = { onAdjustQty(5L) },
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("stock_plus_5_${product.id}")
        ) {
            Text("+5", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SetStockQuantityDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirmQty: (Long) -> Unit
) {
    var qtyInput by remember(product.id) { mutableStateOf(product.stockQty.toString()) }
    val parsedQty = qtyInput.toLongOrNull()?.coerceIn(0L, 1_000_000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Inventory: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Current: ${product.stockQty} (${product.unit}). Enter exact stock count or tap a preset:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            val current = qtyInput.toLongOrNull() ?: 0L
                            qtyInput = (current - 1L).coerceAtLeast(0L).toString()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Subtract 1")
                    }

                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { raw ->
                            qtyInput = raw.filter { it.isDigit() }.take(7)
                        },
                        label = { Text("Stock Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("exact_qty_input")
                    )

                    FilledTonalIconButton(
                        onClick = {
                            val current = qtyInput.toLongOrNull() ?: 0L
                            qtyInput = (current + 1L).coerceAtMost(1_000_000L).toString()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add 1")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0L to "0 (Out)", 5L to "5", 15L to "15", 30L to "30").forEach { (preset, label) ->
                        FilterChip(
                            selected = parsedQty == preset,
                            onClick = { qtyInput = preset.toString() },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeQty = parsedQty ?: 0L
                    onConfirmQty(safeQty)
                },
                enabled = parsedQty != null,
                modifier = Modifier.testTag("confirm_exact_qty_button")
            ) {
                Spacer(modifier = Modifier.width(2.dp))
                Text("Update Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
