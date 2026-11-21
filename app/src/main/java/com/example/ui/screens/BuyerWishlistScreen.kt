package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.UiState
import com.example.model.WishlistItem
import com.example.ui.BuyerViewModel
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.SearchAutocompleteField

@Composable
fun BuyerWishlistScreen(
    viewModel: BuyerViewModel,
    onOpenShop: () -> Unit,
    onOpenCart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val wishlistState by viewModel.wishlistState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.wishlistCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.wishlistSearchQuery.collectAsStateWithLifecycle()
    val activeStore by viewModel.activeStore.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()

    val liveProducts = (productsState as? UiState.Success)?.data ?: emptyList()
    val totalCartCount = cartItems.values.sumOf { it.quantity }
    val totalCartInr = cartItems.values.sumOf { it.product.priceInr * it.quantity }

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
                    Text("Saved Wishlist", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "Save favorite puja needs & groceries for later",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val savedList = (wishlistState as? UiState.Success)?.data ?: emptyList()
                if (savedList.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearWishlist() },
                        modifier = Modifier.testTag("wishlist_clear_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear (${savedList.size})")
                    }
                }
            }

            when (val state = wishlistState) {
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
                    val itemsList = state.data
                    if (itemsList.isEmpty()) {
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
                                    imageVector = Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text("Your wishlist is empty", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "Tap the heart icon on any Puja Need, Fancy or Grocery item in the Shop tab to save it here for later.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onOpenShop,
                                    modifier = Modifier.testTag("empty_wishlist_browse_button")
                                ) {
                                    Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Browse Catalog")
                                }
                            }
                        }
                    } else {
                        val filteredItems = remember(itemsList, selectedCategory, searchQuery) {
                            val q = searchQuery.trim()
                            itemsList.filter { item ->
                                val matchesCat = selectedCategory == null || item.categoryEnum() == selectedCategory
                                val matchesQuery = q.isEmpty() ||
                                    item.name.contains(q, ignoreCase = true) ||
                                    item.description.contains(q, ignoreCase = true) ||
                                    item.storeName.contains(q, ignoreCase = true) ||
                                    item.categoryEnum().displayName.contains(q, ignoreCase = true)
                                matchesCat && matchesQuery
                            }
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 6.dp,
                                bottom = if (totalCartCount > 0) 96.dp else 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "wishlist_search") {
                                val wishlistProducts = remember(itemsList) {
                                    itemsList.map { it.toPreviewProduct() }
                                }
                                SearchAutocompleteField(
                                    query = searchQuery,
                                    onQueryChange = { viewModel.setWishlistSearchQuery(it) },
                                    products = wishlistProducts,
                                    onSelectCategory = { cat -> viewModel.setWishlistCategory(cat) },
                                    onSelectProduct = { product -> viewModel.setWishlistSearchQuery(product.name) },
                                    label = "Search saved puja, fancy or grocery items",
                                    testTag = "wishlist_search_input"
                                )
                            }

                            item(key = "wishlist_category_chips") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedCategory == null,
                                            onClick = { viewModel.setWishlistCategory(null) },
                                            label = { Text("All (${itemsList.size})") },
                                            modifier = Modifier.testTag("wishlist_category_all")
                                        )
                                    }
                                    items(ProductCategory.entries, key = { it.name }) { category ->
                                        val count = itemsList.count { it.categoryEnum() == category }
                                        FilterChip(
                                            selected = selectedCategory == category,
                                            onClick = {
                                                viewModel.setWishlistCategory(
                                                    if (selectedCategory == category) null else category
                                                )
                                            },
                                            label = { Text("${category.displayName} ($count)") },
                                            modifier = Modifier.testTag("wishlist_category_${category.name.lowercase()}")
                                        )
                                    }
                                }
                            }

                            if (filteredItems.isEmpty()) {
                                item(key = "wishlist_no_matches") {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "No matching wishlist items",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            TextButton(
                                                onClick = {
                                                    viewModel.setWishlistCategory(null)
                                                    viewModel.setWishlistSearchQuery("")
                                                }
                                            ) {
                                                Text("Show all saved items")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(filteredItems, key = { it.id }) { item ->
                                    val liveMatch: Product? = if (activeStore?.userId == item.sellerId) {
                                        liveProducts.firstOrNull { it.id == item.productId }
                                    } else {
                                        null
                                    }
                                    WishlistItemCard(
                                        item = item,
                                        liveProduct = liveMatch,
                                        isActiveStore = activeStore?.userId == item.sellerId,
                                        onAddToCartOrOpenStore = {
                                            viewModel.addWishlistItemToCart(item, onOpenShop = onOpenShop)
                                        },
                                        onRemove = { viewModel.removeWishlistItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (totalCartCount > 0) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onOpenCart() }
                    .testTag("wishlist_cart_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = "Cart",
                            tint = MaterialTheme.colorScheme.onTertiary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "$totalCartCount item${if (totalCartCount == 1) "" else "s"} in cart",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                            Text(
                                text = "Total: ₹${totalCartInr.toInt()}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "View Cart →",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistItemCard(
    item: WishlistItem,
    liveProduct: Product?,
    isActiveStore: Boolean,
    onAddToCartOrOpenStore: () -> Unit,
    onRemove: () -> Unit
) {
    val previewProduct = remember(item, liveProduct) {
        liveProduct ?: item.toPreviewProduct()
    }
    val inStockNow = liveProduct?.let { it.inStock && it.stockQty > 0L } ?: true

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wishlist_card_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductThumbnail(
                product = previewProduct,
                modifier = Modifier.size(80.dp)
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
                        text = previewProduct.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("wishlist_remove_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Remove ${previewProduct.name} from wishlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "${item.categoryEnum().displayName} • ₹${previewProduct.priceInr.toInt()} / ${previewProduct.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storefront,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = item.storeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActiveStore && liveProduct != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (inStockNow) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        ) {
                            Text(
                                text = if (inStockNow) "${liveProduct.stockQty} in stock" else "Out of Stock",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (inStockNow) {
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = item.categoryEnum().displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isActiveStore) {
                        Button(
                            onClick = onAddToCartOrOpenStore,
                            enabled = inStockNow,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("wishlist_move_to_cart_${item.id}")
                        ) {
                            Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (inStockNow) "Add to Basket" else "Unavailable")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onAddToCartOrOpenStore,
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Open Store")
                        }
                    }
                }
            }
        }
    }
}
