package com.erykszczesniak.systema.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "system-a")
data class SystemAProperties(
    /** API key every client must send in the `X-API-Key` header. */
    val apiKey: String,
    val seed: Seed = Seed(),
) {
    data class Seed(
        val customers: Int = 50,
        val orders: Int = 150,
        /** Random seed so every start produces the same data set. */
        val random: Long = 42L,
        /** How many days back the seeded `updatedAt` timestamps are spread. */
        val historyDays: Long = 30L,
    )
}
