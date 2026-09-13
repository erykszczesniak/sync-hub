package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.OrderState
import com.erykszczesniak.synchub.source.SourceRecord
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class OrderSourceMapperTest {
    private val mapper = OrderSourceMapper()
    private val json = ObjectMapper()

    @Test
    fun `decimal strings become minor units and lines are mapped`() {
        val order = mapper.toCanonical(record(VALID)).record!!

        assertThat(order.customerKey).isEqualTo("cus_00001")
        assertThat(order.state).isEqualTo(OrderState.PAID)
        assertThat(order.total.amountMinor).isEqualTo(3150)
        assertThat(order.total.currency).isEqualTo("EUR")
        assertThat(order.lines).hasSize(2)
        assertThat(order.lines[0].unitPriceMinor).isEqualTo(1050)
        assertThat(order.lines[1].lineTotalMinor).isEqualTo(1050)
    }

    @Test
    fun `an unknown status value is unmappable, which is how drift on a typed feed shows up`() {
        val ex = assertThrows<MappingException> { mapper.toCanonical(record(VALID.replace("PAID", "ON_HOLD"))) }

        assertThat(ex.kind).isEqualTo(MappingFailureKind.UNMAPPABLE)
        assertThat(ex.errors.single().field).isEqualTo("status")
    }

    @Test
    fun `more than two decimal places is unmappable, not a crash`() {
        val ex = assertThrows<MappingException> { mapper.toCanonical(record(VALID.replace("\"10.50\"", "\"10.505\""))) }

        assertThat(ex.kind).isEqualTo(MappingFailureKind.UNMAPPABLE)
        assertThat(ex.errors.map { it.field }).containsExactly("lines[0].unitPrice", "lines[1].unitPrice")
    }

    @Test
    fun `a total that does not reconcile with the lines is invalid`() {
        val ex = assertThrows<MappingException> { mapper.toCanonical(record(VALID.replace("31.50", "31.49"))) }

        assertThat(ex.kind).isEqualTo(MappingFailureKind.INVALID)
        assertThat(ex.errors.single().field).isEqualTo("total")
    }

    private fun record(payload: String) =
        SourceRecord("orders", "ord_000001", Instant.parse("2026-02-01T12:00:00Z"), false, json.readTree(payload))

    companion object {
        private const val VALID =
            """{"id":"ord_000001","customerId":"cus_00001","status":"PAID","total":{"amount":"31.50","currency":"eur"},
               "lines":[{"sku":"SKU-1","quantity":2,"unitPrice":"10.50"},{"sku":"SKU-2","quantity":1,"unitPrice":"10.50"}],
               "placedAt":"2026-02-01T10:00:00Z","updatedAt":"2026-02-01T12:00:00Z","deleted":false}"""
    }
}
