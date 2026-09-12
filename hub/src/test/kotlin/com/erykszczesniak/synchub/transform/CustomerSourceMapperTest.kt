package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.CustomerLifecycle
import com.erykszczesniak.synchub.canonical.LoyaltyTier
import com.erykszczesniak.synchub.source.SourceRecord
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CustomerSourceMapperTest {
    private val mapper = CustomerSourceMapper()
    private val json = ObjectMapper()
    private val updatedAt = Instant.parse("2026-02-01T12:00:00Z")

    @Test
    fun `maps System A's nested shape into the canonical customer`() {
        val change = mapper.toCanonical(record(VALID))

        val customer = change.record!!
        assertThat(change.businessKey).isEqualTo("cus_00007")
        assertThat(change.sourceUpdatedAt).isEqualTo(updatedAt)
        assertThat(customer.email).isEqualTo("anna.nowak@example.com")
        assertThat(customer.givenName).isEqualTo("Anna")
        assertThat(customer.familyName).isEqualTo("Nowak")
        assertThat(customer.lifecycle).isEqualTo(CustomerLifecycle.ACTIVE)
        assertThat(customer.tier).isEqualTo(LoyaltyTier.GOLD)
        assertThat(customer.address.street).isEqualTo("12 Long Street, Apt 3")
        assertThat(customer.address.countryCode).isEqualTo("PL")
        assertThat(customer.tags).containsExactlyInAnyOrder("vip", "newsletter")
        assertThat(customer.createdAt).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"))
    }

    @Test
    fun `a tombstone becomes a delete without reading the payload`() {
        val change = mapper.toCanonical(record("""{"id":"cus_1"}""", deleted = true))

        assertThat(change.isDelete).isTrue()
        assertThat(change.businessKey).isEqualTo("cus_00007")
    }

    @Test
    fun `missing and unknown fields are all reported as unmappable`() {
        val broken = """{"id":"cus_00007","emailAddress":"a@b.c","firstName":"Anna","lastName":"Nowak","status":"ON_HOLD",
            "tier":"GOLD","address":{"line1":"x","city":"Warsaw","postalCode":12345,"country":"PL"},
            "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-02-01T12:00:00Z"}"""

        val ex = assertThrows<MappingException> { mapper.toCanonical(record(broken)) }

        assertThat(ex.kind).isEqualTo(MappingFailureKind.UNMAPPABLE)
        assertThat(ex.errors.map { it.field }).containsExactlyInAnyOrder("email", "status", "address.postalCode")
        assertThat(ex.message).contains("status: must be one of ACTIVE, INACTIVE, CHURNED (was 'ON_HOLD')")
    }

    @Test
    fun `readable but invalid values are reported by the canonical model`() {
        val badEmail = VALID.replace("Anna.Nowak@Example.com", "not-an-email")
        val emailError = assertThrows<MappingException> { mapper.toCanonical(record(badEmail)) }
        assertThat(emailError.kind).isEqualTo(MappingFailureKind.INVALID)
        assertThat(emailError.errors.map { it.field }).containsExactly("email")

        // Nested value objects validate on their own, so an address problem surfaces as the address's error.
        val badCountry = VALID.replace("\"country\":\"pl\"", "\"country\":\"Poland\"")
        val countryError = assertThrows<MappingException> { mapper.toCanonical(record(badCountry)) }
        assertThat(countryError.kind).isEqualTo(MappingFailureKind.INVALID)
        assertThat(countryError.errors.map { it.field }).containsExactly("address.countryCode")
    }

    private fun record(
        payload: String,
        deleted: Boolean = false,
    ) = SourceRecord("customers", "cus_00007", updatedAt, deleted, json.readTree(payload))

    companion object {
        private const val VALID =
            """{"id":"cus_00007","email":" Anna.Nowak@Example.com ","firstName":"Anna","lastName":"Nowak",
               "status":"ACTIVE","tier":"GOLD",
               "address":{"line1":"12 Long Street","line2":"Apt 3","city":"Warsaw","postalCode":"00-001","country":"pl"},
               "tags":["VIP","newsletter",""],
               "createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-02-01T12:00:00Z","deleted":false}"""
    }
}
