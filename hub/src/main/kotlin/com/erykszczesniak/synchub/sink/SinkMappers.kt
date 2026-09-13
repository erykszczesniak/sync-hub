package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.canonical.CanonicalCustomer
import com.erykszczesniak.synchub.canonical.CanonicalOrder
import com.erykszczesniak.synchub.canonical.CustomerLifecycle
import com.erykszczesniak.synchub.canonical.LoyaltyTier
import com.erykszczesniak.synchub.canonical.OrderState
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/** Canonical → System B. Pure functions; System B never sees a System A field name. */
interface SinkMapper<C : Any, B : SystemBRecord> {
    fun toSystemB(canonical: C): B
}

@Component
class CustomerSinkMapper : SinkMapper<CanonicalCustomer, SystemBCustomerRecord> {
    override fun toSystemB(canonical: CanonicalCustomer) =
        SystemBCustomerRecord(
            customerKey = canonical.businessKey,
            emailAddress = canonical.email,
            fullName = canonical.fullName,
            givenName = canonical.givenName,
            familyName = canonical.familyName,
            lifecycleCode = lifecycleCode(canonical.lifecycle),
            tierRank = canonical.tier.ordinal + 1,
            addressLine = canonical.address.street,
            city = canonical.address.city,
            postalCode = canonical.address.postalCode,
            countryCode = canonical.address.countryCode,
            tagsCsv = canonical.tags.sorted().joinToString(","),
            sourceCreatedAt = canonical.createdAt,
            sourceUpdatedAt = canonical.updatedAt,
        )

    private fun lifecycleCode(lifecycle: CustomerLifecycle) =
        when (lifecycle) {
            CustomerLifecycle.ACTIVE -> "A"
            CustomerLifecycle.INACTIVE -> "I"
            CustomerLifecycle.CHURNED -> "C"
        }

    companion object {
        /** Inverse of `tierRank`, for readers of System B rows. */
        fun tierFromRank(rank: Int): LoyaltyTier =
            LoyaltyTier.entries[
                (rank - 1).coerceIn(
                    0,
                    LoyaltyTier.entries.size - 1,
                ),
            ]
    }
}

@Component
class OrderSinkMapper(
    private val objectMapper: ObjectMapper,
) : SinkMapper<CanonicalOrder, SystemBOrderRecord> {
    override fun toSystemB(canonical: CanonicalOrder) =
        SystemBOrderRecord(
            orderKey = canonical.businessKey,
            customerKey = canonical.customerKey,
            stateCode = stateCode(canonical.state),
            totalMinor = canonical.total.amountMinor,
            currency = canonical.total.currency,
            lineCount = canonical.lines.size,
            linesJson =
                objectMapper.writeValueAsString(
                    canonical.lines.map {
                        mapOf(
                            "sku" to it.sku,
                            "qty" to it.quantity,
                            "unitMinor" to it.unitPriceMinor,
                        )
                    },
                ),
            placedAt = canonical.placedAt,
            sourceUpdatedAt = canonical.updatedAt,
        )

    private fun stateCode(state: OrderState) =
        when (state) {
            OrderState.PENDING -> "PEN"
            OrderState.PAID -> "PAI"
            OrderState.SHIPPED -> "SHP"
            OrderState.DELIVERED -> "DLV"
            OrderState.CANCELLED -> "CAN"
        }
}
