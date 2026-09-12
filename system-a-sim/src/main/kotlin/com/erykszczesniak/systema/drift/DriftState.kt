package com.erykszczesniak.systema.drift

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArraySet

/** Which drift scenarios are currently switched on. Thread-safe, read on every response. */
@Component
class DriftState {
    private val active = CopyOnWriteArraySet<DriftScenario>()

    fun active(): Set<DriftScenario> = active.toSortedSet()

    fun isActive(scenario: DriftScenario): Boolean = scenario in active

    fun replace(scenarios: Collection<DriftScenario>) {
        active.clear()
        active.addAll(scenarios)
    }

    fun clear() = active.clear()
}
