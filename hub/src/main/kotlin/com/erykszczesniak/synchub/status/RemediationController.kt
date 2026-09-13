package com.erykszczesniak.synchub.status

import com.erykszczesniak.synchub.config.OpenApiConfig
import com.erykszczesniak.synchub.sync.DriftEventService
import com.erykszczesniak.synchub.sync.QuarantineService
import com.erykszczesniak.synchub.sync.SyncOrchestrator
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class NoteRequest(
    val note: String? = null,
)

/** What an operator does after drift or invalid data: replay, discard, resolve. Operator account required. */
@RestController
@RequestMapping("/api")
@Tag(name = "Remediation", description = "Replay or discard quarantined records, resolve drift events (HTTP Basic)")
@SecurityRequirement(name = OpenApiConfig.BASIC_AUTH)
class RemediationController(
    private val orchestrator: SyncOrchestrator,
    private val quarantine: QuarantineService,
    private val driftEvents: DriftEventService,
) {
    @PostMapping("/quarantine/{id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Replay a quarantined record through the pipeline",
        description =
            "Re-runs the stored payload as a REPLAY run. Loads it if the cause is fixed, " +
                "re-quarantines it otherwise.",
    )
    fun replay(
        @PathVariable id: UUID,
    ): SyncRunDto = orchestrator.replay(id).toDto()

    @PostMapping("/quarantine/{id}/discard")
    @Operation(summary = "Discard a quarantined record (it will not be loaded)")
    fun discard(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: NoteRequest?,
    ): QuarantinedRecordDto =
        (
            quarantine.discard(id, body?.note)
                ?: throw NoSuchElementException("Quarantined record $id not found")
        ).toDto()

    @PostMapping("/drift/{id}/resolve")
    @Operation(summary = "Mark a drift event resolved (closes the MTTR clock)")
    fun resolve(
        @PathVariable id: UUID,
        @RequestBody(required = false) body: NoteRequest?,
    ): DriftEventDto =
        (driftEvents.resolve(id, body?.note) ?: throw NoSuchElementException("Drift event $id not found")).toDto()
}
