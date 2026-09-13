package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.FieldError

enum class MappingFailureKind {
    /** The payload's structure could not be read into the canonical shape (missing/unparsable fields). */
    UNMAPPABLE,

    /** The payload was readable but the canonical model rejected the values. */
    INVALID,
}

/** A record that cannot become canonical. The pipeline quarantines it with these details. */
class MappingException(
    val kind: MappingFailureKind,
    val errors: List<FieldError>,
    cause: Throwable? = null,
) : RuntimeException("${kind.name.lowercase()}: " + errors.joinToString("; "), cause)
