package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAssistantService
import com.example.data.SellerRepository
import com.example.model.AuditLog
import com.example.model.ChatMessage
import com.example.model.FeedbackCategory
import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.SellerOrder
import com.example.model.SmartFillResult
import com.example.model.StoreConfig
import com.example.model.StoreFeedback
import com.example.model.UiState
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.util.UUID

enum class StockFilter(val label: String) {
    ALL("All Items"),
    PRESENT_ONLY("Present Stock"),
    OUT_OF_STOCK_ONLY("Out of Stock"),
    LOW_STOCK_ONLY("Low Stock Alerts")
}

data class PendingOtpAction(
    val actionLabel: String,
    val isDestructive: Boolean,
    val execute: () -> Unit
)

class SellerViewModel(
    private val repository: SellerRepository,
    private val aiService: GeminiAssistantService,
    val sellerId: String
) : ViewModel() {

    private companion object {
        const val TAG = "SellerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val OTP_SESSION_DURATION_MS = 120_000L
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

    val productsState: StateFlow<UiState<List<Product>>> =
        repository.observeProducts(sellerId).toUiState("Failed to load inventory")

    val ordersState: StateFlow<UiState<List<SellerOrder>>> =
        repository.observeSellerOrders(sellerId).toUiState("Failed to load orders")

    val storeConfigState: StateFlow<UiState<StoreConfig?>> =
        repository.observeStoreConfig(sellerId).toUiState("Failed to load store config")

    val feedbackState: StateFlow<UiState<List<StoreFeedback>>> =
        repository.observeFeedback(sellerId).toUiState("Failed to load feedback")

    val auditLogsState: StateFlow<UiState<List<AuditLog>>> =
        repository.observeAuditLogs(sellerId).toUiState("Failed to load audit trail")

    // Dashboard visibility state (defaults to End-User View)
    private val _isSellerEditMode = MutableStateFlow(false)
    val isSellerEditMode: StateFlow<Boolean> = _isSellerEditMode.asStateFlow()

    private val _stockFilter = MutableStateFlow(StockFilter.ALL)
    val stockFilter: StateFlow<StockFilter> = _stockFilter.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ProductCategory?>(null)
    val selectedCategory: StateFlow<ProductCategory?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // OTP challenge & 2-min batch session for non-delete changes
    private val _pendingOtpAction = MutableStateFlow<PendingOtpAction?>(null)
    val pendingOtpAction: StateFlow<PendingOtpAction?> = _pendingOtpAction.asStateFlow()

    private val _otpSessionExpiresAtMillis = MutableStateFlow(0L)
    val otpSessionExpiresAtMillis: StateFlow<Long> = _otpSessionExpiresAtMillis.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    // AI Chat state
    private val _chatMessages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = "welcome",
                isUser = false,
                text = "Namaste! I am Lakshmi Sahayak. Ask me about Present Stock vs. Out-of-Stock items, festival puja checklists, pricing, or restock priorities."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatStreaming = MutableStateFlow(false)
    val isChatStreaming: StateFlow<Boolean> = _isChatStreaming.asStateFlow()

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()
    private var ttsJob: Job? = null

    private val _isLiveVoiceActive = MutableStateFlow(false)
    val isLiveVoiceActive: StateFlow<Boolean> = _isLiveVoiceActive.asStateFlow()

    private val _liveVoiceStatus = MutableStateFlow<String?>(null)
    val liveVoiceStatus: StateFlow<String?> = _liveVoiceStatus.asStateFlow()
    private var liveVoiceJob: Job? = null

    private val _insightsState = MutableStateFlow<UiState<String>?>(null)
    val insightsState: StateFlow<UiState<String>?> = _insightsState.asStateFlow()

    private val _smartFillState = MutableStateFlow<UiState<SmartFillResult>?>(null)
    val smartFillState: StateFlow<UiState<SmartFillResult>?> = _smartFillState.asStateFlow()

    private val _broadcastingOrderId = MutableStateFlow<String?>(null)
    val broadcastingOrderId: StateFlow<String?> = _broadcastingOrderId.asStateFlow()

    private var activeLocationClient: FusedLocationProviderClient? = null
    private var activeLocationCallback: LocationCallback? = null

    private fun currentProducts(): List<Product> =
        (productsState.value as? UiState.Success)?.data ?: emptyList()

    private fun currentOrders(): List<SellerOrder> =
        (ordersState.value as? UiState.Success)?.data ?: emptyList()

    private fun currentStoreConfig(): StoreConfig? =
        (storeConfigState.value as? UiState.Success)?.data

    private fun currentFeedback(): List<StoreFeedback> =
        (feedbackState.value as? UiState.Success)?.data ?: emptyList()

    fun isOtpProtectionEnabled(): Boolean = currentStoreConfig()?.otpProtectionEnabled ?: true

    fun isDeleteProtectionEnabled(): Boolean = currentStoreConfig()?.deleteProtectionEnabled ?: true

    fun isShowOutOfStockEnabled(): Boolean = currentStoreConfig()?.showOutOfStock ?: true

    fun lowStockThreshold(): Long = (currentStoreConfig()?.lowStockThreshold ?: 5L).coerceIn(1L, 1000L)

    fun isLowStockAlertsEnabled(): Boolean = currentStoreConfig()?.lowStockAlertsEnabled ?: true

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun showMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun setSellerEditMode(enabled: Boolean) {
        _isSellerEditMode.value = enabled
    }

    fun setStockFilter(filter: StockFilter) {
        _stockFilter.value = filter
    }

    fun setSelectedCategory(category: ProductCategory?) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun lockOtpSessionNow() {
        _otpSessionExpiresAtMillis.value = 0L
        _actionMessage.value = "OTP session locked. Next delete or change will require a fresh OTP."
    }

    fun dismissOtpChallenge() {
        _pendingOtpAction.value = null
    }

    fun onOtpVerified(keepSessionForTwoMinutes: Boolean) {
        val pending = _pendingOtpAction.value ?: return
        _pendingOtpAction.value = null
        if (keepSessionForTwoMinutes && !pending.isDestructive) {
            _otpSessionExpiresAtMillis.value = System.currentTimeMillis() + OTP_SESSION_DURATION_MS
        }
        viewModelScope.launch {
            repository.appendAuditLog(
                sellerId,
                "OTP_VERIFIED",
                "Verified OTP for: ${pending.actionLabel}"
            )
        }
        pending.execute()
    }

    private fun runProtected(
        actionLabel: String,
        isDestructive: Boolean,
        alwaysRequireFreshOtp: Boolean = isDestructive,
        block: () -> Unit
    ) {
        val otpRequired = isOtpProtectionEnabled()
        if (!otpRequired) {
            block()
            return
        }
        val sessionValid = !alwaysRequireFreshOtp &&
            System.currentTimeMillis() < _otpSessionExpiresAtMillis.value
        if (sessionValid) {
            block()
        } else {
            _pendingOtpAction.value = PendingOtpAction(
                actionLabel = actionLabel,
                isDestructive = isDestructive,
                execute = block
            )
        }
    }

    fun setShowOutOfStock(visible: Boolean) {
        if (!visible && _stockFilter.value == StockFilter.OUT_OF_STOCK_ONLY) {
            _stockFilter.value = StockFilter.PRESENT_ONLY
        }
        runProtected(
            actionLabel = if (visible) "Show out-of-stock items to buyers" else "Hide out-of-stock items when empty",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val existing = currentStoreConfig()
                val res = repository.saveStoreConfig(
                    sellerId = sellerId,
                    storeName = existing?.storeName ?: "Lakshmi Puja, Fancy & Grocery",
                    address = existing?.address ?: "100 Feet Rd, Indiranagar, Bengaluru",
                    storeLat = existing?.storeLat ?: 12.9716,
                    storeLng = existing?.storeLng ?: 77.5946,
                    deliveryRadiusKm = existing?.deliveryRadiusKm ?: 5.0,
                    showOutOfStock = visible,
                    deleteProtectionEnabled = existing?.deleteProtectionEnabled ?: true,
                    otpProtectionEnabled = existing?.otpProtectionEnabled ?: true,
                    lowStockThreshold = existing?.lowStockThreshold ?: 5L,
                    lowStockAlertsEnabled = existing?.lowStockAlertsEnabled ?: true,
                    alreadyExists = existing != null
                )
                _actionMessage.value = if (res.isSuccess) {
                    if (visible) "Out-of-stock items are now visible." else "Out-of-stock items hidden."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to update visibility setting."
                }
            }
        }
    }

    fun updateLowStockSettings(threshold: Long, alertsEnabled: Boolean) {
        val safeThreshold = threshold.coerceIn(1L, 1000L)
        runProtected(
            actionLabel = "Update low-stock alert threshold to ≤ $safeThreshold units (${if (alertsEnabled) "Alerts ON" else "Alerts OFF"})",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val existing = currentStoreConfig()
                val res = repository.saveStoreConfig(
                    sellerId = sellerId,
                    storeName = existing?.storeName ?: "Lakshmi Puja, Fancy & Grocery",
                    address = existing?.address ?: "100 Feet Rd, Indiranagar, Bengaluru",
                    storeLat = existing?.storeLat ?: 12.9716,
                    storeLng = existing?.storeLng ?: 77.5946,
                    deliveryRadiusKm = existing?.deliveryRadiusKm ?: 5.0,
                    showOutOfStock = existing?.showOutOfStock ?: true,
                    deleteProtectionEnabled = existing?.deleteProtectionEnabled ?: true,
                    otpProtectionEnabled = existing?.otpProtectionEnabled ?: true,
                    lowStockThreshold = safeThreshold,
                    lowStockAlertsEnabled = alertsEnabled,
                    alreadyExists = existing != null
                )
                if (res.isSuccess) {
                    repository.appendAuditLog(
                        sellerId,
                        "LOW_STOCK_THRESHOLD",
                        "Low-stock alert set to ≤ $safeThreshold units (${if (alertsEnabled) "Enabled" else "Paused"})"
                    )
                    _actionMessage.value = if (alertsEnabled) {
                        "Low-stock alerts active for items ≤ $safeThreshold units."
                    } else {
                        "Low-stock push alerts paused (threshold ≤ $safeThreshold)."
                    }
                } else {
                    _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to save alert threshold."
                }
            }
        }
    }

    fun updateSecuritySettings(
        deleteProtectionEnabled: Boolean,
        otpProtectionEnabled: Boolean
    ) {
        val label = buildString {
            append("Update security locks: Delete Lock=")
            append(if (deleteProtectionEnabled) "ON" else "OFF")
            append(", OTP Guard=")
            append(if (otpProtectionEnabled) "ON" else "OFF")
        }
        // Always require fresh OTP when weakening or changing security locks (if OTP is currently ON)
        runProtected(
            actionLabel = label,
            isDestructive = !deleteProtectionEnabled || !otpProtectionEnabled,
            alwaysRequireFreshOtp = true
        ) {
            viewModelScope.launch {
                val existing = currentStoreConfig()
                val res = repository.saveStoreConfig(
                    sellerId = sellerId,
                    storeName = existing?.storeName ?: "Lakshmi Puja, Fancy & Grocery",
                    address = existing?.address ?: "100 Feet Rd, Indiranagar, Bengaluru",
                    storeLat = existing?.storeLat ?: 12.9716,
                    storeLng = existing?.storeLng ?: 77.5946,
                    deliveryRadiusKm = existing?.deliveryRadiusKm ?: 5.0,
                    showOutOfStock = existing?.showOutOfStock ?: true,
                    deleteProtectionEnabled = deleteProtectionEnabled,
                    otpProtectionEnabled = otpProtectionEnabled,
                    lowStockThreshold = existing?.lowStockThreshold ?: 5L,
                    lowStockAlertsEnabled = existing?.lowStockAlertsEnabled ?: true,
                    alreadyExists = existing != null
                )
                if (res.isSuccess) {
                    repository.appendAuditLog(sellerId, "LOCK_CHANGE", label)
                    if (!otpProtectionEnabled) {
                        _otpSessionExpiresAtMillis.value = 0L
                    }
                    _actionMessage.value = "Security settings updated."
                } else {
                    _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update security settings."
                }
            }
        }
    }

    fun seedStarterCatalog() {
        viewModelScope.launch {
            val result = repository.seedStarterCatalog(sellerId)
            _actionMessage.value = if (result.isSuccess) {
                "Loaded grocery & puja starter catalog!"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed to load starter catalog."
            }
        }
    }

    fun saveProduct(
        editingId: String?,
        name: String,
        category: ProductCategory,
        unit: String,
        priceInr: Double,
        stockQty: Long,
        inStock: Boolean,
        description: String,
        photoUri: String,
        imageTag: String
    ) {
        val performSave = {
            viewModelScope.launch {
                repository.ensureDefaultStoreConfig(sellerId)
                val res = if (editingId.isNullOrBlank()) {
                    repository.createProduct(
                        sellerId = sellerId,
                        name = name,
                        category = category,
                        unit = unit,
                        priceInr = priceInr,
                        stockQty = stockQty,
                        inStock = inStock,
                        description = description,
                        photoUri = photoUri,
                        imageTag = imageTag
                    )
                } else {
                    repository.updateProduct(
                        sellerId = sellerId,
                        productId = editingId,
                        name = name,
                        category = category,
                        unit = unit,
                        priceInr = priceInr,
                        stockQty = stockQty,
                        inStock = inStock,
                        description = description,
                        photoUri = photoUri,
                        imageTag = imageTag
                    )
                }
                _actionMessage.value = if (res.isSuccess) {
                    if (editingId.isNullOrBlank()) "Item added to inventory." else "Item updated."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to save item."
                }
            }
            Unit
        }

        if (editingId.isNullOrBlank()) {
            performSave()
        } else {
            runProtected(
                actionLabel = "Modify item '$name' (qty $stockQty, ₹${priceInr.toInt()})",
                isDestructive = false
            ) { performSave() }
        }
    }

    fun toggleProductStock(product: Product, newInStock: Boolean) {
        val targetQty = if (newInStock) maxOf(product.stockQty, 5L) else 0L
        runProtected(
            actionLabel = "Change '${product.name}' to ${if (newInStock) "Present Stock" else "Out of Stock"}",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val res = repository.toggleStockStatus(sellerId, product, newInStock, targetQty)
                _actionMessage.value = if (res.isSuccess) {
                    if (newInStock) "${product.name} marked Present Stock" else "${product.name} marked Out of Stock"
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to update stock visibility."
                }
            }
        }
    }

    fun adjustStockQty(product: Product, delta: Long) {
        val updatedQty = (product.stockQty + delta).coerceIn(0L, 1_000_000L)
        val updatedInStock = updatedQty > 0L
        val verb = if (delta >= 0) "+$delta" else "$delta"
        runProtected(
            actionLabel = "Adjust '${product.name}' inventory ($verb -> $updatedQty)",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val res = repository.toggleStockStatus(sellerId, product, updatedInStock, updatedQty)
                if (res.isFailure) {
                    _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update quantity."
                }
            }
        }
    }

    fun setExactStockQty(product: Product, newQty: Long) {
        val safeQty = newQty.coerceIn(0L, 1_000_000L)
        val updatedInStock = safeQty > 0L
        runProtected(
            actionLabel = "Set '${product.name}' inventory count to $safeQty",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val res = repository.toggleStockStatus(sellerId, product, updatedInStock, safeQty)
                _actionMessage.value = if (res.isSuccess) {
                    "${product.name} updated to $safeQty (${product.unit})."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to update quantity."
                }
            }
        }
    }

    fun deleteProduct(product: Product) {
        if (isDeleteProtectionEnabled()) {
            _actionMessage.value = "Anti-Theft Delete Lock is ON. Unlock it in Safety & Backup before deleting items."
            return
        }
        runProtected(
            actionLabel = "Permanently delete '${product.name}' from catalog",
            isDestructive = true,
            alwaysRequireFreshOtp = true
        ) {
            viewModelScope.launch {
                val res = repository.deleteProduct(sellerId, product)
                _actionMessage.value = if (res.isSuccess) {
                    "Removed ${product.name}."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Delete blocked by security rules."
                }
            }
        }
    }

    fun deleteAllSellerData() {
        if (isDeleteProtectionEnabled()) {
            _actionMessage.value = "Turn OFF Anti-Theft Delete Lock first before deleting store data."
            return
        }
        runProtected(
            actionLabel = "Erase all store products, reviews, orders, and storefront config",
            isDestructive = true,
            alwaysRequireFreshOtp = true
        ) {
            viewModelScope.launch {
                val res = repository.deleteAllSellerData(sellerId)
                _actionMessage.value = if (res.isSuccess) {
                    "Deleted ${res.getOrDefault(0)} store records safely."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to erase store data."
                }
            }
        }
    }

    fun createSampleOrder() {
        viewModelScope.launch {
            repository.ensureDefaultStoreConfig(sellerId)
            val store = currentStoreConfig()
            val baseLat = store?.storeLat ?: 12.9716
            val baseLng = store?.storeLng ?: 77.5946
            val offsetLat = baseLat + ((-15..15).random() / 1000.0)
            val offsetLng = baseLng + ((-15..15).random() / 1000.0)
            val inStockItems = currentProducts().filter { it.inStock }.take(2)
            val summary = if (inStockItems.isNotEmpty()) {
                inStockItems.joinToString { "1x ${it.name}" }
            } else {
                "1x Brass Diya Pair, 1x Toor Dal"
            }
            val total = if (inStockItems.isNotEmpty()) {
                inStockItems.sumOf { it.priceInr }
            } else {
                405.0
            }
            val res = repository.createOrder(
                sellerId = sellerId,
                storeName = store?.storeName ?: "Lakshmi Puja, Fancy & Grocery",
                customerName = listOf("Lakshmi Narayan", "Siddharth Verma", "Deepa Kulkarni", "Arjun Nair").random(),
                neighborhood = listOf("Indiranagar", "Koramangala", "Jayanagar", "Malleshwaram").random(),
                itemsSummary = summary,
                totalInr = total,
                lat = offsetLat,
                lng = offsetLng
            )
            _actionMessage.value = if (res.isSuccess) {
                "New customer order logged!"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to create order."
            }
        }
    }

    fun advanceOrderStatus(order: SellerOrder) {
        runProtected(
            actionLabel = "Advance order for ${order.customerName} to ${order.statusEnum().next()?.label ?: "Next"}",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val store = currentStoreConfig()
                val res = repository.advanceOrderStatus(
                    order = order,
                    storeLat = store?.storeLat,
                    storeLng = store?.storeLng
                )
                _actionMessage.value = if (res.isSuccess) {
                    "Order updated for ${order.customerName}."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to update order."
                }
            }
        }
    }

    fun updateOrderRiderLocation(order: SellerOrder, riderLat: Double, riderLng: Double) {
        viewModelScope.launch {
            val res = repository.updateOrderRiderLocation(
                order = order,
                riderLat = riderLat,
                riderLng = riderLng
            )
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Failed to update live location."
            } else {
                _actionMessage.value = "Updated live delivery pin for ${order.customerName}."
            }
        }
    }

    fun moveRiderStepCloser(order: SellerOrder) {
        if (order.statusEnum() == OrderStatus.DELIVERED) return
        val store = currentStoreConfig()
        val currentLat = order.riderLat ?: store?.storeLat ?: order.lat
        val currentLng = order.riderLng ?: store?.storeLng ?: order.lng
        val nextLat = currentLat + (order.lat - currentLat) * 0.35
        val nextLng = currentLng + (order.lng - currentLng) * 0.35
        updateOrderRiderLocation(order, nextLat, nextLng)
    }

    @SuppressLint("MissingPermission")
    fun startLiveLocationBroadcast(context: Context, order: SellerOrder) {
        stopLiveLocationBroadcast()
        if (order.statusEnum() == OrderStatus.DELIVERED) {
            _actionMessage.value = "Order is already delivered."
            return
        }
        val appContext = context.applicationContext
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        activeLocationClient = client
        _broadcastingOrderId.value = order.id
        _actionMessage.value = "Broadcasting live GPS location for #${order.id.takeLast(6)}..."

        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null && _broadcastingOrderId.value == order.id) {
                viewModelScope.launch {
                    repository.updateOrderRiderLocation(order, loc.latitude, loc.longitude)
                }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val latestOrder = currentOrders().firstOrNull { it.id == order.id } ?: order
                if (latestOrder.statusEnum() == OrderStatus.DELIVERED) {
                    stopLiveLocationBroadcast()
                    return
                }
                viewModelScope.launch {
                    repository.updateOrderRiderLocation(latestOrder, loc.latitude, loc.longitude)
                }
            }
        }
        activeLocationCallback = callback
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request live GPS updates", e)
            _broadcastingOrderId.value = null
            _actionMessage.value = e.localizedMessage ?: "Unable to access live GPS."
        }
    }

    fun stopLiveLocationBroadcast(notifyUser: Boolean = false) {
        val client = activeLocationClient
        val callback = activeLocationCallback
        if (client != null && callback != null) {
            client.removeLocationUpdates(callback)
        }
        activeLocationClient = null
        activeLocationCallback = null
        if (_broadcastingOrderId.value != null && notifyUser) {
            _actionMessage.value = "Stopped live GPS broadcast."
        }
        _broadcastingOrderId.value = null
    }

    fun confirmOrderDelivery(order: SellerOrder, method: String, proofUri: String = "") {
        if (_broadcastingOrderId.value == order.id) {
            stopLiveLocationBroadcast()
        }
        viewModelScope.launch {
            val res = repository.confirmOrderDelivery(order = order, method = method, proofUri = proofUri)
            _actionMessage.value = if (res.isSuccess) {
                when (method) {
                    "PHOTO" -> "Delivery confirmed via Photograph!"
                    "QR" -> "Delivery confirmed via QR Code!"
                    else -> "Delivery confirmed via Buyer OTP!"
                }
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to confirm delivery."
            }
        }
    }

    fun saveStoreConfig(
        storeName: String,
        address: String,
        lat: Double,
        lng: Double,
        radiusKm: Double
    ) {
        runProtected(
            actionLabel = "Update store map location & ${radiusKm.toInt()} km zone",
            isDestructive = false
        ) {
            viewModelScope.launch {
                val existing = currentStoreConfig()
                val res = repository.saveStoreConfig(
                    sellerId = sellerId,
                    storeName = storeName,
                    address = address,
                    storeLat = lat,
                    storeLng = lng,
                    deliveryRadiusKm = radiusKm,
                    showOutOfStock = existing?.showOutOfStock ?: true,
                    deleteProtectionEnabled = existing?.deleteProtectionEnabled ?: true,
                    otpProtectionEnabled = existing?.otpProtectionEnabled ?: true,
                    lowStockThreshold = existing?.lowStockThreshold ?: 5L,
                    lowStockAlertsEnabled = existing?.lowStockAlertsEnabled ?: true,
                    alreadyExists = existing != null
                )
                _actionMessage.value = if (res.isSuccess) {
                    "Store map & delivery zone saved."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to save store config."
                }
            }
        }
    }

    fun submitFeedback(authorName: String, rating: Int, category: FeedbackCategory, comment: String) {
        viewModelScope.launch {
            val res = repository.submitFeedback(sellerId, authorName, rating, category, comment)
            _actionMessage.value = if (res.isSuccess) {
                "Thank you! Rating & feedback saved."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to submit feedback."
            }
        }
    }

    fun deleteFeedback(feedbackId: String) {
        if (isDeleteProtectionEnabled()) {
            _actionMessage.value = "Anti-Theft Delete Lock is ON. Unlock it in Safety & Backup first."
            return
        }
        runProtected(
            actionLabel = "Delete customer feedback review",
            isDestructive = true,
            alwaysRequireFreshOtp = true
        ) {
            viewModelScope.launch {
                val res = repository.deleteFeedback(sellerId, feedbackId)
                _actionMessage.value = if (res.isSuccess) {
                    "Review deleted."
                } else {
                    res.exceptionOrNull()?.localizedMessage ?: "Failed to delete feedback."
                }
            }
        }
    }

    fun buildBackupJson(): String =
        repository.exportBackupJson(
            products = currentProducts(),
            orders = currentOrders(),
            storeConfig = currentStoreConfig(),
            feedbackList = currentFeedback()
        )

    fun exportBackupToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val json = buildBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open backup destination.")
                }
                repository.appendAuditLog(sellerId, "BACKUP_EXPORT", "Exported SHA-256 verified JSON backup")
                _actionMessage.value = "Verified SHA-256 backup saved!"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _actionMessage.value = e.localizedMessage ?: "Backup export failed."
            }
        }
    }

    fun restoreBackupFromUri(context: Context, uri: Uri) {
        runProtected(
            actionLabel = "Restore catalog items from JSON backup",
            isDestructive = false
        ) {
            viewModelScope.launch {
                try {
                    val jsonText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: throw IllegalStateException("Could not read selected backup file.")
                    }
                    val res = repository.restoreFromBackupJson(
                        sellerId = sellerId,
                        jsonText = jsonText,
                        hasExistingStoreConfig = currentStoreConfig() != null
                    )
                    _actionMessage.value = if (res.isSuccess) {
                        "Restored ${res.getOrDefault(0)} verified items from backup!"
                    } else {
                        res.exceptionOrNull()?.localizedMessage ?: "Restore failed."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _actionMessage.value = e.localizedMessage ?: "Failed to read backup file."
                }
            }
        }
    }

    fun runSmartFill(itemName: String) {
        if (itemName.isBlank()) {
            _actionMessage.value = "Enter an item name first to use AI Smart Fill."
            return
        }
        viewModelScope.launch {
            _smartFillState.value = UiState.Loading
            try {
                val filled = aiService.smartFillProduct(itemName)
                _smartFillState.value = UiState.Success(filled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Smart fill failed", e)
                _smartFillState.value = UiState.Error(e.localizedMessage ?: "AI Smart Fill failed.")
            }
        }
    }

    fun clearSmartFill() {
        _smartFillState.value = null
    }

    fun generateRestockInsights() {
        viewModelScope.launch {
            _insightsState.value = UiState.Loading
            try {
                val text = aiService.generateRestockInsights(currentProducts())
                _insightsState.value = UiState.Success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Insights failed", e)
                _insightsState.value = UiState.Error(e.localizedMessage ?: "Failed to generate insights.")
            }
        }
    }

    fun sendChatMessage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty() || _isChatStreaming.value) return

        val userMsg = ChatMessage(id = UUID.randomUUID().toString(), isUser = true, text = clean)
        val botMsgId = UUID.randomUUID().toString()
        _chatMessages.value = _chatMessages.value + userMsg + ChatMessage(
            id = botMsgId,
            isUser = false,
            text = "",
            isStreaming = true
        )
        _isChatStreaming.value = true

        val products = currentProducts()
        val present = products.filter { it.inStock }.joinToString { "${it.name} (${it.stockQty} ${it.unit}, Rs.${it.priceInr.toInt()})" }
        val out = products.filter { !it.inStock }.joinToString { it.name }
        val contextSummary = "Present Stock: ${present.ifBlank { "None" }}\nOut of Stock: ${out.ifBlank { "None" }}"

        viewModelScope.launch {
            try {
                aiService.sendChatStream(clean, contextSummary).collect { partial ->
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == botMsgId) it.copy(text = partial, isStreaming = true) else it
                    }
                }
                _chatMessages.value = _chatMessages.value.map {
                    if (it.id == botMsgId) it.copy(isStreaming = false) else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
                _chatMessages.value = _chatMessages.value.map {
                    if (it.id == botMsgId) {
                        it.copy(
                            text = "Error: ${e.localizedMessage ?: "Unable to reach Gemini right now."}",
                            isStreaming = false
                        )
                    } else it
                }
            } finally {
                _isChatStreaming.value = false
            }
        }
    }

    fun resetChat() {
        aiService.resetChat()
        _chatMessages.value = listOf(
            ChatMessage(
                id = "welcome",
                isUser = false,
                text = "Started a fresh conversation! Ask me about Present Stock, Out-of-Stock restocks, or puja item combinations."
            )
        )
    }

    fun speakText(text: String) {
        ttsJob?.cancel()
        ttsJob = viewModelScope.launch {
            _isTtsSpeaking.value = true
            try {
                aiService.speakTextWithGeminiTts(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS failed", e)
                _actionMessage.value = "TTS Error: ${e.localizedMessage ?: "Could not synthesize speech."}"
            } finally {
                _isTtsSpeaking.value = false
            }
        }
    }

    fun speakStockSummary() {
        val products = currentProducts()
        val present = products.filter { it.inStock }
        val out = products.filter { !it.inStock }
        val summary = buildString {
            append("Lakshmi Stock Summary. ")
            append("${present.size} items are currently in stock, including ")
            append(present.take(4).joinToString { it.name }.ifBlank { "none" })
            append(". ")
            append("${out.size} items are currently out of stock")
            if (out.isNotEmpty()) {
                append(", including ${out.take(3).joinToString { it.name }}")
            }
            append(".")
        }
        speakText(summary)
    }

    fun stopSpeaking() {
        ttsJob?.cancel()
        aiService.stopTts()
        _isTtsSpeaking.value = false
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startLiveVoice() {
        if (_isLiveVoiceActive.value) return
        liveVoiceJob?.cancel()
        liveVoiceJob = viewModelScope.launch {
            _isLiveVoiceActive.value = true
            _liveVoiceStatus.value = "Connecting to Gemini Live..."
            try {
                _liveVoiceStatus.value = "Listening — speak about your grocery or puja stock"
                aiService.startLiveVoiceSession(
                    getStockSnapshot = { currentProducts() },
                    onToggleItemStock = { itemName, inStock ->
                        val match = currentProducts().firstOrNull {
                            it.name.contains(itemName, ignoreCase = true)
                        }
                        if (match != null) {
                            toggleProductStock(match, inStock)
                            "Requested update for ${match.name} to ${if (inStock) "Present Stock" else "Out of Stock"}."
                        } else {
                            "Could not find an item matching $itemName."
                        }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live voice error", e)
                _liveVoiceStatus.value = "Live voice error: ${e.localizedMessage ?: "Connection failed"}"
                _isLiveVoiceActive.value = false
            }
        }
    }

    fun stopLiveVoice() {
        viewModelScope.launch {
            liveVoiceJob?.cancel()
            aiService.stopLiveVoiceSession()
            _isLiveVoiceActive.value = false
            _liveVoiceStatus.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveLocationBroadcast()
        aiService.stopTts()
    }
}
