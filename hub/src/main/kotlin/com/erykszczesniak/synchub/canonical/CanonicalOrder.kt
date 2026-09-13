package com.erykszczesniak.synchub.canonical

import java.time.Instant

enum class OrderState { PENDING, PAID, SHIPPED, DELIVERED, CANCELLED }

/** Money in minor units (cents) plus ISO 4217 currency, so no floating point ever touches an amount. */
data class CanonicalMoney(
    val amountMinor: Long,
    val currency: String,
) {
    init {
        Validation.validate {
            check(amountMinor >= 0, "amountMinor") { "must not be negative" }
            matches(currency, Validation.CURRENCY_CODE, "currency", "an ISO 4217 code")
        }
    }
}

data class CanonicalOrderLine(
    val sku: String,
    val quantity: Int,
    val unitPriceMinor: Long,
) {
    val lineTotalMinor: Long get() = quantity * unitPriceMinor

    init {
        Validation.validate {
            notBlank(sku, "lines.sku")
            check(quantity > 0, "lines.quantity") { "must be positive" }
            check(unitPriceMinor >= 0, "lines.unitPriceMinor") { "must not be negative" }
        }
    }
}

data class CanonicalOrder(
    val businessKey: String,
    val customerKey: String,
    val state: OrderState,
    val total: CanonicalMoney,
    val lines: List<CanonicalOrderLine>,
    val placedAt: Instant,
    val updatedAt: Instant,
) {
    init {
        Validation.validate {
            notBlank(businessKey, "businessKey")
            notBlank(customerKey, "customerKey")
            check(lines.isNotEmpty(), "lines") { "must contain at least one line" }
            check(lines.sumOf { it.lineTotalMinor } == total.amountMinor, "total") {
                "must equal the sum of line totals (${lines.sumOf { it.lineTotalMinor }})"
            }
            check(!updatedAt.isBefore(placedAt), "updatedAt") { "must not be before placedAt" }
        }
    }
}
