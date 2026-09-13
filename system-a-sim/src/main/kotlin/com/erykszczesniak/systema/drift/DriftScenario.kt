package com.erykszczesniak.systema.drift

/**
 * Ways System A can change its contract without telling anyone. Each scenario is applied at
 * serialization time, so the stored data stays intact and drift can be switched on and off live.
 */
enum class DriftScenario(
    val description: String,
) {
    RENAME_EMAIL("customers: field `email` is renamed to `emailAddress`"),
    RETYPE_POSTAL_CODE("customers: `address.postalCode` becomes a number instead of a string"),
    ADD_LOYALTY_POINTS("customers: a new top-level field `loyaltyPoints` appears"),
    DROP_TIER("customers: field `tier` disappears"),
    ORDER_STATUS_ON_HOLD("orders: status uses the new value `ON_HOLD`, unknown to consumers"),
}
