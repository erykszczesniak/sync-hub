package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.repository.SystemBCustomerEntity
import com.erykszczesniak.synchub.repository.SystemBCustomerRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class CustomerSinkLoader(
    private val repository: SystemBCustomerRepository,
) : AbstractSinkLoader<SystemBCustomerRecord, SystemBCustomerEntity>() {
    override val feed: String = "customers"

    override fun find(businessKey: String) = repository.findByBusinessKey(businessKey)

    override fun save(entity: SystemBCustomerEntity) = repository.save(entity)

    override fun create(
        record: SystemBCustomerRecord,
        fingerprint: String,
        now: Instant,
    ) = SystemBCustomerEntity(
        businessKey = record.customerKey,
        sourceUpdatedAt = record.sourceUpdatedAt,
        contentFingerprint = fingerprint,
        firstSyncedAt = now,
        lastSyncedAt = now,
        emailAddress = record.emailAddress,
        fullName = record.fullName,
        givenName = record.givenName,
        familyName = record.familyName,
        lifecycleCode = record.lifecycleCode,
        tierRank = record.tierRank,
        addressLine = record.addressLine,
        city = record.city,
        postalCode = record.postalCode,
        countryCode = record.countryCode,
        tagsCsv = record.tagsCsv,
        sourceCreatedAt = record.sourceCreatedAt,
    )

    override fun apply(
        entity: SystemBCustomerEntity,
        record: SystemBCustomerRecord,
    ) {
        entity.emailAddress = record.emailAddress
        entity.fullName = record.fullName
        entity.givenName = record.givenName
        entity.familyName = record.familyName
        entity.lifecycleCode = record.lifecycleCode
        entity.tierRank = record.tierRank
        entity.addressLine = record.addressLine
        entity.city = record.city
        entity.postalCode = record.postalCode
        entity.countryCode = record.countryCode
        entity.tagsCsv = record.tagsCsv
        entity.sourceCreatedAt = record.sourceCreatedAt
    }

    override fun toRecord(entity: SystemBCustomerEntity) =
        SystemBCustomerRecord(
            customerKey = entity.businessKey,
            emailAddress = entity.emailAddress,
            fullName = entity.fullName,
            givenName = entity.givenName,
            familyName = entity.familyName,
            lifecycleCode = entity.lifecycleCode,
            tierRank = entity.tierRank,
            addressLine = entity.addressLine,
            city = entity.city,
            postalCode = entity.postalCode,
            countryCode = entity.countryCode,
            tagsCsv = entity.tagsCsv,
            sourceCreatedAt = entity.sourceCreatedAt,
            sourceUpdatedAt = entity.sourceUpdatedAt,
        )
}
