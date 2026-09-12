package com.erykszczesniak.synchub.sync

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "synchub.sync")
data class SyncProperties(
    /** How far before the watermark each incremental run starts reading; see [WatermarkStore]. */
    val watermarkOverlap: Duration = DEFAULT_OVERLAP,
) {
    companion object {
        val DEFAULT_OVERLAP: Duration = Duration.ofSeconds(5)
    }
}
