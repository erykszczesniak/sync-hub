package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.canonical.CanonicalAddress
import com.erykszczesniak.synchub.canonical.CanonicalCustomer
import com.erykszczesniak.synchub.canonical.CanonicalMoney
import com.erykszczesniak.synchub.canonical.CanonicalOrder
import com.erykszczesniak.synchub.canonical.CanonicalOrderLine
import com.erykszczesniak.synchub.canonical.CustomerLifecycle
import com.erykszczesniak.synchub.canonical.LoyaltyTier
import com.erykszczesniak.synchub.canonical.OrderState
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SinkMappersTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `customer is flattened into System B's row shape`() {
        val row = CustomerSinkMapper().toSystemB(customer())

        assertThat(row.customerKey).isEqualTo("cus_1")
        assertThat(row.fullName).isEqualTo("Anna Nowak")
        assertThat(row.lifecycleCode).isEqualTo("C")
        assertThat(row.tierRank).isEqualTo(4)
        assertThat(CustomerSinkMapper.tierFromRank(row.tierRank)).isEqualTo(LoyaltyTier.PLATINUM)
        assertThat(row.tagsCsv).isEqualTo("newsletter,vip")
        assertThat(row.sourceUpdatedAt).isEqualTo(t0.plusSeconds(5))
    }

    @Test
    fun `content fingerprint ignores the version clock so a re-read record is recognised as unchanged`() {
        val mapper = CustomerSinkMapper()
        val a = mapper.toSystemB(customer())
        val b = mapper.toSystemB(customer().copy(updatedAt = t0.plusSeconds(500)))
        val c = mapper.toSystemB(customer().copy(tier = LoyaltyTier.BRONZE))

        assertThat(a.contentFingerprint()).isEqualTo(b.contentFingerprint())
        assertThat(a.contentFingerprint()).isNotEqualTo(c.contentFingerprint())
    }

    @Test
    fun `order header plus lines as json`() {
        val row = OrderSinkMapper(ObjectMapper()).toSystemB(order())

        assertThat(row.stateCode).isEqualTo("SHP")
        assertThat(row.totalMinor).isEqualTo(2100)
        assertThat(row.lineCount).isEqualTo(1)
        assertThat(row.linesJson).isEqualTo("""[{"sku":"SKU-1","qty":2,"unitMinor":1050}]""")
    }

    private fun customer() =
        CanonicalCustomer(
            businessKey = "cus_1",
            email = "anna@example.com",
            givenName = "Anna",
            familyName = "Nowak",
            lifecycle = CustomerLifecycle.CHURNED,
            tier = LoyaltyTier.PLATINUM,
            address = CanonicalAddress("1 Street", "Warsaw", "00-001", "PL"),
            tags = setOf("vip", "newsletter"),
            createdAt = t0,
            updatedAt = t0.plusSeconds(5),
        )

    private fun order() =
        CanonicalOrder(
            businessKey = "ord_1",
            customerKey = "cus_1",
            state = OrderState.SHIPPED,
            total = CanonicalMoney(2100, "EUR"),
            lines = listOf(CanonicalOrderLine("SKU-1", 2, 1050)),
            placedAt = t0,
            updatedAt = t0.plusSeconds(5),
        )
}
