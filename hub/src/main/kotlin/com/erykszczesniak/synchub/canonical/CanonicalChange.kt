package com.erykszczesniak.synchub.canonical

import java.time.Instant

/** What happened to a record, in the neutral vocabulary shared by the sink and the change events. */
enum class ChangeType { CREATED, UPDATED, DELETED }

/**
 * One change coming out of the transform stage: the stable business key, the source's own version
 * clock (`sourceUpdatedAt`) and either the canonical record or a tombstone (`record == null`).
 *
 * The source timestamp travels with the record because idempotent loading and late-arrival handling
 * are decided against it, never against the time the hub happened to see the change.
 */
data class CanonicalChange<T : Any>(
    val businessKey: String,
    val sourceUpdatedAt: Instant,
    val record: T?,
) {
    val isDelete: Boolean get() = record == null

    init {
        Validation.validate {
            notBlank(businessKey, "businessKey")
        }
    }
}
