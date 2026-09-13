package com.erykszczesniak.synchub.sink

import com.erykszczesniak.synchub.repository.SystemBOrderEntity
import com.erykszczesniak.synchub.repository.SystemBOrderRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
@Transactional
class OrderSinkLoader(
    private val repository: SystemBOrderRepository,
) : AbstractSinkLoader<SystemBOrderRecord, SystemBOrderEntity>() {
    override val feed: String = "orders"

    override fun find(businessKey: String) = repository.findByBusinessKey(businessKey)

    override fun save(entity: SystemBOrderEntity) = repository.save(entity)

    override fun create(
        record: SystemBOrderRecord,
        fingerprint: String,
        now: Instant,
    ) = SystemBOrderEntity(
        businessKey = record.orderKey,
        sourceUpdatedAt = record.sourceUpdatedAt,
        contentFingerprint = fingerprint,
        firstSyncedAt = now,
        lastSyncedAt = now,
        customerKey = record.customerKey,
        stateCode = record.stateCode,
        totalMinor = record.totalMinor,
        currency = record.currency,
        lineCount = record.lineCount,
        linesJson = record.linesJson,
        placedAt = record.placedAt,
    )

    override fun apply(
        entity: SystemBOrderEntity,
        record: SystemBOrderRecord,
    ) {
        entity.customerKey = record.customerKey
        entity.stateCode = record.stateCode
        entity.totalMinor = record.totalMinor
        entity.currency = record.currency
        entity.lineCount = record.lineCount
        entity.linesJson = record.linesJson
        entity.placedAt = record.placedAt
    }

    override fun toRecord(entity: SystemBOrderEntity) =
        SystemBOrderRecord(
            orderKey = entity.businessKey,
            customerKey = entity.customerKey,
            stateCode = entity.stateCode,
            totalMinor = entity.totalMinor,
            currency = entity.currency,
            lineCount = entity.lineCount,
            linesJson = entity.linesJson,
            placedAt = entity.placedAt,
            sourceUpdatedAt = entity.sourceUpdatedAt,
        )
}
