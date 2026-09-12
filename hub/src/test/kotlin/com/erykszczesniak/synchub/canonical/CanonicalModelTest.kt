package com.erykszczesniak.synchub.canonical

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class CanonicalModelTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `a valid customer is accepted and exposes a full name`() {
        val customer = customer()

        assertThat(customer.fullName).isEqualTo("Anna Nowak")
    }

    @Test
    fun `every violation is reported at once`() {
        val ex =
            assertThrows<CanonicalValidationException> {
                customer(email = "not-an-email", givenName = " ", updatedAt = t0.minusSeconds(1))
            }

        assertThat(ex.errors.map { it.field }).containsExactlyInAnyOrder("email", "givenName", "updatedAt")
        assertThat(ex.message).contains("email: must be a valid email address")
    }

    @Test
    fun `address requires an upper-case ISO country code`() {
        val ex =
            assertThrows<CanonicalValidationException> {
                CanonicalAddress(street = "1 Long Street", city = "Warsaw", postalCode = "00-001", countryCode = "pl")
            }

        assertThat(ex.errors.single().field).isEqualTo("address.countryCode")
    }

    @Test
    fun `order total must reconcile with its lines`() {
        val lines = listOf(CanonicalOrderLine("SKU-1", 2, 1050), CanonicalOrderLine("SKU-2", 1, 500))

        val ok = order(lines = lines, totalMinor = 2600)
        assertThat(ok.total.amountMinor).isEqualTo(2600)

        val ex = assertThrows<CanonicalValidationException> { order(lines = lines, totalMinor = 2599) }
        assertThat(ex.errors.single().field).isEqualTo("total")
        assertThat(ex.errors.single().message).contains("2600")
    }

    @Test
    fun `order lines and money are validated on their own`() {
        assertThrows<CanonicalValidationException> { CanonicalOrderLine("SKU-1", 0, 100) }
        assertThrows<CanonicalValidationException> { CanonicalMoney(-1, "EUR") }
        assertThrows<CanonicalValidationException> { CanonicalMoney(1, "euro") }
        assertThrows<CanonicalValidationException> { order(lines = emptyList(), totalMinor = 0) }
    }

    @Test
    fun `a change without a record is a delete`() {
        val delete = CanonicalChange<CanonicalCustomer>("cus_1", t0, null)
        val upsert = CanonicalChange("cus_1", t0, customer())

        assertThat(delete.isDelete).isTrue()
        assertThat(upsert.isDelete).isFalse()
        assertThrows<CanonicalValidationException> { CanonicalChange<CanonicalCustomer>(" ", t0, null) }
    }

    private fun customer(
        email: String = "anna.nowak@example.com",
        givenName: String = "Anna",
        updatedAt: Instant = t0.plusSeconds(60),
    ) = CanonicalCustomer(
        businessKey = "cus_00001",
        email = email,
        givenName = givenName,
        familyName = "Nowak",
        lifecycle = CustomerLifecycle.ACTIVE,
        tier = LoyaltyTier.GOLD,
        address = CanonicalAddress("1 Long Street", "Warsaw", "00-001", "PL"),
        tags = setOf("vip"),
        createdAt = t0,
        updatedAt = updatedAt,
    )

    private fun order(
        lines: List<CanonicalOrderLine>,
        totalMinor: Long,
    ) = CanonicalOrder(
        businessKey = "ord_000001",
        customerKey = "cus_00001",
        state = OrderState.PAID,
        total = CanonicalMoney(totalMinor, "EUR"),
        lines = lines,
        placedAt = t0,
        updatedAt = t0.plusSeconds(10),
    )
}
