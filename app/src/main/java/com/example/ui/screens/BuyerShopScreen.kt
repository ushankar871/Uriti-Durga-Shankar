package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductSubCategory
import com.example.model.SellerOrder
import com.example.model.UiState
import com.example.model.filterAndSortProducts
import com.example.model.inferSubCategory
import com.example.ui.BuyerViewModel
import com.example.ui.components.AnimatedOrderProgressTracker
import com.example.ui.components.ProductFilterSortBar
import com.example.ui.components.ProductThumbnail
import com.example.ui.components.PromotionalBannerCarousel
import com.example.ui.components.SearchAutocompleteField
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BuyerShopScreen(
    viewModel: BuyerViewModel,
    onOpenCart: () -> Unit,
    onOpenOrders: () -> Unit = {},
    onTrackOrder: (String) -> Unit = {},
    onSwitchToSellerLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storesState by viewModel.storesState.collectAsStateWithLifecycle()
    val activeStore by viewModel.activeStore.collectAsStateWithLifecycle()
    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val buyerOrdersState by viewModel.buyerOrdersState.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedSubCategory by viewModel.selectedSubCategory.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val inStockOnly by viewModel.inStockOnly.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val wishlistState by viewModel.wishlistState.collectAsStateWithLifecycle()
    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()

    var showFeedbackSheet by remember { mutableStateOf(false) }

    val stores = (storesState as? UiState.Success)?.data ?: emptyList()
    val latestOrder = (buyerOrdersState as? UiState.Success)?.data?.firstOrNull()
    val wishlistedProductIds = remember(wishlistState, activeStore) {
        val saved = (wishlistState as? UiState.Success)?.data ?: emptyList()
        val storeId = activeStore?.userId
        saved.filter { storeId == null || it.sellerId == storeId }.map { it.productId }.toSet()
    }
    val feedbackList = (feedbackState as? UiState.Success)?.data ?: emptyList()
    val avgRating = if (feedbackList.isNotEmpty()) feedbackList.map { it.rating.toDouble() }.average() else 0.0

    val totalCartCount = cartItems.values.sumOf { it.quantity }
    val totalCartInr = cartItems.values.sumOf { it.product.priceInr * it.quantity }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        when (storesState) {
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
                        text = (storesState as UiState.Error).message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            is UiState.Success -> {
                if (stores.isEmpty() || activeStore == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 520.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No Active Stores Yet",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = "Log in with a Seller account to publish your Lakshmi store & starter puja, fancy & grocery inventory, then return here to shop!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = onSwitchToSellerLogin) {
                                    Text("Switch to Seller Login")
                                }
                            }
                        }
                    }
                } else {
                    val store = activeStore!!
                    val allProducts = (productsState as? UiState.Success)?.data ?: emptyList()
                    val filtered = remember(
                        allProducts,
                        selectedCategory,
                        selectedSubCategory,
                        sortOption,
                        searchQuery,
                        inStockOnly
                    ) {
                        filterAndSortProducts(
                            products = allProducts,
                            primaryCategory = selectedCategory,
                            subCategory = selectedSubCategory,
                            sortOption = sortOption,
                            searchQuery = searchQuery,
                            inStockOnly = inStockOnly
                        )
                    }
                    val presentItems = remember(filtered) { filtered.filter { it.inStock } }
                    val outItems = remember(filtered) { filtered.filter { !it.inStock } }

                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 760.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = if (totalCartCount > 0) 96.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Store picker if multiple stores exist
                        if (stores.size > 1) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(stores, key = { it.userId }) { st ->
                                        FilterChip(
                                            selected = st.userId == store.userId,
                                            onClick = { viewModel.selectStore(st.userId) },
                                            label = { Text(st.storeName) }
                                        )
                                    }
                                }
                            }
                        }

                        // Friendly Store Welcome Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                                text = store.storeName,
                                                style = MaterialTheme.typography.headlineSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Text(
                                                text = "${store.address} • ${store.deliveryRadiusKm.toInt()} km delivery",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier
                                                .clickable { showFeedbackSheet = true }
                                                .testTag("buyer_rate_store_pill")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = "Rate store",
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (feedbackList.isEmpty()) "Rate" else String.format(Locale.US, "%.1f (%d)", avgRating, feedbackList.size),
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                if (isTtsSpeaking) viewModel.stopSpeaking() else viewModel.speakStoreStockSummary()
                                            },
                                            modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isTtsSpeaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isTtsSpeaking) "Stop Audio" else "Listen")
                                        }

                                        OutlinedButton(
                                            onClick = onOpenCart,
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 44.dp)
                                                .testTag("shop_header_cart_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ShoppingCart,
                                                contentDescription = "Open Cart",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (totalCartCount > 0) "Cart ($totalCartCount)" else "Cart")
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "${presentItems.size} Stock",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Promotional Banner Carousel (Seasonal Puja Offers & Trending Grocery Deals)
                        item(key = "buyer_promo_carousel") {
                            PromotionalBannerCarousel(
                                onBannerClick = { banner ->
                                    viewModel.applyPromotionalFilter(
                                        subCategory = banner.targetSubCategory,
                                        bannerTitle = banner.title
                                    )
                                }
                            )
                        }

                        // Most Recent Order Summary Card (no need to visit History)
                        if (latestOrder != null) {
                            item(key = "recent_order_summary") {
                                RecentOrderSummaryCard(
                                    order = latestOrder,
                                    onViewAllOrders = onOpenOrders,
                                    onTrackLiveMap = { onTrackOrder(latestOrder.id) }
                                )
                            }
                        }

                        // Search with Real-Time Autocomplete + Dedicated Sub-Category Filter & Sort Bar
                        item(key = "buyer_search_filter_bar") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SearchAutocompleteField(
                                    query = searchQuery,
                                    onQueryChange = { viewModel.setSearchQuery(it) },
                                    products = allProducts,
                                    onSelectCategory = { cat ->
                                        viewModel.setSelectedCategory(cat)
                                        viewModel.setSelectedSubCategory(ProductSubCategory.ALL)
                                    },
                                    onSelectProduct = { product -> viewModel.setSearchQuery(product.name) },
                                    label = "Search groceries, fancy, puja items or categories",
                                    testTag = "buyer_search_input"
                                )

                                ProductFilterSortBar(
                                    allProducts = allProducts,
                                    selectedSubCategory = selectedSubCategory,
                                    sortOption = sortOption,
                                    inStockOnly = inStockOnly,
                                    hasActiveSearch = searchQuery.isNotBlank() || selectedCategory != null,
                                    onSelectSubCategory = { sub -> viewModel.setSelectedSubCategory(sub) },
                                    onSelectSortOption = { option -> viewModel.setSortOption(option) },
                                    onToggleInStockOnly = { enabled -> viewModel.setInStockOnly(enabled) },
                                    onResetAll = { viewModel.clearCatalogFilters() }
                                )
                            }
                        }

                        // Available Present Stock Section
                        item {
                            Text(
                                text = "Available Now (${presentItems.size})",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        if (presentItems.isEmpty()) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No matching items currently in stock.",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(presentItems, key = { "buyer_in_${it.id}" }) { product ->
                                val qtyInCart = cartItems[product.id]?.quantity ?: 0
                                BuyerProductCard(
                                    product = product,
                                    qtyInCart = qtyInCart,
                                    isWishlisted = wishlistedProductIds.contains(product.id),
                                    onToggleWishlist = { viewModel.toggleWishlist(product) },
                                    onAdd = { viewModel.addToCart(product) },
                                    onRemove = { viewModel.removeFromCart(product) }
                                )
                            }
                        }

                        // Out of Stock Section (shown only when Seller's showOutOfStock == true)
                        if (store.showOutOfStock && outItems.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Currently Out of Stock (${outItems.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            items(outItems, key = { "buyer_out_${it.id}" }) { product ->
                                BuyerProductCard(
                                    product = product,
                                    qtyInCart = 0,
                                    isWishlisted = wishlistedProductIds.contains(product.id),
                                    onToggleWishlist = { viewModel.toggleWishlist(product) },
                                    onAdd = {},
                                    onRemove = {}
                                )
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
                    .testTag("buyer_cart_bar")
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

@Composable
private fun BuyerProductCard(
    product: Product,
    qtyInCart: Int,
    isWishlisted: Boolean,
    onToggleWishlist: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (product.inStock) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (product.inStock) 1.5.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        onClick = onToggleWishlist,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("buyer_wishlist_toggle_${product.id}")
                    ) {
                        Icon(
                            imageVector = if (isWishlisted) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isWishlisted) "Remove ${product.name} from wishlist" else "Save ${product.name} to wishlist",
                            tint = if (isWishlisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "${product.inferSubCategory().label} • ₹${product.priceInr.toInt()} / ${product.unit}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (product.description.isNotBlank()) {
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
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
                        color = if (product.inStock) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            text = if (product.inStock) "${product.stockQty} available" else "OUT OF STOCK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (product.inStock) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (product.inStock) {
                        if (qtyInCart == 0) {
                            Button(
                                onClick = onAdd,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("buyer_add_${product.id}")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = onRemove,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Decrease cart quantity")
                                }
                                Text(
                                    text = "$qtyInCart",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                FilledTonalIconButton(
                                    onClick = onAdd,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Increase cart quantity")
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
private fun RecentOrderSummaryCard(
    order: SellerOrder,
    onViewAllOrders: () -> Unit,
    onTrackLiveMap: () -> Unit
) {
    val status = order.statusEnum()
    val shortRef = remember(order.id) {
        order.id.takeLast(6).uppercase(Locale.US).ifBlank { "ORDER" }
    }
    val dateLabel = remember(order.createdAt) {
        val ts = order.createdAt
        if (ts == null) "Just placed" else SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(ts.toDate())
    }
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
            .clickable(onClick = onViewAllOrders)
            .testTag("home_recent_order_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (status == OrderStatus.DELIVERED) {
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (status == OrderStatus.DELIVERED) {
                            Icons.Filled.CheckCircle
                        } else {
                            Icons.Filled.LocalShipping
                        },
                        contentDescription = null,
                        tint = if (status == OrderStatus.DELIVERED) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Latest Order • #$shortRef",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

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
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            AnimatedOrderProgressTracker(
                currentStatus = status,
                compact = true
            )

            if (status != OrderStatus.DELIVERED && order.deliveryOtp.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                text = "Handoff Delivery OTP",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = order.deliveryOtp,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (status == OrderStatus.DELIVERED && order.deliveryMethod.isNotBlank()) {
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
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (order.deliveryMethod == "PHOTO") {
                            "Delivery confirmed via photograph"
                        } else {
                            "Delivery confirmed via Buyer OTP"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${order.storeName.ifBlank { "Lakshmi" }} • $dateLabel • ₹${order.totalInr.toInt()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilledTonalButton(
                        onClick = onTrackLiveMap,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("home_recent_order_track_map_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Map,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Live Map")
                    }
                    TextButton(
                        onClick = onViewAllOrders,
                        modifier = Modifier.testTag("home_recent_order_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("History")
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
