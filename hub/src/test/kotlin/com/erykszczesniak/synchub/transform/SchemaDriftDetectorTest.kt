package com.erykszczesniak.synchub.transform

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaDriftDetectorTest {
    private val detector = SchemaDriftDetector()
    private val json = ObjectMapper()

    @Test
    fun `a payload matching the contract has no drift`() {
        val report = detector.inspect(SchemaContracts.CUSTOMERS, json.readTree(CUSTOMER))

        assertThat(report.isEmpty).isTrue()
        assertThat(report.isBlocking).isFalse()
    }

    @Test
    fun `a renamed field collapses missing plus added into one blocking finding`() {
        val payload = CUSTOMER.replace("\"email\"", "\"emailAddress\"")

        val report = detector.inspect(SchemaContracts.CUSTOMERS, json.readTree(payload))

        val finding = report.findings.single()
        assertThat(finding.kind).isEqualTo(DriftKind.FIELD_RENAMED)
        assertThat(finding.field).isEqualTo("email")
        assertThat(finding.actual).isEqualTo("emailAddress")
        assertThat(finding.fingerprint).isEqualTo("FIELD_RENAMED:email:email->emailAddress")
        assertThat(report.isBlocking).isTrue()
    }

    @Test
    fun `retyped nested field, dropped field and unknown enum value are blocking`() {
        val payload =
            CUSTOMER
                .replace("\"postalCode\":\"00-001\"", "\"postalCode\":1")
                .replace("\"tier\":\"GOLD\",", "")
                .replace("\"status\":\"ACTIVE\"", "\"status\":\"ON_HOLD\"")

        val report = detector.inspect(SchemaContracts.CUSTOMERS, json.readTree(payload))

        assertThat(report.findings.map { it.kind to it.field }).containsExactlyInAnyOrder(
            DriftKind.FIELD_RETYPED to "address.postalCode",
            DriftKind.FIELD_MISSING to "tier",
            DriftKind.VALUE_OUT_OF_DOMAIN to "status",
        )
        assertThat(report.findings).allMatch { it.severity == DriftSeverity.BLOCKING }
        assertThat(report.findings.first { it.kind == DriftKind.FIELD_RETYPED }.actual).isEqualTo("NUMBER")
    }

    @Test
    fun `an added field is a warning and does not block loading`() {
        val payload = CUSTOMER.replace("\"deleted\":false", "\"deleted\":false,\"loyaltyPoints\":120")

        val report = detector.inspect(SchemaContracts.CUSTOMERS, json.readTree(payload))

        val finding = report.findings.single()
        assertThat(finding.kind).isEqualTo(DriftKind.FIELD_ADDED)
        assertThat(finding.field).isEqualTo("loyaltyPoints")
        assertThat(finding.severity).isEqualTo(DriftSeverity.WARNING)
        assertThat(report.isBlocking).isFalse()
    }

    @Test
    fun `optional fields may be absent and nested unknown fields are found`() {
        val payload =
            CUSTOMER
                .replace("\"line2\":\"Apt 3\",", "")
                .replace("\"tags\":[\"vip\"],", "")
                .replace("\"country\":\"PL\"", "\"country\":\"PL\",\"geo\":{\"lat\":52.2}")

        val report = detector.inspect(SchemaContracts.CUSTOMERS, json.readTree(payload))

        assertThat(report.findings.map { it.kind to it.field }).containsExactly(DriftKind.FIELD_ADDED to "address.geo")
    }

    @Test
    fun `orders contract flags an unknown status value`() {
        val order =
            """{"id":"ord_1","customerId":"cus_1","status":"ON_HOLD","total":{"amount":"1.00","currency":"EUR"},
               "lines":[],"placedAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-01T00:00:00Z","deleted":false}"""

        val report = detector.inspect(SchemaContracts.ORDERS, json.readTree(order))

        assertThat(report.findings.single().kind).isEqualTo(DriftKind.VALUE_OUT_OF_DOMAIN)
        assertThat(report.findings.single().actual).isEqualTo("ON_HOLD")
    }

    companion object {
        private const val CUSTOMER =
            """{"id":"cus_1","email":"a@b.c","firstName":"Anna","lastName":"Nowak","status":"ACTIVE","tier":"GOLD",
               "address":{"line1":"1 Street","line2":"Apt 3","city":"Warsaw","postalCode":"00-001","country":"PL"},
               "tags":["vip"],"createdAt":"2026-01-01T00:00:00Z","updatedAt":"2026-01-02T00:00:00Z","deleted":false}"""
    }
}
