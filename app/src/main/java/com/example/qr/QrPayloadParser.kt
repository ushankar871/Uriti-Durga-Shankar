package com.example.qr

import com.example.model.OrderStatus
import com.example.model.Product
import com.example.model.SellerOrder
import org.json.JSONObject
import java.util.Locale

sealed interface ParsedQrPayload {
    val raw: String

    data class ItemRef(
        val productId: String? = null,
        val queryText: String,
        override val raw: String
    ) : ParsedQrPayload

    data class OrderRef(
        val orderId: String,
        val otp: String? = null,
        override val raw: String
    ) : ParsedQrPayload
}

object QrPayloadParser {

    fun encodeProductQr(product: Product): String =
        "LAKSHMI:ITEM:${product.id}:${product.name}"

    fun encodeOrderQr(order: SellerOrder): String =
        if (order.deliveryOtp.isNotBlank()) {
            "LAKSHMI:ORDER:${order.id}:${order.deliveryOtp}"
        } else {
            "LAKSHMI:ORDER:${order.id}"
        }

    fun parse(rawValue: String): ParsedQrPayload? {
        val clean = rawValue.trim()
        if (clean.isEmpty()) return null

        // JSON format: {"type":"order","orderId":"...","otp":"..."} or {"type":"item","id":"..."}
        if (clean.startsWith("{") && clean.endsWith("}")) {
            try {
                val json = JSONObject(clean)
                val type = json.optString("type", "").lowercase(Locale.US)
                val orderId = json.optString("orderId", json.optString("order_id", "")).trim()
                val otp = json.optString("otp", "").trim().takeIf { it.isNotEmpty() }
                if (type == "order" || orderId.isNotEmpty()) {
                    if (orderId.isNotEmpty()) {
                        return ParsedQrPayload.OrderRef(orderId = orderId, otp = otp, raw = clean)
                    }
                }
                val itemId = json.optString("productId", json.optString("id", "")).trim()
                val itemName = json.optString("name", itemId).trim()
                if (itemId.isNotEmpty() || itemName.isNotEmpty()) {
                    return ParsedQrPayload.ItemRef(
                        productId = itemId.takeIf { it.isNotEmpty() },
                        queryText = itemName.ifBlank { itemId },
                        raw = clean
                    )
                }
            } catch (_: Exception) {
                // Fall through to structured URI / delimited parsing
            }
        }

        // URI format: lakshmi://order/<id>?otp=123456 or lakshmi://item/<id>
        if (clean.startsWith("lakshmi://", ignoreCase = true)) {
            val remainder = clean.substringAfter("://")
            val host = remainder.substringBefore("/").lowercase(Locale.US)
            val pathAndQuery = remainder.substringAfter("/", "")
            val pathPart = pathAndQuery.substringBefore("?").trim()
            val queryPart = pathAndQuery.substringAfter("?", "")
            if (host == "order" && pathPart.isNotEmpty()) {
                val otp = queryPart.split("&")
                    .firstOrNull { it.startsWith("otp=", ignoreCase = true) }
                    ?.substringAfter("=")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                return ParsedQrPayload.OrderRef(orderId = pathPart, otp = otp, raw = clean)
            }
            if (host == "item" && pathPart.isNotEmpty()) {
                return ParsedQrPayload.ItemRef(productId = pathPart, queryText = pathPart, raw = clean)
            }
        }

        // Delimited format: LAKSHMI:ORDER:<orderId>[:<otp>] or LAKSHMI:ITEM:<productId>[:<name>]
        val parts = clean.split(":")
        if (parts.size >= 3 && parts[0].equals("LAKSHMI", ignoreCase = true)) {
            when (parts[1].uppercase(Locale.US)) {
                "ORDER" -> {
                    val orderId = parts[2].trim()
                    val otp = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
                    if (orderId.isNotEmpty()) {
                        return ParsedQrPayload.OrderRef(orderId = orderId, otp = otp, raw = clean)
                    }
                }
                "ITEM" -> {
                    val productId = parts[2].trim()
                    val label = parts.drop(3).joinToString(":").trim().ifBlank { productId }
                    if (productId.isNotEmpty()) {
                        return ParsedQrPayload.ItemRef(
                            productId = productId,
                            queryText = label,
                            raw = clean
                        )
                    }
                }
            }
        }

        // Fallback: #ORDERREF or plain product ID/name/barcode
        if (clean.startsWith("#") && clean.length >= 4) {
            return ParsedQrPayload.OrderRef(
                orderId = clean.removePrefix("#").trim(),
                otp = null,
                raw = clean
            )
        }

        return ParsedQrPayload.ItemRef(
            productId = clean,
            queryText = clean,
            raw = clean
        )
    }

    fun matchProduct(payload: ParsedQrPayload.ItemRef, products: List<Product>): Product? {
        val targetId = payload.productId?.trim().orEmpty()
        if (targetId.isNotEmpty()) {
            products.firstOrNull { it.id.equals(targetId, ignoreCase = true) }?.let { return it }
        }
        val query = payload.queryText.trim()
        if (query.isNotEmpty()) {
            products.firstOrNull { it.name.equals(query, ignoreCase = true) }?.let { return it }
            products.firstOrNull {
                it.name.contains(query, ignoreCase = true) ||
                    it.id.endsWith(query, ignoreCase = true)
            }?.let { return it }
        }
        return null
    }

    fun matchOrder(payload: ParsedQrPayload.OrderRef, orders: List<SellerOrder>): SellerOrder? {
        val target = payload.orderId.trim()
        if (target.isEmpty()) return null
        orders.firstOrNull { it.id.equals(target, ignoreCase = true) }?.let { return it }
        orders.firstOrNull {
            it.id.takeLast(6).equals(target, ignoreCase = true) ||
                it.id.endsWith(target, ignoreCase = true)
        }?.let { return it }
        return null
    }

    fun findOrderFromRaw(rawValue: String, orders: List<SellerOrder>): SellerOrder? {
        val parsed = parse(rawValue) ?: return null
        return when (parsed) {
            is ParsedQrPayload.OrderRef -> matchOrder(parsed, orders)
            is ParsedQrPayload.ItemRef -> orders.firstOrNull {
                it.statusEnum() != OrderStatus.DELIVERED && (
                    it.id.equals(parsed.queryText, ignoreCase = true) ||
                        it.id.takeLast(6).equals(parsed.queryText, ignoreCase = true)
                    )
            }
        }
    }
}
