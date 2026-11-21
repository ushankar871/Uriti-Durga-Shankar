package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.FilterChip
import com.example.model.BuyerAddress
import com.example.model.CartItem
import com.example.model.PreferredPaymentMethod
import com.example.model.Product
import com.example.model.StoreConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerCartSheet(
    store: StoreConfig?,
    defaultName: String,
    cartItems: List<CartItem>,
    savedAddresses: List<BuyerAddress> = emptyList(),
    paymentMethods: List<PreferredPaymentMethod> = emptyList(),
    onDismiss: () -> Unit,
    onAddQty: (Product) -> Unit,
    onRemoveQty: (Product) -> Unit,
    onDeleteLineItem: (String) -> Unit,
    onClearCart: () -> Unit,
    onBrowseShop: () -> Unit,
    onPlaceOrder: (customerName: String, neighborhood: String) -> Unit
) {
    val defaultAddress = remember(savedAddresses) {
        savedAddresses.firstOrNull { it.defaultAddress } ?: savedAddresses.firstOrNull()
    }
    val defaultPayment = remember(paymentMethods) {
        paymentMethods.firstOrNull { it.defaultMethod } ?: paymentMethods.firstOrNull()
    }
    var buyerName by remember(defaultName, defaultAddress?.recipientName) {
        mutableStateOf(defaultAddress?.recipientName?.ifBlank { null } ?: defaultName)
    }
    var neighborhood by remember(defaultAddress?.id) {
        mutableStateOf(defaultAddress?.formattedDeliverySummary().orEmpty())
    }
    var selectedPaymentId by remember(defaultPayment?.id) {
        mutableStateOf(defaultPayment?.id)
    }
    val totalCount = cartItems.sumOf { it.quantity }
    val totalInr = cartItems.sumOf { it.product.priceInr * it.quantity }
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
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (totalCount > 0) "Your Cart ($totalCount)" else "Your Cart",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (store != null) {
                            Text(
                                text = store.storeName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (cartItems.isNotEmpty()) {
                    TextButton(
                        onClick = onClearCart,
                        modifier = Modifier.testTag("cart_clear_button")
                    ) {
                        Text("Clear Cart")
                    }
                }
            }

            if (cartItems.isEmpty() || store == null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = "Your Lakshmi Cart is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add fresh groceries, fancy items, festive puja needs, or saved wishlist items to begin checkout.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onBrowseShop()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("empty_cart_browse_shop_button")
                        ) {
                            Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browse Puja, Fancy & Groceries")
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cartItems.forEach { item ->
                        key(item.product.id) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProductThumbnail(
                                    product = item.product,
                                    modifier = Modifier.size(52.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.product.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "₹${item.product.priceInr.toInt()} / ${item.product.unit} • Line: ₹${(item.product.priceInr * item.quantity).toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = { onRemoveQty(item.product) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("cart_dec_${item.product.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Remove,
                                            contentDescription = "Decrease ${item.product.name} quantity",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "${item.quantity}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FilledTonalIconButton(
                                        onClick = { onAddQty(item.product) },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("cart_inc_${item.product.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = "Increase ${item.product.name} quantity",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteLineItem(item.product.id) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove ${item.product.name} from cart",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Subtotal ($totalCount items)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${totalInr.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (savedAddresses.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Saved Addresses",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedAddresses, key = { it.id }) { addr ->
                            val summary = addr.formattedDeliverySummary()
                            FilterChip(
                                selected = neighborhood == summary,
                                onClick = {
                                    neighborhood = summary
                                    if (addr.recipientName.isNotBlank()) {
                                        buyerName = addr.recipientName
                                    }
                                },
                                label = { Text("${addr.label}: ${addr.neighborhood}") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = buyerName,
                    onValueChange = { buyerName = it },
                    label = { Text("Recipient Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = neighborhood,
                    onValueChange = { neighborhood = it },
                    label = { Text("Delivery Neighborhood / Address") },
                    placeholder = { Text("e.g. Indiranagar 12th Main") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("buyer_checkout_address")
                )

                if (paymentMethods.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Payments,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Preferred Payment",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(paymentMethods, key = { it.id }) { method ->
                            FilterChip(
                                selected = selectedPaymentId == method.id,
                                onClick = { selectedPaymentId = method.id },
                                label = {
                                    Text(
                                        if (method.handleOrHint.isNotBlank()) {
                                            "${method.displayLabel} (${method.handleOrHint})"
                                        } else {
                                            method.displayLabel
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = { onPlaceOrder(buyerName, neighborhood.ifBlank { "Near ${store.address}" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("place_buyer_order_button")
                ) {
                    Text("Place Order • ₹${totalInr.toInt()}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
