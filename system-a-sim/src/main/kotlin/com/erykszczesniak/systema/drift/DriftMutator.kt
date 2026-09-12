package com.erykszczesniak.systema.drift

import com.erykszczesniak.systema.model.Customer
import com.erykszczesniak.systema.model.Order
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.stereotype.Component

/** Applies the active [DriftScenario]s to outgoing payloads. */
@Component
class DriftMutator(
    private val objectMapper: ObjectMapper,
    private val state: DriftState,
) {
    fun customer(customer: Customer): ObjectNode {
        val node = objectMapper.valueToTree<ObjectNode>(customer)
        if (state.isActive(DriftScenario.RENAME_EMAIL)) {
            node.set<ObjectNode>("emailAddress", node.remove("email"))
        }
        if (state.isActive(DriftScenario.RETYPE_POSTAL_CODE)) {
            val address = node.get("address") as? ObjectNode
            val digits = address?.get("postalCode")?.asText()?.filter { it.isDigit() }
            if (address != null && !digits.isNullOrEmpty()) {
                address.put("postalCode", digits.toInt())
            }
        }
        if (state.isActive(DriftScenario.ADD_LOYALTY_POINTS)) {
            node.put("loyaltyPoints", LOYALTY_POINTS)
        }
        if (state.isActive(DriftScenario.DROP_TIER)) {
            node.remove("tier")
        }
        return node
    }

    /** Orders are typed by the GraphQL schema, so drift there shows up as a value outside the known domain. */
    fun orderStatus(order: Order): String =
        if (state.isActive(DriftScenario.ORDER_STATUS_ON_HOLD)) "ON_HOLD" else order.status.name

    companion object {
        private const val LOYALTY_POINTS = 120
    }
}
