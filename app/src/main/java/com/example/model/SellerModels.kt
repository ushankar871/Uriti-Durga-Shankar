package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AccountRole(val title: String, val badge: String) {
    SELLER("Seller Account", "Seller App"),
    BUYER("Buyer Account", "Buyer App");

    companion object {
        fun fromRaw(raw: String?): AccountRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: SELLER
    }
}

@Keep
@IgnoreExtraProperties
data class AccountProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = AccountRole.SELLER.name,
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): AccountRole = AccountRole.fromRaw(role)
}

enum class ProductCategory(val displayName: String) {
    GROCERY("Groceries"),
    PUJA("Puja Needs"),
    FANCY("Fancy Items");

    companion object {
        fun fromRaw(raw: String?): ProductCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GROCERY
    }
}

enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    PACKED("Packed"),
    OUT_FOR_DELIVERY("Out for Delivery"),
    DELIVERED("Delivered");

    fun next(): OrderStatus? = when (this) {
        PENDING -> PACKED
        PACKED -> OUT_FOR_DELIVERY
        OUT_FOR_DELIVERY -> DELIVERED
        DELIVERED -> null
    }

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = ProductCategory.GROCERY.name,
    val unit: String = "",
    val priceInr: Double = 0.0,
    val stockQty: Long = 0L,
    val inStock: Boolean = true,
    val description: String = "",
    val photoUri: String = "",
    val imageTag: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): ProductCategory = ProductCategory.fromRaw(category)

    fun isLowStock(threshold: Long): Boolean = !inStock || stockQty <= threshold

    fun isPresentButLow(threshold: Long): Boolean = inStock && stockQty in 1L..threshold
}

@Keep
@IgnoreExtraProperties
data class SellerOrder(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val buyerId: String = "",
    val storeName: String = "",
    val customerName: String = "",
    val neighborhood: String = "",
    val itemsSummary: String = "",
    val totalInr: Double = 0.0,
    val status: String = OrderStatus.PENDING.name,
    val lat: Double = 12.9716,
    val lng: Double = 77.5946,
    val deliveryOtp: String = "",
    val deliveryMethod: String = "",
    val deliveryProofUri: String = "",
    val riderLat: Double? = null,
    val riderLng: Double? = null,
    val riderUpdatedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): OrderStatus = OrderStatus.fromRaw(status)

    fun hasLiveRiderCoordinates(): Boolean = riderLat != null && riderLng != null
}

@Keep
@IgnoreExtraProperties
data class StoreConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val storeName: String = "Lakshmi Puja, Fancy & Grocery",
    val address: String = "100 Feet Rd, Indiranagar, Bengaluru",
    val storeLat: Double = 12.9716,
    val storeLng: Double = 77.5946,
    val deliveryRadiusKm: Double = 5.0,
    val showOutOfStock: Boolean = true,
    val deleteProtectionEnabled: Boolean = true,
    val otpProtectionEnabled: Boolean = true,
    val lowStockThreshold: Long = 5L,
    val lowStockAlertsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false
)

data class SmartFillResult(
    val category: ProductCategory,
    val unit: String,
    val priceInr: Double,
    val description: String,
    val imageTag: String
)

enum class FeedbackCategory(val label: String) {
    GENERAL("Overall"),
    GROCERY("Groceries"),
    PUJA("Puja Needs"),
    FANCY("Fancy Items"),
    DELIVERY("Delivery");

    companion object {
        fun fromRaw(raw: String?): FeedbackCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERAL
    }
}

@Keep
@IgnoreExtraProperties
data class StoreFeedback(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val rating: Long = 5L,
    val category: String = FeedbackCategory.GENERAL.name,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun categoryEnum(): FeedbackCategory = FeedbackCategory.fromRaw(category)
}

data class CartItem(
    val product: Product,
    val quantity: Int
)

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val sellerId: String = "",
    val action: String = "",
    val summary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class ProductSubCategory(val label: String) {
    ALL("All"),
    FRESH_PRODUCE("Fresh Produce"),
    PUJA_SAMAGRI("Puja Samagri"),
    FANCY_ITEMS("Fancy Items"),
    PERSONAL_CARE("Personal Care"),
    PANTRY_STAPLES("Pantry & Staples");

    companion object {
        val filterableEntries = listOf(ALL, FRESH_PRODUCE, PUJA_SAMAGRI, FANCY_ITEMS, PERSONAL_CARE, PANTRY_STAPLES)
    }
}

enum class ProductSortOption(val label: String) {
    FEATURED("Featured"),
    PRICE_LOW_HIGH("Price: Low to High"),
    PRICE_HIGH_LOW("Price: High to Low"),
    NAME_A_Z("Name: A–Z"),
    IN_STOCK_FIRST("In Stock First")
}

fun Product.inferSubCategory(): ProductSubCategory {
    val haystack = "${name.lowercase()} ${description.lowercase()} ${imageTag.lowercase()}"
    val personalCareKeywords = listOf(
        "personal", "soap", "shampoo", "hair oil", "coconut oil", "neem", "face",
        "skin", "tooth", "paste", "hygiene", "sanitizer", "lotion", "cream", "bath",
        "tulsi", "ubtan", "comb", "personal_care"
    )
    if (personalCareKeywords.any { haystack.contains(it) }) {
        return ProductSubCategory.PERSONAL_CARE
    }

    val produceKeywords = listOf(
        "produce", "palak", "spinach", "tomato", "vegetable", "fruit", "fresh farm",
        "coriander", "onion", "potato", "banana", "apple", "mango", "lemon", "curry",
        "greens", "flower", "marigold", "garland"
    )
    if (produceKeywords.any { haystack.contains(it) } && categoryEnum() == ProductCategory.GROCERY) {
        return ProductSubCategory.FRESH_PRODUCE
    }

    val fancyKeywords = listOf(
        "fancy", "bangle", "bangles", "bindi", "jhumka", "earring", "hairpin",
        "hair clip", "mehendi", "mirror", "gift", "potli", "ribbon", "payal", "kumkum box"
    )
    if (categoryEnum() == ProductCategory.FANCY || fancyKeywords.any { haystack.contains(it) }) {
        return ProductSubCategory.FANCY_ITEMS
    }

    if (categoryEnum() == ProductCategory.PUJA) {
        return ProductSubCategory.PUJA_SAMAGRI
    }

    return ProductSubCategory.PANTRY_STAPLES
}

fun filterAndSortProducts(
    products: List<Product>,
    primaryCategory: ProductCategory? = null,
    subCategory: ProductSubCategory = ProductSubCategory.ALL,
    sortOption: ProductSortOption = ProductSortOption.FEATURED,
    searchQuery: String = "",
    inStockOnly: Boolean = false
): List<Product> {
    val query = searchQuery.trim()
    val filtered = products.filter { product ->
        val matchesPrimary = primaryCategory == null || product.categoryEnum() == primaryCategory
        val matchesSub = subCategory == ProductSubCategory.ALL || product.inferSubCategory() == subCategory
        val matchesStock = !inStockOnly || (product.inStock && product.stockQty > 0L)
        val matchesQuery = query.isBlank() ||
            product.name.contains(query, ignoreCase = true) ||
            product.description.contains(query, ignoreCase = true) ||
            product.inferSubCategory().label.contains(query, ignoreCase = true)
        matchesPrimary && matchesSub && matchesStock && matchesQuery
    }

    return when (sortOption) {
        ProductSortOption.FEATURED -> filtered
        ProductSortOption.PRICE_LOW_HIGH -> filtered.sortedWith(compareBy<Product> { it.priceInr }.thenBy { it.name.lowercase() })
        ProductSortOption.PRICE_HIGH_LOW -> filtered.sortedWith(compareByDescending<Product> { it.priceInr }.thenBy { it.name.lowercase() })
        ProductSortOption.NAME_A_Z -> filtered.sortedBy { it.name.lowercase() }
        ProductSortOption.IN_STOCK_FIRST -> filtered.sortedWith(
            compareByDescending<Product> { it.inStock && it.stockQty > 0L }
                .thenByDescending { it.stockQty }
                .thenBy { it.name.lowercase() }
        )
    }
}

@Keep
@IgnoreExtraProperties
data class BuyerAddress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "Home",
    val recipientName: String = "",
    val phoneNumber: String = "",
    val streetAddress: String = "",
    val neighborhood: String = "",
    val landmark: String = "",
    val lat: Double = 12.9716,
    val lng: Double = 77.5946,
    val defaultAddress: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun formattedDeliverySummary(): String {
        val parts = listOf(streetAddress.trim(), neighborhood.trim(), landmark.trim())
            .filter { it.isNotBlank() }
        return parts.joinToString(", ")
    }
}

enum class PaymentMethodType(val label: String) {
    UPI("UPI"),
    COD("Cash on Delivery"),
    CARD_LABEL("Saved Card Label"),
    WALLET("Digital Wallet");

    companion object {
        fun fromRaw(raw: String?): PaymentMethodType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UPI
    }
}

@Keep
@IgnoreExtraProperties
data class PreferredPaymentMethod(
    @DocumentId val id: String = "",
    val userId: String = "",
    val methodType: String = PaymentMethodType.UPI.name,
    val displayLabel: String = "",
    val handleOrHint: String = "",
    val defaultMethod: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun methodTypeEnum(): PaymentMethodType = PaymentMethodType.fromRaw(methodType)
}

@Keep
@IgnoreExtraProperties
data class WishlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sellerId: String = "",
    val storeName: String = "",
    val productId: String = "",
    val name: String = "",
    val category: String = ProductCategory.GROCERY.name,
    val unit: String = "",
    val priceInr: Double = 0.0,
    val description: String = "",
    val photoUri: String = "",
    val imageTag: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun categoryEnum(): ProductCategory = ProductCategory.fromRaw(category)

    fun toPreviewProduct(): Product = Product(
        id = productId,
        userId = sellerId,
        name = name,
        category = category,
        unit = unit,
        priceInr = priceInr,
        stockQty = 1L,
        inStock = true,
        description = description,
        photoUri = photoUri,
        imageTag = imageTag
    )
}
