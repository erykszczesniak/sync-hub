package com.erykszczesniak.synchub.common

/** Base of everything that can go wrong talking to a source system. */
sealed class SourceException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Transient failure (5xx, timeout, connection refused): worth a retry, counts towards the circuit breaker. */
class SourceUnavailableException(
    message: String,
    cause: Throwable? = null,
) : SourceException(message, cause)

/** Credentials rejected (401/403): retrying will not help, and neither will the next scheduled run. */
class SourceAuthException(
    message: String,
) : SourceException(message)

/** The source answered, but not in a shape the hub can read (4xx other than auth, unparsable body). */
class SourceResponseException(
    message: String,
    cause: Throwable? = null,
) : SourceException(message, cause)
