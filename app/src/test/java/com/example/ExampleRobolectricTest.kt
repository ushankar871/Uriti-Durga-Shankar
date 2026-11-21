package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.getTestLoginPreset
import com.example.auth.loadAccountDetails
import com.example.auth.loadSavedPortalRole
import com.example.auth.saveAccountDetails
import com.example.auth.savePortalRole
import com.example.model.AccountRole
import com.example.model.BuyerAddress
import com.example.model.OrderStatus
import com.example.model.PaymentMethodType
import com.example.model.PreferredPaymentMethod
import com.example.model.Product
import com.example.model.ProductCategory
import com.example.model.ProductSortOption
import com.example.model.ProductSubCategory
import com.example.model.SellerOrder
import com.example.model.filterAndSortProducts
import com.example.model.inferSubCategory
import com.example.notifications.LowStockNotifier
import com.example.qr.ParsedQrPayload
import com.example.qr.QrPayloadParser
import com.example.ui.components.defaultPromotionalBanners
import com.example.ui.components.generalItemImagePresets
import com.example.ui.components.resolveProductArtworkRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lakshmi", appName)
    }

    @Test
    fun `test login presets provide valid buyer and seller profiles`() {
        val buyerPreset = getTestLoginPreset(AccountRole.BUYER)
        val sellerPreset = getTestLoginPreset(AccountRole.SELLER)

        assertEquals(AccountRole.BUYER, buyerPreset.role)
        assertEquals(AccountRole.SELLER, sellerPreset.role)
        assertTrue(buyerPreset.phoneNumber.isNotBlank())
        assertTrue(buyerPreset.email.contains("@"))
        assertTrue(sellerPreset.phoneNumber.isNotBlank())
        assertTrue(sellerPreset.email.contains("@"))
    }

    @Test
    fun `test login role and contact persistence works per uid`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uid = "qa_test_uid_1"
        val preset = getTestLoginPreset(AccountRole.SELLER)

        saveAccountDetails(context, uid, preset.toContactDetails())
        savePortalRole(context, uid, AccountRole.SELLER)

        val loadedDetails = loadAccountDetails(context, uid)
        assertEquals(preset.displayName, loadedDetails.displayName)
        assertEquals(preset.phoneNumber, loadedDetails.phoneNumber)
        assertEquals(preset.email, loadedDetails.email)
        assertEquals(AccountRole.SELLER, loadSavedPortalRole(context, uid))

        savePortalRole(context, uid, AccountRole.BUYER)
        assertEquals(AccountRole.BUYER, loadSavedPortalRole(context, uid))
    }

    @Test
    fun `qr payload parser decodes and matches item and order payloads`() {
        val diya = Product(
            id = "prod_diya_01",
            name = "Brass Diya Pair",
            category = ProductCategory.PUJA.name,
            unit = "1 pair",
            priceInr = 250.0,
            stockQty = 3L,
            inStock = true
        )
        val order = SellerOrder(
            id = "ord_987654",
            customerName = "Priya",
            status = OrderStatus.OUT_FOR_DELIVERY.name,
            deliveryOtp = "482910"
        )

        val itemQr = QrPayloadParser.encodeProductQr(diya)
        val parsedItem = QrPayloadParser.parse(itemQr) as? ParsedQrPayload.ItemRef
        assertNotNull(parsedItem)
        assertEquals(diya.id, QrPayloadParser.matchProduct(parsedItem!!, listOf(diya))?.id)

        val orderQr = QrPayloadParser.encodeOrderQr(order)
        val parsedOrder = QrPayloadParser.parse(orderQr) as? ParsedQrPayload.OrderRef
        assertNotNull(parsedOrder)
        assertEquals("482910", parsedOrder!!.otp)
        assertEquals(order.id, QrPayloadParser.matchOrder(parsedOrder, listOf(order))?.id)

        val jsonOrder = QrPayloadParser.parse("""{"type":"order","orderId":"987654","otp":"482910"}""") as? ParsedQrPayload.OrderRef
        assertNotNull(jsonOrder)
        assertEquals(order.id, QrPayloadParser.matchOrder(jsonOrder!!, listOf(order))?.id)
    }

    @Test
    fun `low stock alert sound preference toggles cleanly per seller`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sellerId = "seller_sound_test"

        assertTrue(LowStockNotifier.isAlertSoundEnabled(context, sellerId))
        LowStockNotifier.setAlertSoundEnabled(context, sellerId, false)
        assertFalse(LowStockNotifier.isAlertSoundEnabled(context, sellerId))
        LowStockNotifier.setAlertSoundEnabled(context, sellerId, true)
        assertTrue(LowStockNotifier.isAlertSoundEnabled(context, sellerId))
    }

    @Test
    fun `product sub-category inference and sorting filter bar logic work accurately`() {
        val tomatoes = Product(
            id = "p1",
            name = "Fresh Farm Tomatoes",
            category = ProductCategory.GROCERY.name,
            priceInr = 68.0,
            stockQty = 16L,
            inStock = true,
            imageTag = "produce"
        )
        val camphor = Product(
            id = "p2",
            name = "Bhimseni Camphor",
            category = ProductCategory.PUJA.name,
            priceInr = 210.0,
            stockQty = 3L,
            inStock = true,
            imageTag = "camphor"
        )
        val soap = Product(
            id = "p3",
            name = "Herbal Neem & Tulsi Soap",
            category = ProductCategory.GROCERY.name,
            priceInr = 145.0,
            stockQty = 0L,
            inStock = false,
            imageTag = "personal_care"
        )
        val rice = Product(
            id = "p4",
            name = "Aged Basmati Rice",
            category = ProductCategory.GROCERY.name,
            priceInr = 490.0,
            stockQty = 18L,
            inStock = true,
            imageTag = "rice"
        )
        val all = listOf(tomatoes, camphor, soap, rice)

        assertEquals(ProductSubCategory.FRESH_PRODUCE, tomatoes.inferSubCategory())
        assertEquals(ProductSubCategory.PUJA_SAMAGRI, camphor.inferSubCategory())
        assertEquals(ProductSubCategory.PERSONAL_CARE, soap.inferSubCategory())
        assertEquals(ProductSubCategory.PANTRY_STAPLES, rice.inferSubCategory())

        val pujaOnly = filterAndSortProducts(all, subCategory = ProductSubCategory.PUJA_SAMAGRI)
        assertEquals(listOf("p2"), pujaOnly.map { it.id })

        val sortedPriceLow = filterAndSortProducts(all, sortOption = ProductSortOption.PRICE_LOW_HIGH)
        assertEquals(listOf("p1", "p3", "p2", "p4"), sortedPriceLow.map { it.id })

        val inStockHighPrice = filterAndSortProducts(
            all,
            sortOption = ProductSortOption.PRICE_HIGH_LOW,
            inStockOnly = true
        )
        assertEquals(listOf("p4", "p2", "p1"), inStockHighPrice.map { it.id })
    }

    @Test
    fun `promotional banners and buyer profile helpers format valid metadata`() {
        val banners = defaultPromotionalBanners()
        assertTrue(banners.size >= 4)
        assertTrue(banners.any { it.targetSubCategory == ProductSubCategory.PUJA_SAMAGRI })
        assertTrue(banners.any { it.targetSubCategory == ProductSubCategory.FANCY_ITEMS })
        assertTrue(banners.any { it.targetSubCategory == ProductSubCategory.FRESH_PRODUCE })
        banners.forEach { banner ->
            assertTrue(banner.title.isNotBlank())
            assertTrue(banner.ctaLabel.isNotBlank())
        }

        val address = BuyerAddress(
            label = "Home",
            recipientName = "Priya",
            phoneNumber = "+91 9876500002",
            streetAddress = "42, 12th Main",
            neighborhood = "Indiranagar",
            landmark = "Near Metro"
        )
        assertEquals("42, 12th Main, Indiranagar, Near Metro", address.formattedDeliverySummary())

        val payment = PreferredPaymentMethod(
            methodType = PaymentMethodType.UPI.name,
            displayLabel = "Primary GPay",
            handleOrHint = "priya@okaxis"
        )
        assertEquals(PaymentMethodType.UPI, payment.methodTypeEnum())
    }

    @Test
    fun `fancy category inference and AI general item image presets resolve properly`() {
        val bangles = Product(
            id = "f1",
            name = "Silk & Glass Bangles Set",
            category = ProductCategory.FANCY.name,
            priceInr = 220.0,
            stockQty = 15L,
            inStock = true,
            imageTag = "fancy"
        )
        val diya = Product(
            id = "p_diya",
            name = "Brass Puja Diya Pair",
            category = ProductCategory.PUJA.name,
            priceInr = 240.0,
            stockQty = 14L,
            inStock = true,
            imageTag = "diya"
        )

        assertEquals(ProductCategory.FANCY, ProductCategory.fromRaw("FANCY"))
        assertEquals("Fancy Items", ProductCategory.FANCY.displayName)
        assertEquals(ProductSubCategory.FANCY_ITEMS, bangles.inferSubCategory())
        assertTrue(ProductSubCategory.filterableEntries.contains(ProductSubCategory.FANCY_ITEMS))

        val fancyFiltered = filterAndSortProducts(
            listOf(bangles, diya),
            primaryCategory = ProductCategory.FANCY,
            subCategory = ProductSubCategory.FANCY_ITEMS
        )
        assertEquals(listOf("f1"), fancyFiltered.map { it.id })

        val presets = generalItemImagePresets()
        assertTrue(presets.size >= 6)
        assertTrue(presets.any { it.tag == "fancy" && it.suggestedCategory == ProductCategory.FANCY })
        assertTrue(presets.any { it.tag == "general" })
        assertEquals(R.drawable.img_fancy_items, resolveProductArtworkRes("fancy", ProductCategory.FANCY))
        assertEquals(R.drawable.img_general_merchandise, resolveProductArtworkRes("general", ProductCategory.GROCERY))
        assertEquals(R.drawable.img_pantry_staples, resolveProductArtworkRes("rice", ProductCategory.GROCERY))
        assertEquals(R.drawable.img_personal_care, resolveProductArtworkRes("personal_care", ProductCategory.GROCERY))
    }
}
