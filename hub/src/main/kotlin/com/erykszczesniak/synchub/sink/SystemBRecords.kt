package com.erykszczesniak.synchub.sink

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

/**
 * System B's view of a customer: a flat, warehouse-style row. Codes instead of enum names, tags
 * joined, address flattened, one `full_name` for reporting. This is the model the loader persists
 * and the change events carry as before/after.
 */
data class SystemBCustomerRecord(
    val customerKey: String,
    val emailAddress: String,
    val fullName: String,
    val givenName: String,
    val familyName: String,
    /** A = active, I = inactive, C = churned. */
    val lifecycleCode: String,
    /** 1 = bronze … 4 = platinum, so tiers sort numerically. */
    val tierRank: Int,
    val addressLine: String,
    val city: String,
    val postalCode: String,
    val countryCode: String,
    val tagsCsv: String,
    val sourceCreatedAt: Instant,
    override val sourceUpdatedAt: Instant,
) : SystemBRecord {
    /** Merge key for the loader; not part of the wire payload (`customerKey` already carries it). */
    @get:JsonIgnore
    override val key: String get() = customerKey

    /** What "the same record" means for idempotency: every business attribute, not the version clock. */
    override fun contentFingerprint(): String =
        listOf(
            emailAddress,
            fullName,
            givenName,
            familyName,
            lifecycleCode,
            tierRank,
            addressLine,
            city,
            postalCode,
            countryCode,
            tagsCsv,
            sourceCreatedAt,
        ).joinToString("|")
}

data class SystemBOrderRecord(
    val orderKey: String,
    val customerKey: String,
    /** PEN, PAI, SHP, DLV, CAN. */
    val stateCode: String,
    val totalMinor: Long,
    val currency: String,
    val lineCount: Int,
    /** Order lines as a JSON array string; System B reports on headers and keeps lines for drill-down. */
    val linesJson: String,
    val placedAt: Instant,
    override val sourceUpdatedAt: Instant,
) : SystemBRecord {
    @get:JsonIgnore
    override val key: String get() = orderKey

    override fun contentFingerprint(): String =
        listOf(customerKey, stateCode, totalMinor, currency, lineCount, linesJson, placedAt).joinToString("|")
}

interface SystemBRecord {
    val key: String
    val sourceUpdatedAt: Instant

    fun contentFingerprint(): String
}
