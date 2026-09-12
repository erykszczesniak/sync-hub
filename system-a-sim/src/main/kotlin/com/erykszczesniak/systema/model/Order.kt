package com.erykszczesniak.systema.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.time.Instant

enum class OrderStatus { PENDING, PAID, SHIPPED, DELIVERED, CANCELLED }

data class Money(
    val amount: BigDecimal,
    val currency: String,
)

data class OrderLine(
    val sku: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
)

data class Order(
    val id: String,
    val customerId: String,
    val status: OrderStatus,
    val total: Money,
    val lines: List<OrderLine>,
    val placedAt: Instant,
    override val updatedAt: Instant,
    override val deleted: Boolean = false,
) : Versioned {
    /** Internal ordering key; must not leak into the API payload (consumers would see an unexpected field). */
    @get:JsonIgnore
    override val key: String get() = id
}
