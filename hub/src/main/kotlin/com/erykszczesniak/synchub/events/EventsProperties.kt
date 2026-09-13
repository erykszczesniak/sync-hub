package com.erykszczesniak.synchub.events

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "synchub.events")
data class EventsProperties(
    /** `kafka` or `log`. */
    val transport: String = "log",
    val topicPrefix: String = "synchub.changes",
    val publishInterval: Duration = Duration.ofSeconds(1),
    val partitions: Int = 3,
) {
    fun topicFor(feed: String) = "$topicPrefix.$feed"
}
