package com.erykszczesniak.systema.model

import java.time.Instant

/** Anything the change feeds can page over: identified by a key and ordered by `updatedAt`. */
interface Versioned {
    val key: String
    val updatedAt: Instant
    val deleted: Boolean
}
