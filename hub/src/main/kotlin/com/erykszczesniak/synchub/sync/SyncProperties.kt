package com.erykszczesniak.synchub.sync

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "synchub.sync")
data class SyncProperties(
    /** How far before the watermark each incremental run starts reading; see [WatermarkStore]. */
    val watermarkOverlap: Duration = DEFAULT_OVERLAP,
    /** Whether the scheduler runs incremental syncs on its own. */
    val scheduled: Boolean = true,
    /** Delay between the end of one scheduled pass and the start of the next. */
    val interval: Duration = DEFAULT_INTERVAL,
    val initialDelay: Duration = DEFAULT_INITIAL_DELAY,
) {
    companion object {
        val DEFAULT_OVERLAP: Duration = Duration.ofSeconds(5)
        val DEFAULT_INTERVAL: Duration = Duration.ofSeconds(60)
        val DEFAULT_INITIAL_DELAY: Duration = Duration.ofSeconds(15)
    }
}
