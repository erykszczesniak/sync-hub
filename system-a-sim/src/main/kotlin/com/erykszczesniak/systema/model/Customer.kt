package com.erykszczesniak.systema.model

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

enum class CustomerStatus { ACTIVE, INACTIVE, CHURNED }

enum class CustomerTier { BRONZE, SILVER, GOLD, PLATINUM }

data class Address(
    val line1: String,
    val line2: String? = null,
    val city: String,
    val postalCode: String,
    val country: String,
)

/** System A's own view of a customer: nested address, free-form tags, camelCase, ISO timestamps. */
data class Customer(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val status: CustomerStatus,
    val tier: CustomerTier,
    val address: Address,
    val tags: List<String>,
    val createdAt: Instant,
    override val updatedAt: Instant,
    /** Tombstone flag: deleted records stay visible in the change feed so consumers can propagate deletes. */
    override val deleted: Boolean = false,
) : Versioned {
    /** Internal ordering key; must not leak into the API payload (consumers would see an unexpected field). */
    @get:JsonIgnore
    override val key: String get() = id
}
