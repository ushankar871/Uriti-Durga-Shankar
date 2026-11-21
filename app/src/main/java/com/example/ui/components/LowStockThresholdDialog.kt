package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LowStockThresholdDialog(
    currentThreshold: Long,
    currentAlertsEnabled: Boolean,
    allProducts: List<Product>,
    onDismiss: () -> Unit,
    onSave: (threshold: Long, alertsEnabled: Boolean) -> Unit
) {
    var thresholdText by remember { mutableStateOf(currentThreshold.coerceIn(1L, 1000L).toString()) }
    var alertsEnabled by remember { mutableStateOf(currentAlertsEnabled) }

    val parsedThreshold = thresholdText.toLongOrNull()?.coerceIn(1L, 1000L)
    val previewCount = remember(allProducts, parsedThreshold) {
        if (parsedThreshold == null) 0 else allProducts.count { it.isLowStock(parsedThreshold) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Low-Stock Alert Threshold")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Get an automatic dashboard highlight and push notification whenever any grocery or puja item falls to or below this quantity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automated Push & Highlights",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (alertsEnabled) "Active for low & out-of-stock items" else "Paused by owner",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = alertsEnabled,
                            onCheckedChange = { alertsEnabled = it },
                            modifier = Modifier.testTag("low_stock_alerts_enabled_switch")
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(3L, 5L, 10L, 15L, 25L).forEach { preset ->
                        FilterChip(
                            selected = parsedThreshold == preset,
                            onClick = { thresholdText = preset.toString() },
                            label = { Text("≤ $preset") },
                            modifier = Modifier.testTag("threshold_preset_$preset")
                        )
                    }
                }

                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { input ->
                        thresholdText = input.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("Alert Threshold (units, 1–1000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("low_stock_threshold_input")
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (previewCount > 0) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (parsedThreshold == null) {
                            "Enter a threshold between 1 and 1000 units."
                        } else if (previewCount == 0) {
                            "All ${allProducts.size} catalog items are currently above $parsedThreshold units."
                        } else {
                            "$previewCount item${if (previewCount == 1) "" else "s"} in your catalog will trigger an alert at ≤ $parsedThreshold units."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (previewCount > 0) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = (parsedThreshold ?: currentThreshold).coerceIn(1L, 1000L)
                    onSave(value, alertsEnabled)
                },
                enabled = parsedThreshold != null,
                modifier = Modifier.testTag("save_threshold_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
