package com.erykszczesniak.synchub.canonical

/** One violated rule on one field. */
data class FieldError(
    val field: String,
    val message: String,
) {
    override fun toString(): String = "$field: $message"
}

/**
 * Thrown by canonical constructors when a record breaks the canonical contract.
 * Carries every violation, not only the first.
 */
class CanonicalValidationException(
    val errors: List<FieldError>,
) : RuntimeException("Canonical validation failed: " + errors.joinToString("; "))

/**
 * Explicit, dependency-free validation used by the canonical model (the Kotlin equivalent of a
 * pydantic model): rules are plain code, errors are collected, and a record is either fully valid
 * or rejected with the complete list of reasons.
 */
class Validation {
    private val errors = mutableListOf<FieldError>()

    fun check(
        condition: Boolean,
        field: String,
        message: () -> String,
    ) {
        if (!condition) errors += FieldError(field, message())
    }

    fun notBlank(
        value: String?,
        field: String,
    ) = check(!value.isNullOrBlank(), field) { "must not be blank" }

    fun matches(
        value: String?,
        regex: Regex,
        field: String,
        description: String,
    ) = check(value != null && regex.matches(value), field) { "must be $description" }

    fun throwIfAny() {
        if (errors.isNotEmpty()) throw CanonicalValidationException(errors.toList())
    }

    companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val COUNTRY_CODE = Regex("^[A-Z]{2}$")
        val CURRENCY_CODE = Regex("^[A-Z]{3}$")

        inline fun validate(block: Validation.() -> Unit) = Validation().apply(block).throwIfAny()
    }
}
