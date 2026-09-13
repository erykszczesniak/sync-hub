package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.CanonicalChange
import com.erykszczesniak.synchub.canonical.CanonicalValidationException
import com.erykszczesniak.synchub.canonical.FieldError
import com.erykszczesniak.synchub.source.SourceRecord
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Source model → canonical model. One implementation per (source, feed). Tombstones map to a
 * [CanonicalChange] without a record; everything else must produce a fully valid canonical record
 * or throw [MappingException] with every problem listed.
 */
interface SourceMapper<T : Any> {
    val feed: String

    fun toCanonical(record: SourceRecord): CanonicalChange<T> {
        if (record.deleted) return CanonicalChange(record.businessKey, record.sourceUpdatedAt, null)
        val reader = PayloadReader(record.payload)
        val canonical =
            try {
                map(record, reader)
            } catch (ex: CanonicalValidationException) {
                // Structural problems (missing/unparsable fields) come first: placeholder values for
                // missing fields would otherwise show up as misleading validation errors.
                reader.throwIfAny()
                throw MappingException(MappingFailureKind.INVALID, ex.errors, ex)
            }
        reader.throwIfAny()
        return CanonicalChange(record.businessKey, record.sourceUpdatedAt, canonical)
    }

    /** Build the canonical record; read fields through [reader] so missing/unparsable ones are collected. */
    fun map(
        record: SourceRecord,
        reader: PayloadReader,
    ): T
}

/**
 * Field access over a raw JSON payload that collects problems instead of failing on the first one.
 * Required fields that are absent yield placeholder values and an error; [throwIfAny] turns the
 * collected errors into an UNMAPPABLE [MappingException] after the whole record has been inspected.
 */
class PayloadReader(
    private val root: JsonNode,
    /** Shared with child readers (see [objects]) so problems inside nested arrays are not lost. */
    private val errors: MutableList<FieldError> = mutableListOf(),
    private val prefix: String = "",
) {
    fun text(path: String): String = textOrNull(path) ?: missing(path, "text").let { "" }

    fun textOrNull(path: String): String? = node(path)?.takeIf { it.isTextual }?.asText()

    fun instant(path: String): Instant =
        try {
            textOrNull(path)?.let(Instant::parse) ?: missing(path, "ISO-8601 timestamp").let { Instant.EPOCH }
        } catch (ex: DateTimeParseException) {
            errors += FieldError(prefix + path, "is not an ISO-8601 timestamp: ${ex.parsedString}")
            Instant.EPOCH
        }

    fun int(path: String): Int =
        node(path)?.takeIf { it.isIntegralNumber }?.asInt() ?: missing(path, "integer").let { 0 }

    fun decimal(path: String): BigDecimal {
        val node = node(path)
        val value = if (node != null && (node.isTextual || node.isNumber)) node.asText().toBigDecimalOrNull() else null
        return value ?: missing(path, "decimal").let { BigDecimal.ZERO }
    }

    fun textList(path: String): List<String> {
        val node = node(path) ?: return emptyList()
        if (!node.isArray) {
            errors += FieldError(prefix + path, "must be an array")
            return emptyList()
        }
        return node.map { it.asText() }
    }

    fun objects(path: String): List<PayloadReader> {
        val node = node(path)
        if (node == null || !node.isArray) {
            missing(path, "array")
            return emptyList()
        }
        return node.mapIndexed { index, element -> PayloadReader(element, errors, "$prefix$path[$index].") }
    }

    inline fun <reified E : Enum<E>> enum(path: String): E {
        val raw = textOrNull(path)
        val match = raw?.let { value -> enumValues<E>().firstOrNull { it.name.equals(value, ignoreCase = true) } }
        if (match == null) {
            report(path, "must be one of ${enumValues<E>().joinToString { it.name }} (was '$raw')")
            return enumValues<E>().first()
        }
        return match
    }

    fun report(
        path: String,
        message: String,
    ) {
        errors += FieldError(prefix + path, message)
    }

    fun throwIfAny() {
        if (errors.isNotEmpty()) throw MappingException(MappingFailureKind.UNMAPPABLE, errors.toList())
    }

    private fun missing(
        path: String,
        expected: String,
    ) {
        errors += FieldError(prefix + path, "missing or not a $expected")
    }

    private fun node(path: String): JsonNode? =
        path.split('.').fold<String, JsonNode?>(root) { node, segment -> node?.get(segment) }?.takeUnless { it.isNull }
}
