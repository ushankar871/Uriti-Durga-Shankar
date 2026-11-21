package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAssistantService
import com.example.data.SellerRepository
import com.example.model.AccountProfile
import com.example.model.AccountRole
import com.example.model.BuyerAddress
import com.example.model.CartItem
import com.example.model.FeedbackCategory
import com.example.model.OrderStatus
import com.example.model.PaymentMethodType
import com.example.model.PreferredPaymentMethod
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductSortOption
import com.example.model.ProductSubCategory
import com.example.model.SellerOrder
import com.example.model.StoreConfig
import com.example.model.StoreFeedback
import com.example.model.UiState
import com.example.model.WishlistItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OrderHistoryFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    DELIVERED("Delivered"),
    PENDING("Pending"),
    PACKED("Packed"),
    OUT_FOR_DELIVERY("On the Way");

    fun matches(status: OrderStatus): Boolean = when (this) {
        ALL -> true
        ACTIVE -> status != OrderStatus.DELIVERED
        DELIVERED -> status == OrderStatus.DELIVERED
        PENDING -> status == OrderStatus.PENDING
        PACKED -> status == OrderStatus.PACKED
        OUT_FOR_DELIVERY -> status == OrderStatus.OUT_FOR_DELIVERY
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BuyerViewModel(
    private val repository: SellerRepository,
    private val aiService: GeminiAssistantService,
    val buyerId: String,
    val buyerDisplayName: String
) : ViewModel() {

    private companion object {
        const val TAG = "BuyerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }

    private fun <T> Flow<T>.toUiState(errorFallback: String): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, errorFallback, rootError)
                emit(UiState.Error(rootError.message ?: errorFallback))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val storesState: StateFlow<UiState<List<StoreConfig>>> =
        repository.observeAllStores().toUiState("Failed to load stores")

    private val _selectedStoreId = MutableStateFlow<String?>(null)
    val selectedStoreId: StateFlow<String?> = _selectedStoreId.asStateFlow()

    val activeStore: StateFlow<StoreConfig?> = combine(storesState, _selectedStoreId) { storesUi, pickedId ->
        val list = (storesUi as? UiState.Success)?.data ?: emptyList()
        if (list.isEmpty()) null
        else list.firstOrNull { it.userId == pickedId } ?: list.first()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val productsState: StateFlow<UiState<List<Product>>> = activeStore
        .flatMapLatest { store ->
            if (store == null) flowOf(emptyList())
            else repository.observeProducts(store.userId)
        }
        .toUiState("Failed to load store inventory")

    val feedbackState: StateFlow<UiState<List<StoreFeedback>>> = activeStore
        .flatMapLatest { store ->
            if (store == null) flowOf(emptyList())
            else repository.observeFeedback(store.userId)
        }
        .toUiState("Failed to load store reviews")

    val buyerOrdersState: StateFlow<UiState<List<SellerOrder>>> =
        repository.observeBuyerOrders(buyerId).toUiState("Failed to load your orders")

    val wishlistState: StateFlow<UiState<List<WishlistItem>>> =
        repository.observeWishlist(buyerId).toUiState("Failed to load wishlist")

    val accountProfileState: StateFlow<UiState<AccountProfile?>> =
        repository.observeAccountProfile(buyerId).toUiState("Failed to load profile")

    val addressesState: StateFlow<UiState<List<BuyerAddress>>> =
        repository.observeBuyerAddresses(buyerId).toUiState("Failed to load saved addresses")

    val paymentMethodsState: StateFlow<UiState<List<PreferredPaymentMethod>>> =
        repository.observePreferredPaymentMethods(buyerId).toUiState("Failed to load payment preferences")

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _selectedSubCategory = MutableStateFlow(ProductSubCategory.ALL)
    val selectedSubCategory: StateFlow<ProductSubCategory> = _selectedSubCategory.asStateFlow()

    private val _sortOption = MutableStateFlow(ProductSortOption.FEATURED)
    val sortOption: StateFlow<ProductSortOption> = _sortOption.asStateFlow()

    private val _inStockOnly = MutableStateFlow(false)
    val inStockOnly: StateFlow<Boolean> = _inStockOnly.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _wishlistCategory = MutableStateFlow<ProductCategory?>(null)
    val wishlistCategory: StateFlow<ProductCategory?> = _wishlistCategory.asStateFlow()

    private val _wishlistSearchQuery = MutableStateFlow("")
    val wishlistSearchQuery: StateFlow<String> = _wishlistSearchQuery.asStateFlow()

    private val _orderHistoryFilter = MutableStateFlow(OrderHistoryFilter.ALL)
    val orderHistoryFilter: StateFlow<OrderHistoryFilter> = _orderHistoryFilter.asStateFlow()

    private val _orderSearchQuery = MutableStateFlow("")
    val orderSearchQuery: StateFlow<String> = _orderSearchQuery.asStateFlow()

    private val _cartItems = MutableStateFlow<Map<String, CartItem>>(emptyMap())
    val cartItems: StateFlow<Map<String, CartItem>> = _cartItems.asStateFlow()

    private val _pendingOtpAction = MutableStateFlow<PendingOtpAction?>(null)
    val pendingOtpAction: StateFlow<PendingOtpAction?> = _pendingOtpAction.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()
    private var ttsJob: Job? = null

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun selectStore(sellerId: String) {
        if (_selectedStoreId.value != sellerId) {
            _selectedStoreId.value = sellerId
            _cartItems.value = emptyMap()
        }
    }

    fun setSelectedCategory(category: ProductCategory?) {
        _selectedCategory.value = category
    }

    fun setSelectedSubCategory(subCategory: ProductSubCategory) {
        _selectedSubCategory.value = subCategory
        if (subCategory == ProductSubCategory.PUJA_SAMAGRI) {
            _selectedCategory.value = null
        }
    }

    fun setSortOption(option: ProductSortOption) {
        _sortOption.value = option
    }

    fun setInStockOnly(enabled: Boolean) {
        _inStockOnly.value = enabled
    }

    fun clearCatalogFilters() {
        _selectedCategory.value = null
        _selectedSubCategory.value = ProductSubCategory.ALL
        _sortOption.value = ProductSortOption.FEATURED
        _inStockOnly.value = false
        _searchQuery.value = ""
    }

    fun applyPromotionalFilter(subCategory: ProductSubCategory, bannerTitle: String) {
        _selectedCategory.value = null
        _selectedSubCategory.value = subCategory
        _searchQuery.value = ""
        _actionMessage.value = "Showing ${subCategory.label} • $bannerTitle"
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setOrderHistoryFilter(filter: OrderHistoryFilter) {
        _orderHistoryFilter.value = filter
    }

    fun setOrderSearchQuery(query: String) {
        _orderSearchQuery.value = query
    }

    fun setWishlistCategory(category: ProductCategory?) {
        _wishlistCategory.value = category
    }

    fun setWishlistSearchQuery(query: String) {
        _wishlistSearchQuery.value = query
    }

    fun toggleWishlist(product: Product) {
        val store = activeStore.value
        val sellerId = store?.userId ?: product.userId
        val storeName = store?.storeName ?: "Lakshmi Store"
        val currentList = (wishlistState.value as? UiState.Success)?.data ?: emptyList()
        val existing = currentList.firstOrNull {
            it.productId == product.id && it.sellerId == sellerId
        }
        viewModelScope.launch {
            if (existing != null) {
                val res = repository.removeWishlistItem(buyerId, existing.id)
                _actionMessage.value = if (res.isSuccess) {
                    "Removed ${product.name} from wishlist."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Could not update wishlist."
                }
            } else {
                val res = repository.saveWishlistItem(
                    userId = buyerId,
                    sellerId = sellerId,
                    storeName = storeName,
                    product = product
                )
                _actionMessage.value = if (res.isSuccess) {
                    "Saved ${product.name} to wishlist."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Could not save to wishlist."
                }
            }
        }
    }

    fun removeWishlistItem(item: WishlistItem) {
        viewModelScope.launch {
            val res = repository.removeWishlistItem(buyerId, item.id)
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to remove item."
            }
        }
    }

    fun clearWishlist() {
        viewModelScope.launch {
            val res = repository.clearWishlist(buyerId)
            _actionMessage.value = if (res.isSuccess) {
                "Cleared ${res.getOrDefault(0)} saved items."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to clear wishlist."
            }
        }
    }

    fun addWishlistItemToCart(item: WishlistItem, onOpenShop: () -> Unit) {
        val currentActive = activeStore.value
        if (currentActive == null || currentActive.userId != item.sellerId) {
            selectStore(item.sellerId)
            _actionMessage.value = "Switched to ${item.storeName}. Tap Add to put ${item.name} in your basket."
            onOpenShop()
            return
        }
        val liveProducts = (productsState.value as? UiState.Success)?.data ?: emptyList()
        val liveMatch = liveProducts.firstOrNull { it.id == item.productId }
        if (liveMatch == null) {
            _actionMessage.value = "${item.name} is no longer listed in ${item.storeName}."
            return
        }
        if (!liveMatch.inStock || liveMatch.stockQty <= 0L) {
            _actionMessage.value = "${liveMatch.name} is currently out of stock."
            return
        }
        addToCart(liveMatch)
        _actionMessage.value = "Added ${liveMatch.name} to your basket."
    }

    fun addToCart(product: Product) {
        if (!product.inStock || product.stockQty <= 0L) {
            _actionMessage.value = "${product.name} is currently out of stock."
            return
        }
        val currentMap = _cartItems.value.toMutableMap()
        val existing = currentMap[product.id]
        val nextQty = (existing?.quantity ?: 0) + 1
        if (nextQty > product.stockQty) {
            _actionMessage.value = "Only ${product.stockQty} available in stock."
            return
        }
        currentMap[product.id] = CartItem(product = product, quantity = nextQty)
        _cartItems.value = currentMap
    }

    fun removeFromCart(product: Product) {
        val currentMap = _cartItems.value.toMutableMap()
        val existing = currentMap[product.id] ?: return
        if (existing.quantity <= 1) {
            currentMap.remove(product.id)
        } else {
            currentMap[product.id] = existing.copy(quantity = existing.quantity - 1)
        }
        _cartItems.value = currentMap
    }

    fun deleteFromCart(productId: String) {
        val currentMap = _cartItems.value.toMutableMap()
        if (currentMap.remove(productId) != null) {
            _cartItems.value = currentMap
        }
    }

    fun clearCart() {
        _cartItems.value = emptyMap()
    }

    fun placeOrder(
        customerName: String,
        neighborhood: String,
        lat: Double,
        lng: Double
    ) {
        val store = activeStore.value
        val items = _cartItems.value.values.toList()
        if (store == null || items.isEmpty()) {
            _actionMessage.value = "Add at least one in-stock item before placing an order."
            return
        }
        val summary = items.joinToString { "${it.quantity}x ${it.product.name}" }.take(300)
        val total = items.sumOf { it.product.priceInr * it.quantity }

        viewModelScope.launch {
            val res = repository.createOrder(
                sellerId = store.userId,
                storeName = store.storeName,
                customerName = customerName.ifBlank { buyerDisplayName },
                neighborhood = neighborhood,
                itemsSummary = summary,
                totalInr = total,
                lat = lat,
                lng = lng
            )
            if (res.isSuccess) {
                _cartItems.value = emptyMap()
                _actionMessage.value = "Order placed with ${store.storeName}!"
            } else {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to place order."
            }
        }
    }

    fun submitFeedback(authorName: String, rating: Int, category: FeedbackCategory, comment: String) {
        val store = activeStore.value ?: return
        viewModelScope.launch {
            val res = repository.submitFeedback(
                sellerId = store.userId,
                authorName = authorName.ifBlank { buyerDisplayName },
                rating = rating,
                category = category,
                comment = comment
            )
            _actionMessage.value = if (res.isSuccess) {
                "Thank you! Your review has been posted."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to post review."
            }
        }
    }

    fun requestClearPendingOrdersWithOtp() {
        _pendingOtpAction.value = PendingOtpAction(
            actionLabel = "Cancel & delete all pending buyer orders",
            isDestructive = true
        ) {
            viewModelScope.launch {
                val res = repository.deleteBuyerPendingOrders(buyerId)
                _actionMessage.value = if (res.isSuccess) {
                    "Cancelled ${res.getOrDefault(0)} pending orders."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to cancel orders."
                }
            }
        }
    }

    fun dismissOtpChallenge() {
        _pendingOtpAction.value = null
    }

    fun onOtpVerified() {
        val pending = _pendingOtpAction.value ?: return
        _pendingOtpAction.value = null
        pending.execute()
    }

    fun updateContactDetails(
        displayName: String,
        email: String,
        phoneNumber: String
    ) {
        viewModelScope.launch {
            val res = repository.updateAccountContactDetails(
                userId = buyerId,
                role = AccountRole.BUYER,
                displayName = displayName,
                email = email,
                phoneNumber = phoneNumber
            )
            _actionMessage.value = if (res.isSuccess) {
                "Contact details updated."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update contact details."
            }
        }
    }

    fun saveAddress(
        addressId: String?,
        label: String,
        recipientName: String,
        phoneNumber: String,
        streetAddress: String,
        neighborhood: String,
        landmark: String,
        makeDefault: Boolean
    ) {
        val store = activeStore.value
        val baseLat = store?.storeLat ?: 12.9716
        val baseLng = store?.storeLng ?: 77.5946
        viewModelScope.launch {
            val res = repository.saveBuyerAddress(
                userId = buyerId,
                addressId = addressId,
                label = label,
                recipientName = recipientName,
                phoneNumber = phoneNumber,
                streetAddress = streetAddress,
                neighborhood = neighborhood,
                landmark = landmark,
                lat = baseLat + 0.008,
                lng = baseLng + 0.008,
                makeDefault = makeDefault
            )
            _actionMessage.value = if (res.isSuccess) {
                if (addressId == null) "Delivery address saved." else "Delivery address updated."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save address."
            }
        }
    }

    fun setDefaultAddress(addressId: String) {
        viewModelScope.launch {
            val res = repository.setDefaultBuyerAddress(buyerId, addressId)
            _actionMessage.value = if (res.isSuccess) {
                "Default delivery address updated."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update default address."
            }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            val res = repository.deleteBuyerAddress(buyerId, addressId)
            _actionMessage.value = if (res.isSuccess) {
                "Delivery address removed."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to remove address."
            }
        }
    }

    fun savePaymentMethod(
        methodId: String?,
        methodType: PaymentMethodType,
        displayLabel: String,
        handleOrHint: String,
        makeDefault: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.savePreferredPaymentMethod(
                userId = buyerId,
                methodId = methodId,
                methodType = methodType,
                displayLabel = displayLabel,
                handleOrHint = handleOrHint,
                makeDefault = makeDefault
            )
            _actionMessage.value = if (res.isSuccess) {
                if (methodId == null) "Payment preference saved." else "Payment preference updated."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to save payment preference."
            }
        }
    }

    fun setDefaultPaymentMethod(methodId: String) {
        viewModelScope.launch {
            val res = repository.setDefaultPreferredPaymentMethod(buyerId, methodId)
            _actionMessage.value = if (res.isSuccess) {
                "Default payment method updated."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to update default payment."
            }
        }
    }

    fun deletePaymentMethod(methodId: String) {
        viewModelScope.launch {
            val res = repository.deletePreferredPaymentMethod(buyerId, methodId)
            _actionMessage.value = if (res.isSuccess) {
                "Payment preference removed."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to remove payment preference."
            }
        }
    }

    fun speakStoreStockSummary() {
        val store = activeStore.value
        val products = (productsState.value as? UiState.Success)?.data ?: emptyList()
        val present = products.filter { it.inStock }
        val summary = buildString {
            append(store?.storeName ?: "Lakshmi")
            append(". ")
            append("${present.size} groceries and puja items are available right now, including ")
            append(present.take(4).joinToString { "${it.name} for ${it.priceInr.toInt()} rupees" }.ifBlank { "no items" })
            append(".")
        }
        ttsJob?.cancel()
        ttsJob = viewModelScope.launch {
            _isTtsSpeaking.value = true
            try {
                aiService.speakTextWithGeminiTts(summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = "Audio error: ${e.localizedMessage ?: "Unable to read catalog."}"
            } finally {
                _isTtsSpeaking.value = false
            }
        }
    }

    fun stopSpeaking() {
        ttsJob?.cancel()
        aiService.stopTts()
        _isTtsSpeaking.value = false
    }

    override fun onCleared() {
        super.onCleared()
        aiService.stopTts()
    }
}
