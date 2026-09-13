package com.erykszczesniak.synchub.repository

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/** Sync bookkeeping every System B row carries; see V2__system_b.sql. */
@MappedSuperclass
abstract class SystemBEntity(
    @Id
    @Column(name = "id")
    val id: UUID = UUID.randomUUID(),
    @Column(name = "business_key", nullable = false, length = 128)
    val businessKey: String,
    @Column(name = "source_updated_at", nullable = false)
    var sourceUpdatedAt: Instant,
    @Column(name = "content_fingerprint", nullable = false, length = 64)
    var contentFingerprint: String,
    @Column(name = "version", nullable = false)
    var version: Int = 1,
    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
    @Column(name = "first_synced_at", nullable = false)
    val firstSyncedAt: Instant,
    @Column(name = "last_synced_at", nullable = false)
    var lastSyncedAt: Instant,
)

@Entity
@Table(name = "b_customer")
class SystemBCustomerEntity(
    id: UUID = UUID.randomUUID(),
    businessKey: String,
    sourceUpdatedAt: Instant,
    contentFingerprint: String,
    version: Int = 1,
    deletedAt: Instant? = null,
    firstSyncedAt: Instant,
    lastSyncedAt: Instant,
    @Column(name = "email_address", nullable = false, length = 320)
    var emailAddress: String,
    @Column(name = "full_name", nullable = false, length = 200)
    var fullName: String,
    @Column(name = "given_name", nullable = false, length = 100)
    var givenName: String,
    @Column(name = "family_name", nullable = false, length = 100)
    var familyName: String,
    @Column(name = "lifecycle_code", nullable = false, length = 1)
    var lifecycleCode: String,
    @Column(name = "tier_rank", nullable = false)
    var tierRank: Int,
    @Column(name = "address_line", nullable = false, length = 300)
    var addressLine: String,
    @Column(name = "city", nullable = false, length = 100)
    var city: String,
    @Column(name = "postal_code", nullable = false, length = 20)
    var postalCode: String,
    @Column(name = "country_code", nullable = false, length = 2)
    var countryCode: String,
    @Column(name = "tags_csv", nullable = false, length = 500)
    var tagsCsv: String,
    @Column(name = "source_created_at", nullable = false)
    var sourceCreatedAt: Instant,
) : SystemBEntity(id, businessKey, sourceUpdatedAt, contentFingerprint, version, deletedAt, firstSyncedAt, lastSyncedAt)

@Entity
@Table(name = "b_order")
class SystemBOrderEntity(
    id: UUID = UUID.randomUUID(),
    businessKey: String,
    sourceUpdatedAt: Instant,
    contentFingerprint: String,
    version: Int = 1,
    deletedAt: Instant? = null,
    firstSyncedAt: Instant,
    lastSyncedAt: Instant,
    @Column(name = "customer_key", nullable = false, length = 128)
    var customerKey: String,
    @Column(name = "state_code", nullable = false, length = 3)
    var stateCode: String,
    @Column(name = "total_minor", nullable = false)
    var totalMinor: Long,
    @Column(name = "currency", nullable = false, length = 3)
    var currency: String,
    @Column(name = "line_count", nullable = false)
    var lineCount: Int,
    @Column(name = "lines_json", nullable = false, columnDefinition = "TEXT")
    var linesJson: String,
    @Column(name = "placed_at", nullable = false)
    var placedAt: Instant,
) : SystemBEntity(id, businessKey, sourceUpdatedAt, contentFingerprint, version, deletedAt, firstSyncedAt, lastSyncedAt)

interface SystemBCustomerRepository : JpaRepository<SystemBCustomerEntity, UUID> {
    fun findByBusinessKey(businessKey: String): SystemBCustomerEntity?

    fun countByDeletedAtIsNull(): Long
}

interface SystemBOrderRepository : JpaRepository<SystemBOrderEntity, UUID> {
    fun findByBusinessKey(businessKey: String): SystemBOrderEntity?

    fun countByDeletedAtIsNull(): Long
}
