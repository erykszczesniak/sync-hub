package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.config.OpenApiConfig
import com.erykszczesniak.synchub.repository.SyncRunEntity
import com.erykszczesniak.synchub.repository.SyncTrigger
import com.erykszczesniak.synchub.sync.SyncOrchestrator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class BackfillRequest(
    @field:NotNull val from: Instant,
    @field:NotNull val to: Instant,
)

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Sync control", description = "Trigger syncs on demand (HTTP Basic, operator account)")
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH)
class SyncControlController(
    private val orchestrator: SyncOrchestrator,
) {
    @PostMapping("/{feed}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Run an incremental sync of a feed now",
        description = "Synchronous: returns the finished run.",
    )
    fun run(
        @PathVariable feed: String,
    ): SyncRunDto = orchestrator.runIncremental(feed, SyncTrigger.MANUAL).toDto()

    @PostMapping("/{feed}/backfill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Re-sync a window of a feed",
        description =
            "Extracts every record with updatedAt in (from, to] and merges it idempotently; nothing is duplicated " +
                "and the watermark is not moved. A clean backfill resolves the feed's open drift events.",
    )
    fun backfill(
        @PathVariable feed: String,
        @Valid @RequestBody body: BackfillRequest,
    ): SyncRunDto = orchestrator.runBackfill(feed, body.from, body.to).toDto()
}

data class SyncRunDto(
    val id: String,
    val feed: String,
    val mode: String,
    val trigger: String,
    val status: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val durationMs: Long?,
    val extracted: Int,
    val transformed: Int,
    val loaded: Int,
    val skipped: Int,
    val quarantined: Int,
    val failed: Int,
    val driftDetected: Boolean,
    val watermarkBefore: Instant?,
    val watermarkAfter: Instant?,
    val backfillFrom: Instant?,
    val backfillTo: Instant?,
    val maxLagSeconds: Long?,
    val errorMessage: String?,
)

fun SyncRunEntity.toDto() =
    SyncRunDto(
        id = id.toString(),
        feed = feed,
        mode = mode.name,
        trigger = trigger.name,
        status = status.name,
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMs =
            finishedAt?.let {
                java.time.Duration
                    .between(startedAt, it)
                    .toMillis()
            },
        extracted = extracted,
        transformed = transformed,
        loaded = loaded,
        skipped = skipped,
        quarantined = quarantined,
        failed = failed,
        driftDetected = driftDetected,
        watermarkBefore = watermarkBefore,
        watermarkAfter = watermarkAfter,
        backfillFrom = backfillFrom,
        backfillTo = backfillTo,
        maxLagSeconds = maxLagSeconds,
        errorMessage = errorMessage,
    )
