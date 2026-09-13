package com.erykszczesniak.synchub.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Scheduling can be switched off (tests, one-off CLI runs) with `synchub.scheduling.enabled=false`. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "synchub.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig
