package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.repository.DriftStatus
import com.erykszczesniak.synchub.repository.QuarantineStatus
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Read-only, unauthenticated: what the dashboard shows. */
@RestController
@RequestMapping("/api/status")
@Validated
@Tag(name = "Status", description = "Read-only health, run history, quarantine and drift views (no auth)")
class StatusController(
    private val status: StatusService,
) {
    @GetMapping("/overview")
    @Operation(summary = "Everything the dashboard's home page needs in one call, including MTTD/MTTR")
    fun overview(): OverviewDto = status.overview()

    @GetMapping("/feeds")
    @Operation(summary = "Health of every feed: last run, freshness, lag, watermark, open drift and quarantine")
    fun feeds(): List<FeedHealthDto> = status.feeds()

    @GetMapping("/feeds/{feed}")
    fun feed(
        @PathVariable feed: String,
    ): FeedHealthDto = status.feed(feed)

    @GetMapping("/runs")
    @Operation(summary = "Run history, newest first")
    fun runs(
        @RequestParam(required = false) feed: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) size: Int,
    ): PageDto<SyncRunDto> = status.runs(feed, page, size)

    @GetMapping("/runs/{id}")
    fun run(
        @PathVariable id: UUID,
    ): SyncRunDto = status.run(id)

    @GetMapping("/quarantine")
    @Operation(summary = "Quarantined records with their raw payload and the reason")
    fun quarantine(
        @RequestParam(required = false) feed: String?,
        @RequestParam(defaultValue = "OPEN") status: QuarantineStatus,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) size: Int,
    ): PageDto<QuarantinedRecordDto> = this.status.quarantine(feed, status, page, size)

    @GetMapping("/drift")
    @Operation(summary = "Schema-drift events with time-to-detect and time-to-resolve")
    fun drift(
        @RequestParam(required = false) status: DriftStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(200) size: Int,
    ): PageDto<DriftEventDto> = this.status.driftEvents(status, page, size)
}
