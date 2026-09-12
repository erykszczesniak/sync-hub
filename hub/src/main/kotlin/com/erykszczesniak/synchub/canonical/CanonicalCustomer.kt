package com.erykszczesniak.synchub.canonical

import java.time.Instant

enum class CustomerLifecycle { ACTIVE, INACTIVE, CHURNED }

enum class LoyaltyTier { BRONZE, SILVER, GOLD, PLATINUM }

data class CanonicalAddress(
    val street: String,
    val city: String,
    val postalCode: String,
    /** ISO 3166-1 alpha-2, upper case. */
    val countryCode: String,
) {
    init {
        Validation.validate {
            notBlank(street, "address.street")
            notBlank(city, "address.city")
            notBlank(postalCode, "address.postalCode")
            matches(countryCode, Validation.COUNTRY_CODE, "address.countryCode", "an ISO 3166-1 alpha-2 code")
        }
    }
}

/**
 * The neutral customer. Neither System A's shape (nested `address`, `firstName`) nor System B's
 * (flat columns, `full_name`): every source maps into this and every sink maps out of it, so adding
 * a third system touches one mapper, not the whole pipeline.
 */
data class CanonicalCustomer(
    val businessKey: String,
    val email: String,
    val givenName: String,
    val familyName: String,
    val lifecycle: CustomerLifecycle,
    val tier: LoyaltyTier,
    val address: CanonicalAddress,
    val tags: Set<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val fullName: String get() = "$givenName $familyName"

    init {
        Validation.validate {
            notBlank(businessKey, "businessKey")
            matches(email.lowercase(), Validation.EMAIL, "email", "a valid email address")
            notBlank(givenName, "givenName")
            notBlank(familyName, "familyName")
            check(!updatedAt.isBefore(createdAt), "updatedAt") { "must not be before createdAt" }
            check(tags.none { it.isBlank() }, "tags") { "must not contain blank tags" }
        }
    }
}
