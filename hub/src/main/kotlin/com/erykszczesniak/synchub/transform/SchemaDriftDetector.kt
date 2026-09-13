package com.erykszczesniak.synchub.transform

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

enum class DriftKind { FIELD_ADDED, FIELD_MISSING, FIELD_RETYPED, FIELD_RENAMED, VALUE_OUT_OF_DOMAIN }

enum class DriftSeverity {
    /** Backward compatible; the record can still be loaded. Raised so someone looks at it. */
    WARNING,

    /** The hub can no longer trust its mapping; the record is quarantined. */
    BLOCKING,
}

data class DriftFinding(
    val kind: DriftKind,
    val field: String,
    val expected: String?,
    val actual: String?,
    val details: String,
) {
    val severity: DriftSeverity
        get() = if (kind == DriftKind.FIELD_ADDED) DriftSeverity.WARNING else DriftSeverity.BLOCKING

    /** Stable identity of a finding across records and runs, so repeated detections collapse into one event. */
    val fingerprint: String
        get() = "${kind.name}:${this.field}:${expected ?: "-"}->${actual ?: "-"}"
}

data class DriftReport(
    val findings: List<DriftFinding>,
) {
    val isBlocking: Boolean
        get() = findings.any { it.severity == DriftSeverity.BLOCKING }
    val isEmpty: Boolean
        get() = findings.isEmpty()
}

/**
 * Compares a raw payload with the feed's [SchemaContract].
 *
 * Policy (see docs/SCHEMA-DRIFT.md): missing, retyped, renamed fields and values outside a closed
 * domain are BLOCKING: the record is quarantined and a drift event is raised, because loading it
 * would mean guessing. New fields are a WARNING: additive changes cannot corrupt System B, so the
 * record is loaded and the event tells an operator the source grew.
 *
 * A rename is inferred when a required field is missing and an undeclared field with a similar name
 * appears next to it (`email` → `emailAddress`); the two findings collapse into one FIELD_RENAMED.
 */
@Component
class SchemaDriftDetector {
    fun inspect(
        contract: SchemaContract,
        payload: JsonNode,
    ): DriftReport {
        val findings = mutableListOf<DriftFinding>()
        contract.fields.forEach { (path, spec) -> inspectDeclared(path, spec, payload, findings) }
        findAdded(contract, payload, "", findings)
        return DriftReport(collapseRenames(findings))
    }

    private fun inspectDeclared(
        path: String,
        spec: FieldSpec,
        payload: JsonNode,
        findings: MutableList<DriftFinding>,
    ) {
        val node = payload.at("/" + path.replace('.', '/'))
        if (node.isMissingNode || node.isNull) {
            if (spec.required) {
                findings +=
                    DriftFinding(
                        DriftKind.FIELD_MISSING,
                        path,
                        spec.type.name,
                        null,
                        "required field '$path' is missing",
                    )
            }
            return
        }
        val actual = typeOf(node)
        if (actual != spec.type) {
            findings +=
                DriftFinding(
                    DriftKind.FIELD_RETYPED,
                    path,
                    spec.type.name,
                    actual.name,
                    "field '$path' is ${actual.name}, expected ${spec.type.name}",
                )
            return
        }
        val allowed = spec.allowedValues
        if (allowed != null && node.isTextual && node.asText() !in allowed) {
            findings +=
                DriftFinding(
                    DriftKind.VALUE_OUT_OF_DOMAIN,
                    path,
                    allowed.sorted().joinToString("|"),
                    node.asText(),
                    "field '$path' has value '${node.asText()}' outside ${allowed.sorted()}",
                )
        }
    }

    private fun findAdded(
        contract: SchemaContract,
        node: JsonNode,
        parent: String,
        findings: MutableList<DriftFinding>,
    ) {
        if (!node.isObject) return
        val declared = contract.declaredChildren(parent)
        node.fieldNames().forEach { name ->
            val path = if (parent.isEmpty()) name else "$parent.$name"
            if (name !in declared) {
                findings +=
                    DriftFinding(
                        DriftKind.FIELD_ADDED,
                        path,
                        null,
                        typeOf(node.get(name)).name,
                        "unexpected field '$path'",
                    )
            } else if (contract.fields[path]?.type == FieldType.OBJECT) {
                findAdded(contract, node.get(name), path, findings)
            }
        }
    }

    private fun collapseRenames(findings: List<DriftFinding>): List<DriftFinding> {
        val missing = findings.filter { it.kind == DriftKind.FIELD_MISSING }
        val added = findings.filter { it.kind == DriftKind.FIELD_ADDED }
        val renames = mutableListOf<DriftFinding>()
        val consumed = mutableSetOf<DriftFinding>()
        for (m in missing) {
            val candidate =
                added.firstOrNull { it !in consumed && sameParent(m.field, it.field) && similar(m.field, it.field) }
                    ?: continue
            consumed += m
            consumed += candidate
            renames +=
                DriftFinding(
                    DriftKind.FIELD_RENAMED,
                    m.field,
                    m.field,
                    candidate.field,
                    "field '${m.field}' appears to be renamed to '${candidate.field}'",
                )
        }
        return findings.filterNot { it in consumed } + renames
    }

    private fun sameParent(
        a: String,
        b: String,
    ) = a.substringBeforeLast('.', "") == b.substringBeforeLast('.', "")

    private fun similar(
        a: String,
        b: String,
    ): Boolean {
        val x = a.substringAfterLast('.').lowercase().filter { it.isLetterOrDigit() }
        val y = b.substringAfterLast('.').lowercase().filter { it.isLetterOrDigit() }
        return x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x))
    }

    private fun typeOf(node: JsonNode): FieldType =
        when {
            node.isTextual -> FieldType.STRING
            node.isNumber -> FieldType.NUMBER
            node.isBoolean -> FieldType.BOOLEAN
            node.isArray -> FieldType.ARRAY
            else -> FieldType.OBJECT
        }
}
