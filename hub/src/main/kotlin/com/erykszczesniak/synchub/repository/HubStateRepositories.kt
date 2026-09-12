package com.erykszczesniak.synchub.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeedWatermarkRepository : JpaRepository<FeedWatermarkEntity, String>

interface SyncRunRepository : JpaRepository<SyncRunEntity, UUID> {
    fun findByFeedOrderByStartedAtDesc(
        feed: String,
        pageable: Pageable,
    ): Page<SyncRunEntity>

    fun findAllByOrderByStartedAtDesc(pageable: Pageable): Page<SyncRunEntity>

    fun findFirstByFeedAndStatusOrderByStartedAtDesc(
        feed: String,
        status: SyncRunStatus,
    ): SyncRunEntity?

    fun findFirstByFeedOrderByStartedAtDesc(feed: String): SyncRunEntity?

    fun existsByFeedAndStatus(
        feed: String,
        status: SyncRunStatus,
    ): Boolean
}

interface QuarantinedRecordRepository : JpaRepository<QuarantinedRecordEntity, UUID> {
    fun findByStatusOrderByQuarantinedAtDesc(
        status: QuarantineStatus,
        pageable: Pageable,
    ): Page<QuarantinedRecordEntity>

    fun findByFeedAndStatusOrderByQuarantinedAtDesc(
        feed: String,
        status: QuarantineStatus,
        pageable: Pageable,
    ): Page<QuarantinedRecordEntity>

    fun findByFeedAndBusinessKeyAndStatus(
        feed: String,
        businessKey: String,
        status: QuarantineStatus,
    ): List<QuarantinedRecordEntity>

    fun countByFeedAndStatus(
        feed: String,
        status: QuarantineStatus,
    ): Long
}

interface DriftEventRepository : JpaRepository<DriftEventEntity, UUID> {
    fun findByFeedAndFingerprintAndStatus(
        feed: String,
        fingerprint: String,
        status: DriftStatus,
    ): DriftEventEntity?

    fun findByFeedAndStatus(
        feed: String,
        status: DriftStatus,
    ): List<DriftEventEntity>

    fun findByStatusOrderByDetectedAtDesc(
        status: DriftStatus,
        pageable: Pageable,
    ): Page<DriftEventEntity>

    fun findAllByOrderByDetectedAtDesc(pageable: Pageable): Page<DriftEventEntity>

    fun countByFeedAndStatus(
        feed: String,
        status: DriftStatus,
    ): Long
}
