package com.erykszczesniak.synchub.transform

enum class FieldType { STRING, NUMBER, BOOLEAN, OBJECT, ARRAY }

/** What the hub expects of one field in a source payload. Nested fields use dotted paths (`address.city`). */
data class FieldSpec(
    val type: FieldType,
    val required: Boolean = true,
    /** Closed value domain (enums). Any other value is drift of kind VALUE_OUT_OF_DOMAIN. */
    val allowedValues: Set<String>? = null,
)

/**
 * The schema the hub was built against for one feed. Drift is anything in the payload that this
 * contract does not describe: fields that vanished, changed type, were renamed, appeared, or carry
 * values outside a closed domain.
 */
data class SchemaContract(
    val feed: String,
    val fields: Map<String, FieldSpec>,
) {
    /** Direct children of [parent] ("" for the root) that the contract declares. */
    fun declaredChildren(parent: String): Set<String> =
        fields.keys
            .filter { path ->
                if (parent.isEmpty()) {
                    !path.contains('.')
                } else {
                    path.startsWith("$parent.") &&
                        !path.removePrefix("$parent.").contains('.')
                }
            }.map { it.substringAfterLast('.') }
            .toSet()
}

object SchemaContracts {
    private val CUSTOMER_STATUSES = setOf("ACTIVE", "INACTIVE", "CHURNED")
    private val CUSTOMER_TIERS = setOf("BRONZE", "SILVER", "GOLD", "PLATINUM")
    private val ORDER_STATUSES = setOf("PENDING", "PAID", "SHIPPED", "DELIVERED", "CANCELLED")

    val CUSTOMERS =
        SchemaContract(
            feed = "customers",
            fields =
                mapOf(
                    "id" to FieldSpec(FieldType.STRING),
                    "email" to FieldSpec(FieldType.STRING),
                    "firstName" to FieldSpec(FieldType.STRING),
                    "lastName" to FieldSpec(FieldType.STRING),
                    "status" to FieldSpec(FieldType.STRING, allowedValues = CUSTOMER_STATUSES),
                    "tier" to FieldSpec(FieldType.STRING, allowedValues = CUSTOMER_TIERS),
                    "address" to FieldSpec(FieldType.OBJECT),
                    "address.line1" to FieldSpec(FieldType.STRING),
                    "address.line2" to FieldSpec(FieldType.STRING, required = false),
                    "address.city" to FieldSpec(FieldType.STRING),
                    "address.postalCode" to FieldSpec(FieldType.STRING),
                    "address.country" to FieldSpec(FieldType.STRING),
                    "tags" to FieldSpec(FieldType.ARRAY, required = false),
                    "createdAt" to FieldSpec(FieldType.STRING),
                    "updatedAt" to FieldSpec(FieldType.STRING),
                    "deleted" to FieldSpec(FieldType.BOOLEAN, required = false),
                ),
        )

    val ORDERS =
        SchemaContract(
            feed = "orders",
            fields =
                mapOf(
                    "id" to FieldSpec(FieldType.STRING),
                    "customerId" to FieldSpec(FieldType.STRING),
                    "status" to FieldSpec(FieldType.STRING, allowedValues = ORDER_STATUSES),
                    "total" to FieldSpec(FieldType.OBJECT),
                    "total.amount" to FieldSpec(FieldType.STRING),
                    "total.currency" to FieldSpec(FieldType.STRING),
                    "lines" to FieldSpec(FieldType.ARRAY),
                    "placedAt" to FieldSpec(FieldType.STRING),
                    "updatedAt" to FieldSpec(FieldType.STRING),
                    "deleted" to FieldSpec(FieldType.BOOLEAN, required = false),
                ),
        )

    val ALL: Map<String, SchemaContract> = listOf(CUSTOMERS, ORDERS).associateBy { it.feed }
}
