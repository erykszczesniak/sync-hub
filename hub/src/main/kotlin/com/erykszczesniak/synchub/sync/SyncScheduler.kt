package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.repository.SyncTrigger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Incremental sync of every feed on a fixed delay (`synchub.sync.interval`). */
@Component
@ConditionalOnProperty(prefix = "synchub.sync", name = ["scheduled"], havingValue = "true", matchIfMissing = true)
class SyncScheduler(
    private val registry: FeedRegistry,
    private val orchestrator: SyncOrchestrator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${synchub.sync.interval:60s}",
        initialDelayString = "\${synchub.sync.initial-delay:15s}",
    )
    fun syncAllFeeds() {
        registry.names.forEach { feed ->
            try {
                orchestrator.runIncremental(feed, SyncTrigger.SCHEDULED)
            } catch (ex: FeedBusyException) {
                log.info("Skipping scheduled sync: {}", ex.message)
            } catch (
                @Suppress("TooGenericExceptionCaught") ex: RuntimeException,
            ) {
                log.error("Scheduled sync of '{}' failed", feed, ex)
            }
        }
    }
}
