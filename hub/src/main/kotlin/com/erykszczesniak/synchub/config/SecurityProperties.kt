package com.erykszczesniak.synchub.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** Operator credentials for the control endpoints; see `.env.example` for the environment variables. */
@ConfigurationProperties(prefix = "synchub.security")
data class SecurityProperties(
    val admin: Admin,
) {
    data class Admin(
        val username: String,
        val password: String,
    )
}
