package com.example.data

import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.AccountProfile
import com.example.model.AccountRole
import com.example.model.AuditLog
import com.example.model.BuyerAddress
import com.example.model.FeedbackCategory
import com.example.model.OrderStatus
import com.example.model.PaymentMethodType
import com.example.model.PreferredPaymentMethod
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.SellerOrder
import com.example.model.StoreConfig
import com.example.model.StoreFeedback
import com.example.model.WishlistItem
import com.example.security.OtpSecurityManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class SellerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeAccountProfile(userId: String): Flow<AccountProfile?> =
        db.collection("accounts").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        AccountProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }

    suspend fun upsertAccountProfile(
        userId: String,
        role: AccountRole,
        displayName: String,
        email: String,
        phoneNumber: String = ""
    ): Result<Unit> {
        val docPath = "accounts/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("accounts").document(uid)
            val existing = docRef.get().await()
            val cleanName = displayName.trim().ifBlank {
                if (role == AccountRole.SELLER) "Lakshmi Seller" else "Lakshmi Buyer"
            }.take(80)
            val cleanEmail = email.trim().take(160)
            val cleanPhone = phoneNumber.trim().take(24)

            if (existing.exists()) {
                val updates = mutableMapOf<String, Any>(
                    "role" to role.name,
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanEmail.isNotEmpty()) updates["email"] = cleanEmail
                if (cleanPhone.isNotEmpty()) updates["phoneNumber"] = cleanPhone
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "role" to role.name,
                    "displayName" to cleanName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanEmail.isNotEmpty()) payload["email"] = cleanEmail
                if (cleanPhone.isNotEmpty()) payload["phoneNumber"] = cleanPhone
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeWishlist(userId: String): Flow<List<WishlistItem>> =
        db.collection("accounts").document(userId).collection("wishlist")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    WishlistItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun saveWishlistItem(
        userId: String,
        sellerId: String,
        storeName: String,
        product: Product
    ): Result<Unit> {
        val safeSellerId = sellerId.ifBlank { product.userId }
        val docId = "${safeSellerId}_${product.id}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
        val docPath = "accounts/$userId/wishlist/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user wishlist write." }
            require(safeSellerId.isNotBlank() && product.id.isNotBlank()) { "Invalid store or product reference." }

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "sellerId" to safeSellerId,
                "storeName" to storeName.trim().ifBlank { "Lakshmi Store" }.take(80),
                "productId" to product.id,
                "name" to product.name.trim().ifBlank { "Saved Item" }.take(80),
                "category" to product.categoryEnum().name,
                "unit" to product.unit.trim().ifBlank { "1 unit" }.take(30),
                "priceInr" to product.priceInr.coerceIn(0.0, 1_000_000.0),
                "createdAt" to FieldValue.serverTimestamp()
            )
            val cleanDesc = product.description.trim().take(300)
            if (cleanDesc.isNotEmpty()) payload["description"] = cleanDesc
            val cleanPhoto = product.photoUri.trim().take(500)
            if (cleanPhoto.isNotEmpty()) payload["photoUri"] = cleanPhoto
            val cleanTag = product.imageTag.trim().take(40)
            if (cleanTag.isNotEmpty()) payload["imageTag"] = cleanTag

            db.collection("accounts").document(uid).collection("wishlist").document(docId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun removeWishlistItem(userId: String, wishlistItemId: String): Result<Unit> {
        val docPath = "accounts/$userId/wishlist/$wishlistItemId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user wishlist delete." }
            require(wishlistItemId.isNotBlank()) { "Wishlist item ID must not be blank." }
            db.collection("accounts").document(uid).collection("wishlist").document(wishlistItemId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun clearWishlist(userId: String): Result<Int> {
        val docPath = "accounts/$userId/wishlist"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user wishlist clear." }
            val snap = db.collection("accounts").document(uid).collection("wishlist").get().await()
            var removed = 0
            for (doc in snap.documents) {
                doc.reference.delete().await()
                removed++
            }
            Result.success(removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAccountContactDetails(
        userId: String,
        role: AccountRole,
        displayName: String,
        email: String,
        phoneNumber: String
    ): Result<Unit> {
        val docPath = "accounts/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user profile write." }
            val cleanName = displayName.trim().ifBlank { "Lakshmi Buyer" }.take(80)
            val cleanEmail = email.trim().take(160)
            val cleanPhone = phoneNumber.trim().take(24)
            val docRef = db.collection("accounts").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "role" to role.name,
                        "displayName" to cleanName,
                        "email" to cleanEmail,
                        "phoneNumber" to cleanPhone,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "role" to role.name,
                        "displayName" to cleanName,
                        "email" to cleanEmail,
                        "phoneNumber" to cleanPhone,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    fun observeBuyerAddresses(userId: String): Flow<List<BuyerAddress>> =
        db.collection("accounts").document(userId).collection("addresses")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    BuyerAddress::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<BuyerAddress> { it.defaultAddress }
                        .thenByDescending { it.updatedAt?.seconds ?: 0L }
                )
            }

    suspend fun saveBuyerAddress(
        userId: String,
        addressId: String?,
        label: String,
        recipientName: String,
        phoneNumber: String,
        streetAddress: String,
        neighborhood: String,
        landmark: String,
        lat: Double,
        lng: Double,
        makeDefault: Boolean
    ): Result<Unit> {
        var docPath = "accounts/$userId/addresses"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user address write." }
            val cleanLabel = label.trim().ifBlank { "Home" }.take(30)
            val cleanRecipient = recipientName.trim().ifBlank { "Buyer" }.take(80)
            val cleanPhone = phoneNumber.trim().ifBlank { "Not set" }.take(24)
            val cleanStreet = streetAddress.trim().take(180)
            val cleanNeighborhood = neighborhood.trim().take(100)
            require(cleanStreet.isNotBlank()) { "Street address is required." }
            require(cleanNeighborhood.isNotBlank()) { "Neighborhood / area is required." }
            val cleanLandmark = landmark.trim().take(120)
            val safeLat = lat.coerceIn(-90.0, 90.0)
            val safeLng = lng.coerceIn(-180.0, 180.0)

            val collectionRef = db.collection("accounts").document(uid).collection("addresses")
            val existingSnap = collectionRef.get().await()
            val shouldBeDefault = makeDefault || existingSnap.isEmpty

            val targetRef = if (addressId.isNullOrBlank()) {
                collectionRef.document()
            } else {
                collectionRef.document(addressId)
            }
            docPath = targetRef.path

            if (shouldBeDefault) {
                for (doc in existingSnap.documents) {
                    if (doc.id != targetRef.id && doc.getBoolean("defaultAddress") == true) {
                        doc.reference.update(
                            mapOf(
                                "defaultAddress" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            if (addressId.isNullOrBlank()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "label" to cleanLabel,
                    "recipientName" to cleanRecipient,
                    "phoneNumber" to cleanPhone,
                    "streetAddress" to cleanStreet,
                    "neighborhood" to cleanNeighborhood,
                    "lat" to safeLat,
                    "lng" to safeLng,
                    "defaultAddress" to shouldBeDefault,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanLandmark.isNotEmpty()) {
                    payload["landmark"] = cleanLandmark
                }
                targetRef.set(payload).await()
            } else {
                targetRef.update(
                    mapOf(
                        "label" to cleanLabel,
                        "recipientName" to cleanRecipient,
                        "phoneNumber" to cleanPhone,
                        "streetAddress" to cleanStreet,
                        "neighborhood" to cleanNeighborhood,
                        "landmark" to cleanLandmark,
                        "lat" to safeLat,
                        "lng" to safeLng,
                        "defaultAddress" to shouldBeDefault,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setDefaultBuyerAddress(userId: String, addressId: String): Result<Unit> {
        val docPath = "accounts/$userId/addresses/$addressId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user address write." }
            val collectionRef = db.collection("accounts").document(uid).collection("addresses")
            val snap = collectionRef.get().await()
            for (doc in snap.documents) {
                val isTarget = doc.id == addressId
                val currentlyDefault = doc.getBoolean("defaultAddress") == true
                if (isTarget != currentlyDefault) {
                    doc.reference.update(
                        mapOf(
                            "defaultAddress" to isTarget,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBuyerAddress(userId: String, addressId: String): Result<Unit> {
        val docPath = "accounts/$userId/addresses/$addressId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user address delete." }
            require(addressId.isNotBlank()) { "Address ID must not be blank." }
            db.collection("accounts").document(uid).collection("addresses").document(addressId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observePreferredPaymentMethods(userId: String): Flow<List<PreferredPaymentMethod>> =
        db.collection("accounts").document(userId).collection("paymentMethods")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    PreferredPaymentMethod::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareByDescending<PreferredPaymentMethod> { it.defaultMethod }
                        .thenByDescending { it.updatedAt?.seconds ?: 0L }
                )
            }

    suspend fun savePreferredPaymentMethod(
        userId: String,
        methodId: String?,
        methodType: PaymentMethodType,
        displayLabel: String,
        handleOrHint: String,
        makeDefault: Boolean
    ): Result<Unit> {
        var docPath = "accounts/$userId/paymentMethods"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user payment write." }
            val cleanLabel = displayLabel.trim().ifBlank { methodType.label }.take(60)
            val cleanHint = handleOrHint.trim().take(120)

            val collectionRef = db.collection("accounts").document(uid).collection("paymentMethods")
            val existingSnap = collectionRef.get().await()
            val shouldBeDefault = makeDefault || existingSnap.isEmpty

            val targetRef = if (methodId.isNullOrBlank()) {
                collectionRef.document()
            } else {
                collectionRef.document(methodId)
            }
            docPath = targetRef.path

            if (shouldBeDefault) {
                for (doc in existingSnap.documents) {
                    if (doc.id != targetRef.id && doc.getBoolean("defaultMethod") == true) {
                        doc.reference.update(
                            mapOf(
                                "defaultMethod" to false,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            if (methodId.isNullOrBlank()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "methodType" to methodType.name,
                    "displayLabel" to cleanLabel,
                    "defaultMethod" to shouldBeDefault,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanHint.isNotEmpty()) {
                    payload["handleOrHint"] = cleanHint
                }
                targetRef.set(payload).await()
            } else {
                targetRef.update(
                    mapOf(
                        "methodType" to methodType.name,
                        "displayLabel" to cleanLabel,
                        "handleOrHint" to cleanHint,
                        "defaultMethod" to shouldBeDefault,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setDefaultPreferredPaymentMethod(userId: String, methodId: String): Result<Unit> {
        val docPath = "accounts/$userId/paymentMethods/$methodId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user payment write." }
            val collectionRef = db.collection("accounts").document(uid).collection("paymentMethods")
            val snap = collectionRef.get().await()
            for (doc in snap.documents) {
                val isTarget = doc.id == methodId
                val currentlyDefault = doc.getBoolean("defaultMethod") == true
                if (isTarget != currentlyDefault) {
                    doc.reference.update(
                        mapOf(
                            "defaultMethod" to isTarget,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePreferredPaymentMethod(userId: String, methodId: String): Result<Unit> {
        val docPath = "accounts/$userId/paymentMethods/$methodId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user payment delete." }
            require(methodId.isNotBlank()) { "Payment method ID must not be blank." }
            db.collection("accounts").document(uid).collection("paymentMethods").document(methodId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun observeAllStores(): Flow<List<StoreConfig>> =
        db.collection("stores")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StoreConfig::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.storeName.lowercase() }
            }

    fun observeStoreConfig(sellerId: String): Flow<StoreConfig?> =
        db.collection("stores").document(sellerId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        StoreConfig::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }

    fun observeProducts(sellerId: String): Flow<List<Product>> =
        db.collection("stores").document(sellerId).collection("products")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Product::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(compareByDescending<Product> { it.inStock }.thenBy { it.name.lowercase() })
            }

    fun observeSellerOrders(sellerId: String): Flow<List<SellerOrder>> =
        db.collection("orders")
            .whereEqualTo("sellerId", sellerId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SellerOrder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeBuyerOrders(buyerId: String): Flow<List<SellerOrder>> =
        db.collection("orders")
            .whereEqualTo("buyerId", buyerId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SellerOrder::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeFeedback(sellerId: String): Flow<List<StoreFeedback>> =
        db.collection("stores").document(sellerId).collection("feedback")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    StoreFeedback::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    fun observeAuditLogs(sellerId: String): Flow<List<AuditLog>> =
        db.collection("stores").document(sellerId).collection("auditLogs")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AuditLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun appendAuditLog(sellerId: String, action: String, summary: String) {
        val docPath = "stores/$sellerId/auditLogs"
        try {
            val uid = requireUserId()
            if (uid != sellerId) return
            db.collection("stores").document(uid).collection("auditLogs").document()
                .set(
                    mapOf(
                        "sellerId" to uid,
                        "action" to action.trim().ifBlank { "EVENT" }.take(40),
                        "summary" to summary.trim().ifBlank { "Inventory change" }.take(240),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
        }
    }

    suspend fun submitFeedback(
        sellerId: String,
        authorName: String,
        rating: Int,
        category: FeedbackCategory,
        comment: String
    ): Result<Unit> {
        val docPath = "stores/$sellerId/feedback"
        return try {
            val uid = requireUserId()
            val cleanAuthor = authorName.trim().ifBlank { "Customer" }.take(80)
            val cleanComment = comment.trim().take(400)
            require(cleanComment.isNotBlank()) { "Please enter a feedback comment." }
            val safeRating = rating.coerceIn(1, 5).toLong()

            val docRef = db.collection("stores").document(sellerId).collection("feedback").document()
            docRef.set(
                mapOf(
                    "sellerId" to sellerId,
                    "authorId" to uid,
                    "authorName" to cleanAuthor,
                    "rating" to safeRating,
                    "category" to category.name,
                    "comment" to cleanComment,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteFeedback(sellerId: String, feedbackId: String): Result<Unit> {
        val docPath = "stores/$sellerId/feedback/$feedbackId"
        return try {
            requireUserId()
            db.collection("stores").document(sellerId).collection("feedback").document(feedbackId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createProduct(
        sellerId: String,
        name: String,
        category: ProductCategory,
        unit: String,
        priceInr: Double,
        stockQty: Long,
        inStock: Boolean,
        description: String,
        photoUri: String,
        imageTag: String
    ): Result<Unit> {
        val docPath = "stores/$sellerId/products"
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            val cleanName = name.trim().take(80)
            val cleanUnit = unit.trim().ifBlank { "1 unit" }.take(30)
            require(cleanName.isNotBlank()) { "Product name is required." }

            val docRef = db.collection("stores").document(uid).collection("products").document()
            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "name" to cleanName,
                "category" to category.name,
                "unit" to cleanUnit,
                "priceInr" to priceInr.coerceIn(0.0, 1_000_000.0),
                "stockQty" to stockQty.coerceIn(0L, 1_000_000L),
                "inStock" to inStock,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val cleanDesc = description.trim().take(300)
            if (cleanDesc.isNotEmpty()) payload["description"] = cleanDesc
            val cleanUri = photoUri.trim().take(500)
            if (cleanUri.isNotEmpty()) payload["photoUri"] = cleanUri
            val cleanTag = imageTag.trim().take(40)
            if (cleanTag.isNotEmpty()) payload["imageTag"] = cleanTag

            docRef.set(payload).await()
            appendAuditLog(uid, "CREATE_ITEM", "Created $cleanName (${stockQty.coerceIn(0L, 1_000_000L)} $cleanUnit)")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(
        sellerId: String,
        productId: String,
        name: String,
        category: ProductCategory,
        unit: String,
        priceInr: Double,
        stockQty: Long,
        inStock: Boolean,
        description: String,
        photoUri: String,
        imageTag: String
    ): Result<Unit> {
        val docPath = "stores/$sellerId/products/$productId"
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            require(productId.isNotBlank()) { "Product ID must not be blank." }
            val cleanName = name.trim().take(80)
            val cleanUnit = unit.trim().ifBlank { "1 unit" }.take(30)
            require(cleanName.isNotBlank()) { "Product name is required." }

            val updates = mutableMapOf<String, Any>(
                "name" to cleanName,
                "category" to category.name,
                "unit" to cleanUnit,
                "priceInr" to priceInr.coerceIn(0.0, 1_000_000.0),
                "stockQty" to stockQty.coerceIn(0L, 1_000_000L),
                "inStock" to inStock,
                "description" to description.trim().take(300),
                "photoUri" to photoUri.trim().take(500),
                "imageTag" to imageTag.trim().take(40),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("stores").document(uid).collection("products").document(productId)
                .update(updates)
                .await()
            appendAuditLog(uid, "EDIT_ITEM", "Updated $cleanName -> qty ${stockQty.coerceIn(0L, 1_000_000L)}, ₹${priceInr.toInt()}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleStockStatus(
        sellerId: String,
        product: Product,
        newInStock: Boolean,
        newQty: Long = if (newInStock) maxOf(product.stockQty, 5L) else 0L
    ): Result<Unit> {
        val docPath = "stores/$sellerId/products/${product.id}"
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            require(product.id.isNotBlank()) { "Product ID must not be blank." }
            val safeQty = newQty.coerceIn(0L, 1_000_000L)
            db.collection("stores").document(uid).collection("products").document(product.id)
                .update(
                    mapOf(
                        "inStock" to newInStock,
                        "stockQty" to safeQty,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            appendAuditLog(
                uid,
                "STOCK_ADJUST",
                "${product.name}: ${product.stockQty} -> $safeQty (${if (newInStock) "Present" else "Out"})"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(sellerId: String, product: Product): Result<Unit> {
        val docPath = "stores/$sellerId/products/${product.id}"
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            require(product.id.isNotBlank()) { "Product ID must not be blank." }
            db.collection("stores").document(uid).collection("products").document(product.id)
                .delete()
                .await()
            appendAuditLog(uid, "DELETE_ITEM", "Deleted ${product.name}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createOrder(
        sellerId: String,
        storeName: String,
        customerName: String,
        neighborhood: String,
        itemsSummary: String,
        totalInr: Double,
        lat: Double,
        lng: Double
    ): Result<Unit> {
        val docPath = "orders"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("orders").document()
            val deliveryOtp = (100_000 + java.security.SecureRandom().nextInt(900_000)).toString()
            val payload = mapOf(
                "sellerId" to sellerId,
                "buyerId" to uid,
                "storeName" to storeName.trim().ifBlank { "Lakshmi Store" }.take(80),
                "customerName" to customerName.trim().ifBlank { "Buyer Customer" }.take(80),
                "neighborhood" to neighborhood.trim().ifBlank { "Local Sector" }.take(100),
                "itemsSummary" to itemsSummary.trim().ifBlank { "Mixed Basket" }.take(300),
                "totalInr" to totalInr.coerceIn(0.0, 1_000_000.0),
                "status" to OrderStatus.PENDING.name,
                "lat" to lat.coerceIn(-90.0, 90.0),
                "lng" to lng.coerceIn(-180.0, 180.0),
                "deliveryOtp" to deliveryOtp,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun advanceOrderStatus(
        order: SellerOrder,
        storeLat: Double? = null,
        storeLng: Double? = null
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            check(uid == order.sellerId) { "Only the fulfilling seller can update order status." }
            val nextStatus = order.statusEnum().next()
                ?: return Result.failure(IllegalStateException("Delivered orders are finalized."))
            val updates = mutableMapOf<String, Any>(
                "status" to nextStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            when (nextStatus) {
                OrderStatus.PACKED -> {
                    if (order.riderLat == null && storeLat != null && storeLng != null) {
                        updates["riderLat"] = storeLat.coerceIn(-90.0, 90.0)
                        updates["riderLng"] = storeLng.coerceIn(-180.0, 180.0)
                        updates["riderUpdatedAt"] = FieldValue.serverTimestamp()
                    }
                }
                OrderStatus.OUT_FOR_DELIVERY -> {
                    val baseLat = order.riderLat ?: storeLat
                    val baseLng = order.riderLng ?: storeLng
                    if (baseLat != null && baseLng != null) {
                        val dispatchLat = baseLat + (order.lat - baseLat) * 0.25
                        val dispatchLng = baseLng + (order.lng - baseLng) * 0.25
                        updates["riderLat"] = dispatchLat.coerceIn(-90.0, 90.0)
                        updates["riderLng"] = dispatchLng.coerceIn(-180.0, 180.0)
                        updates["riderUpdatedAt"] = FieldValue.serverTimestamp()
                    }
                }
                OrderStatus.DELIVERED -> {
                    updates["riderLat"] = order.lat.coerceIn(-90.0, 90.0)
                    updates["riderLng"] = order.lng.coerceIn(-180.0, 180.0)
                    updates["riderUpdatedAt"] = FieldValue.serverTimestamp()
                }
                OrderStatus.PENDING -> Unit
            }
            db.collection("orders").document(order.id)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderRiderLocation(
        order: SellerOrder,
        riderLat: Double,
        riderLng: Double
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            check(uid == order.sellerId) { "Only the fulfilling seller can update delivery location." }
            check(order.statusEnum() != OrderStatus.DELIVERED) { "Delivered orders cannot update live location." }
            db.collection("orders").document(order.id)
                .update(
                    mapOf(
                        "riderLat" to riderLat.coerceIn(-90.0, 90.0),
                        "riderLng" to riderLng.coerceIn(-180.0, 180.0),
                        "riderUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun confirmOrderDelivery(
        order: SellerOrder,
        method: String,
        proofUri: String = ""
    ): Result<Unit> {
        val docPath = "orders/${order.id}"
        return try {
            val uid = requireUserId()
            check(uid == order.sellerId) { "Only the fulfilling seller can confirm delivery." }
            val safeMethod = if (method == "PHOTO") "PHOTO" else "OTP"
            val auditMethodLabel = when (method) {
                "PHOTO" -> "PHOTO"
                "QR" -> "QR (OTP)"
                else -> "OTP"
            }
            val safeProof = proofUri.trim().take(500)
            db.collection("orders").document(order.id)
                .update(
                    mapOf(
                        "status" to OrderStatus.DELIVERED.name,
                        "deliveryMethod" to safeMethod,
                        "deliveryProofUri" to safeProof,
                        "riderLat" to order.lat.coerceIn(-90.0, 90.0),
                        "riderLng" to order.lng.coerceIn(-180.0, 180.0),
                        "riderUpdatedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            appendAuditLog(
                uid,
                "DELIVERY_CONFIRMED",
                "Delivered order for ${order.customerName} (#${order.id.takeLast(6)}) via $auditMethodLabel"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun ensureDefaultStoreConfig(sellerId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("stores").document(uid)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                saveStoreConfig(
                    sellerId = uid,
                    storeName = "Lakshmi Puja, Fancy & Grocery",
                    address = "100 Feet Rd, Indiranagar, Bengaluru",
                    storeLat = 12.9716,
                    storeLng = 77.5946,
                    deliveryRadiusKm = 5.0,
                    showOutOfStock = true,
                    deleteProtectionEnabled = true,
                    otpProtectionEnabled = true,
                    lowStockThreshold = 5L,
                    lowStockAlertsEnabled = true,
                    alreadyExists = false
                ).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveStoreConfig(
        sellerId: String,
        storeName: String,
        address: String,
        storeLat: Double,
        storeLng: Double,
        deliveryRadiusKm: Double,
        showOutOfStock: Boolean,
        deleteProtectionEnabled: Boolean,
        otpProtectionEnabled: Boolean,
        alreadyExists: Boolean,
        lowStockThreshold: Long = 5L,
        lowStockAlertsEnabled: Boolean = true
    ): Result<Unit> {
        val docPath = "stores/$sellerId"
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            val docRef = db.collection("stores").document(uid)
            val cleanName = storeName.trim().ifBlank { "Lakshmi Store" }.take(80)
            val cleanAddress = address.trim().ifBlank { "Main Bazaar Road" }.take(160)
            val safeLat = storeLat.coerceIn(-90.0, 90.0)
            val safeLng = storeLng.coerceIn(-180.0, 180.0)
            val safeRadius = deliveryRadiusKm.coerceIn(0.5, 50.0)
            val safeThreshold = lowStockThreshold.coerceIn(1L, 1000L)

            if (alreadyExists) {
                docRef.update(
                    mapOf(
                        "storeName" to cleanName,
                        "address" to cleanAddress,
                        "storeLat" to safeLat,
                        "storeLng" to safeLng,
                        "deliveryRadiusKm" to safeRadius,
                        "showOutOfStock" to showOutOfStock,
                        "deleteProtectionEnabled" to deleteProtectionEnabled,
                        "otpProtectionEnabled" to otpProtectionEnabled,
                        "lowStockThreshold" to safeThreshold,
                        "lowStockAlertsEnabled" to lowStockAlertsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "storeName" to cleanName,
                        "address" to cleanAddress,
                        "storeLat" to safeLat,
                        "storeLng" to safeLng,
                        "deliveryRadiusKm" to safeRadius,
                        "showOutOfStock" to showOutOfStock,
                        "deleteProtectionEnabled" to deleteProtectionEnabled,
                        "otpProtectionEnabled" to otpProtectionEnabled,
                        "lowStockThreshold" to safeThreshold,
                        "lowStockAlertsEnabled" to lowStockAlertsEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAllSellerData(sellerId: String): Result<Int> {
        val docPath = "stores/$sellerId"
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            var deletedCount = 0

            val productsSnap = db.collection("stores").document(uid).collection("products").get().await()
            for (doc in productsSnap.documents) {
                doc.reference.delete().await()
                deletedCount++
            }

            val feedbackSnap = db.collection("stores").document(uid).collection("feedback").get().await()
            for (doc in feedbackSnap.documents) {
                doc.reference.delete().await()
                deletedCount++
            }

            val ordersSnap = db.collection("orders").whereEqualTo("sellerId", uid).get().await()
            for (doc in ordersSnap.documents) {
                doc.reference.delete().await()
                deletedCount++
            }

            val storeRef = db.collection("stores").document(uid)
            if (storeRef.get().await().exists()) {
                storeRef.delete().await()
            }
            Result.success(deletedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBuyerPendingOrders(buyerId: String): Result<Int> {
        val docPath = "orders"
        return try {
            val uid = requireUserId()
            check(uid == buyerId) { "Signed-in user changed; refusing cross-user delete." }
            val snap = db.collection("orders").whereEqualTo("buyerId", uid).get().await()
            var removed = 0
            for (doc in snap.documents) {
                if (doc.getString("status") == OrderStatus.PENDING.name) {
                    doc.reference.delete().await()
                    removed++
                }
            }
            Result.success(removed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterCatalog(sellerId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            val storeExists = db.collection("stores").document(uid).get().await().exists()
            saveStoreConfig(
                sellerId = uid,
                storeName = "Lakshmi Puja, Fancy & Grocery",
                address = "100 Feet Rd, Indiranagar, Bengaluru",
                storeLat = 12.9716,
                storeLng = 77.5946,
                deliveryRadiusKm = 5.0,
                showOutOfStock = true,
                deleteProtectionEnabled = true,
                otpProtectionEnabled = true,
                alreadyExists = storeExists
            ).getOrThrow()

            val starterItems = listOf(
                Product(
                    name = "Aged Basmati Rice",
                    category = ProductCategory.GROCERY.name,
                    unit = "5 kg",
                    priceInr = 490.0,
                    stockQty = 18,
                    inStock = true,
                    description = "Long-grain aromatic basmati rice for daily meals & festive pulao.",
                    imageTag = "rice"
                ),
                Product(
                    name = "Organic Toor Dal",
                    category = ProductCategory.GROCERY.name,
                    unit = "1 kg",
                    priceInr = 165.0,
                    stockQty = 24,
                    inStock = true,
                    description = "Unpolished golden split pigeon peas, rich in protein.",
                    imageTag = "dal"
                ),
                Product(
                    name = "Pure Cow Ghee",
                    category = ProductCategory.GROCERY.name,
                    unit = "500 ml",
                    priceInr = 395.0,
                    stockQty = 4,
                    inStock = true,
                    description = "Traditional bilona A2 cow ghee suitable for cooking and diyas.",
                    imageTag = "ghee"
                ),
                Product(
                    name = "Fresh Palak & Tomatoes",
                    category = ProductCategory.GROCERY.name,
                    unit = "1 combo",
                    priceInr = 75.0,
                    stockQty = 0,
                    inStock = false,
                    description = "Farm-fresh morning spinach bunch and vine tomatoes.",
                    imageTag = "produce"
                ),
                Product(
                    name = "Brass Puja Diya Pair",
                    category = ProductCategory.PUJA.name,
                    unit = "2 pcs",
                    priceInr = 240.0,
                    stockQty = 14,
                    inStock = true,
                    description = "Heavy-gauge polished brass oil lamps for daily aarti.",
                    imageTag = "diya"
                ),
                Product(
                    name = "Bhimseni Camphor (Kapur)",
                    category = ProductCategory.PUJA.name,
                    unit = "100 g",
                    priceInr = 210.0,
                    stockQty = 3,
                    inStock = true,
                    description = "Pure crystalline camphor that burns cleanly without residue.",
                    imageTag = "camphor"
                ),
                Product(
                    name = "Kesar Chandan Agarbatti",
                    category = ProductCategory.PUJA.name,
                    unit = "1 pack",
                    priceInr = 85.0,
                    stockQty = 30,
                    inStock = true,
                    description = "Hand-rolled saffron & sandalwood temple incense sticks.",
                    imageTag = "incense"
                ),
                Product(
                    name = "Fresh Marigold Garland",
                    category = ProductCategory.PUJA.name,
                    unit = "2 ft",
                    priceInr = 60.0,
                    stockQty = 0,
                    inStock = false,
                    description = "Freshly strung orange & yellow genda phool toran for morning puja.",
                    imageTag = "flowers"
                ),
                Product(
                    name = "Kumkum & Haldi Puja Pack",
                    category = ProductCategory.PUJA.name,
                    unit = "200 g",
                    priceInr = 95.0,
                    stockQty = 0,
                    inStock = false,
                    description = "Natural turmeric and vermilion powder for auspicious rituals.",
                    imageTag = "kumkum"
                ),
                Product(
                    name = "Fresh Farm Tomatoes & Coriander",
                    category = ProductCategory.GROCERY.name,
                    unit = "1 kg + bunch",
                    priceInr = 68.0,
                    stockQty = 16,
                    inStock = true,
                    description = "Crisp morning-picked vine tomatoes with fresh coriander greens.",
                    imageTag = "produce"
                ),
                Product(
                    name = "Herbal Neem & Tulsi Bath Soap",
                    category = ProductCategory.GROCERY.name,
                    unit = "3 x 100 g",
                    priceInr = 145.0,
                    stockQty = 20,
                    inStock = true,
                    description = "Gentle ayurvedic personal care bath bars infused with neem and tulsi.",
                    imageTag = "personal_care"
                ),
                Product(
                    name = "Cold-Pressed Coconut Hair Oil",
                    category = ProductCategory.GROCERY.name,
                    unit = "250 ml",
                    priceInr = 160.0,
                    stockQty = 12,
                    inStock = true,
                    description = "Pure virgin coconut oil for daily personal care and hair nourishment.",
                    imageTag = "personal_care"
                ),
                Product(
                    name = "Silk & Glass Bangles Set",
                    category = ProductCategory.FANCY.name,
                    unit = "1 dozen set",
                    priceInr = 220.0,
                    stockQty = 15,
                    inStock = true,
                    description = "Festive handcrafted maroon and gold silk-thread bangles with shimmer accents.",
                    imageTag = "fancy"
                ),
                Product(
                    name = "Designer Stone Bindi & Mirror Box",
                    category = ProductCategory.FANCY.name,
                    unit = "1 gift box",
                    priceInr = 135.0,
                    stockQty = 4,
                    inStock = true,
                    description = "Assorted festive velvet & kundan bindis with a compact pearl pocket mirror.",
                    imageTag = "fancy"
                ),
                Product(
                    name = "Embroidered Potli & Jhumka Combo",
                    category = ProductCategory.FANCY.name,
                    unit = "1 combo",
                    priceInr = 340.0,
                    stockQty = 9,
                    inStock = true,
                    description = "Brocade drawstring potli pouch paired with traditional gold-plated jhumka earrings.",
                    imageTag = "bangles"
                )
            )
            for (item in starterItems) {
                createProduct(
                    sellerId = uid,
                    name = item.name,
                    category = item.categoryEnum(),
                    unit = item.unit,
                    priceInr = item.priceInr,
                    stockQty = item.stockQty,
                    inStock = item.inStock,
                    description = item.description,
                    photoUri = "",
                    imageTag = item.imageTag
                ).getOrThrow()
            }
            createOrder(
                sellerId = uid,
                storeName = "Lakshmi Puja, Fancy & Grocery",
                customerName = "Ananya Iyer",
                neighborhood = "Indiranagar 12th Main",
                itemsSummary = "1x Brass Diya Pair, 1x Bhimseni Camphor, 1x Basmati Rice",
                totalInr = 940.0,
                lat = 12.9784,
                lng = 77.6408
            ).getOrThrow()
            submitFeedback(
                sellerId = uid,
                authorName = "Priya Deshmukh",
                rating = 5,
                category = FeedbackCategory.PUJA,
                comment = "Always stocked with pure Bhimseni camphor and brass diyas. Very clear stock visibility!"
            ).getOrThrow()
            submitFeedback(
                sellerId = uid,
                authorName = "Karthik Rao",
                rating = 4,
                category = FeedbackCategory.GROCERY,
                comment = "Basmati rice and organic dal quality is great; hoping marigold garlands restock soon."
            ).getOrThrow()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportBackupJson(
        products: List<Product>,
        orders: List<SellerOrder>,
        storeConfig: StoreConfig?,
        feedbackList: List<StoreFeedback> = emptyList()
    ): String {
        val productsArray = JSONArray()
        products.forEach { p ->
            productsArray.put(
                JSONObject().apply {
                    put("name", p.name)
                    put("category", p.category)
                    put("unit", p.unit)
                    put("priceInr", p.priceInr)
                    put("stockQty", p.stockQty)
                    put("inStock", p.inStock)
                    put("description", p.description)
                    put("photoUri", p.photoUri)
                    put("imageTag", p.imageTag)
                }
            )
        }

        val ordersArray = JSONArray()
        orders.forEach { o ->
            ordersArray.put(
                JSONObject().apply {
                    put("customerName", o.customerName)
                    put("neighborhood", o.neighborhood)
                    put("itemsSummary", o.itemsSummary)
                    put("totalInr", o.totalInr)
                    put("status", o.status)
                    put("lat", o.lat)
                    put("lng", o.lng)
                }
            )
        }

        val feedbackArray = JSONArray()
        feedbackList.forEach { f ->
            feedbackArray.put(
                JSONObject().apply {
                    put("authorName", f.authorName)
                    put("rating", f.rating)
                    put("category", f.category)
                    put("comment", f.comment)
                }
            )
        }

        val payloadDigest = OtpSecurityManager.sha256(productsArray.toString())

        val root = JSONObject()
        root.put("app", "Lakshmi")
        root.put("backupVersion", 2)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("sha256Integrity", payloadDigest)
        root.put("products", productsArray)
        root.put("orders", ordersArray)
        root.put("feedback", feedbackArray)

        if (storeConfig != null) {
            root.put(
                "storeConfig",
                JSONObject().apply {
                    put("storeName", storeConfig.storeName)
                    put("address", storeConfig.address)
                    put("storeLat", storeConfig.storeLat)
                    put("storeLng", storeConfig.storeLng)
                    put("deliveryRadiusKm", storeConfig.deliveryRadiusKm)
                    put("showOutOfStock", storeConfig.showOutOfStock)
                    put("deleteProtectionEnabled", storeConfig.deleteProtectionEnabled)
                    put("otpProtectionEnabled", storeConfig.otpProtectionEnabled)
                    put("lowStockThreshold", storeConfig.lowStockThreshold)
                    put("lowStockAlertsEnabled", storeConfig.lowStockAlertsEnabled)
                }
            )
        }
        return root.toString(2)
    }

    suspend fun restoreFromBackupJson(
        sellerId: String,
        jsonText: String,
        hasExistingStoreConfig: Boolean
    ): Result<Int> {
        return try {
            val uid = requireUserId()
            check(uid == sellerId) { "Signed-in user changed; refusing cross-user write." }
            val root = JSONObject(jsonText)
            val productsArray = root.optJSONArray("products") ?: JSONArray()
            val expectedHash = root.optString("sha256Integrity", "")
            if (expectedHash.isNotBlank()) {
                val actualHash = OtpSecurityManager.sha256(productsArray.toString())
                require(actualHash == expectedHash) {
                    "Backup integrity check failed: file appears modified or corrupted."
                }
            }

            var restoredCount = 0
            for (i in 0 until productsArray.length()) {
                val obj = productsArray.getJSONObject(i)
                createProduct(
                    sellerId = uid,
                    name = obj.optString("name", "Restored Item"),
                    category = ProductCategory.fromRaw(obj.optString("category")),
                    unit = obj.optString("unit", "1 unit"),
                    priceInr = obj.optDouble("priceInr", 0.0),
                    stockQty = obj.optLong("stockQty", 0L),
                    inStock = obj.optBoolean("inStock", true),
                    description = obj.optString("description", ""),
                    photoUri = obj.optString("photoUri", ""),
                    imageTag = obj.optString("imageTag", "")
                ).getOrThrow()
                restoredCount++
            }
            val feedbackArray = root.optJSONArray("feedback") ?: JSONArray()
            for (i in 0 until feedbackArray.length()) {
                val obj = feedbackArray.getJSONObject(i)
                submitFeedback(
                    sellerId = uid,
                    authorName = obj.optString("authorName", "Customer"),
                    rating = obj.optInt("rating", 5),
                    category = FeedbackCategory.fromRaw(obj.optString("category")),
                    comment = obj.optString("comment", "Restored feedback")
                ).getOrThrow()
            }
            val storeObj = root.optJSONObject("storeConfig")
            if (storeObj != null) {
                saveStoreConfig(
                    sellerId = uid,
                    storeName = storeObj.optString("storeName", "Lakshmi Store"),
                    address = storeObj.optString("address", "Main Bazaar"),
                    storeLat = storeObj.optDouble("storeLat", 12.9716),
                    storeLng = storeObj.optDouble("storeLng", 77.5946),
                    deliveryRadiusKm = storeObj.optDouble("deliveryRadiusKm", 5.0),
                    showOutOfStock = storeObj.optBoolean("showOutOfStock", true),
                    deleteProtectionEnabled = storeObj.optBoolean("deleteProtectionEnabled", true),
                    otpProtectionEnabled = storeObj.optBoolean("otpProtectionEnabled", true),
                    lowStockThreshold = storeObj.optLong("lowStockThreshold", 5L),
                    lowStockAlertsEnabled = storeObj.optBoolean("lowStockAlertsEnabled", true),
                    alreadyExists = hasExistingStoreConfig
                ).getOrThrow()
            }
            appendAuditLog(uid, "BACKUP_RESTORE", "Restored $restoredCount verified items from JSON backup")
            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
