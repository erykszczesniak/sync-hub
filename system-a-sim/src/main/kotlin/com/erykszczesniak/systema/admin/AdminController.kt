package com.erykszczesniak.systema.admin

import com.erykszczesniak.systema.config.SystemAProperties
import com.erykszczesniak.systema.drift.DriftScenario
import com.erykszczesniak.systema.drift.DriftState
import com.erykszczesniak.systema.model.Address
import com.erykszczesniak.systema.model.Customer
import com.erykszczesniak.systema.model.CustomerStatus
import com.erykszczesniak.systema.model.CustomerTier
import com.erykszczesniak.systema.model.Order
import com.erykszczesniak.systema.model.OrderStatus
import com.erykszczesniak.systema.store.ChangeFeedStore
import com.erykszczesniak.systema.store.DataSeeder
import com.erykszczesniak.systema.store.SystemAStore
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import kotlin.random.Random

data class CustomerPatch(
    @field:Email val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val status: CustomerStatus? = null,
    val tier: CustomerTier? = null,
    val address: Address? = null,
    val tags: List<String>? = null,
)

data class NewCustomer(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val firstName: String,
    @field:NotBlank val lastName: String,
    val status: CustomerStatus = CustomerStatus.ACTIVE,
    val tier: CustomerTier = CustomerTier.BRONZE,
    val address: Address,
    val tags: List<String> = emptyList(),
)

data class OrderPatch(
    val status: OrderStatus? = null,
)

data class DriftRequest(
    val scenarios: Set<DriftScenario>,
)

data class DriftResponse(
    val active: Set<DriftScenario>,
    val available: Map<DriftScenario, String>,
)

data class Stats(
    val customers: Int,
    val orders: Int,
    val latestCustomerUpdatedAt: Instant?,
    val latestOrderUpdatedAt: Instant?,
    val activeDrift: Set<DriftScenario>,
)

data class ActivityResult(
    val customersUpdated: List<String>,
    val ordersUpdated: List<String>,
)

/**
 * Demo controls: they are what a person clicks (or curls) to make System A change so the hub has
 * something to sync. Not part of the "real" source API, hence the separate prefix.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin (demo controls)", description = "Mutate System A and toggle schema drift; requires X-API-Key")
class AdminController(
    private val store: SystemAStore,
    private val seeder: DataSeeder,
    private val driftState: DriftState,
    private val properties: SystemAProperties,
) {
    @GetMapping("/stats")
    fun stats() =
        Stats(
            customers = store.customers.size(),
            orders = store.orders.size(),
            latestCustomerUpdatedAt = store.customers.latestUpdatedAt(),
            latestOrderUpdatedAt = store.orders.latestUpdatedAt(),
            activeDrift = driftState.active(),
        )

    @PostMapping("/reset")
    @Operation(summary = "Reset to the deterministic seed data and switch all drift off")
    fun reset(
        @RequestParam(required = false) customers: Int?,
        @RequestParam(required = false) orders: Int?,
    ): Stats {
        driftState.clear()
        seeder.seed(customers ?: properties.seed.customers, orders ?: properties.seed.orders)
        return stats()
    }

    @PostMapping("/customers")
    fun createCustomer(
        @Valid @RequestBody body: NewCustomer,
    ): Customer {
        val now = ChangeFeedStore.now()
        return store.customers.put(
            Customer(
                id = store.nextCustomerId(),
                email = body.email,
                firstName = body.firstName,
                lastName = body.lastName,
                status = body.status,
                tier = body.tier,
                address = body.address,
                tags = body.tags,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @PatchMapping("/customers/{id}")
    @Operation(summary = "Change a customer; bumps updatedAt so the change feed picks it up")
    fun patchCustomer(
        @PathVariable id: String,
        @Valid @RequestBody patch: CustomerPatch,
    ): Customer {
        val current = store.customers.get(id) ?: throw NoSuchElementException("customer $id not found")
        return store.customers.put(
            current.copy(
                email = patch.email ?: current.email,
                firstName = patch.firstName ?: current.firstName,
                lastName = patch.lastName ?: current.lastName,
                status = patch.status ?: current.status,
                tier = patch.tier ?: current.tier,
                address = patch.address ?: current.address,
                tags = patch.tags ?: current.tags,
                updatedAt = ChangeFeedStore.now(),
                deleted = false,
            ),
        )
    }

    @DeleteMapping("/customers/{id}")
    @Operation(summary = "Soft-delete a customer (tombstone stays in the change feed)")
    fun deleteCustomer(
        @PathVariable id: String,
    ): Customer {
        val current = store.customers.get(id) ?: throw NoSuchElementException("customer $id not found")
        return store.customers.put(current.copy(deleted = true, updatedAt = ChangeFeedStore.now()))
    }

    @PatchMapping("/orders/{id}")
    fun patchOrder(
        @PathVariable id: String,
        @RequestBody patch: OrderPatch,
    ): Order {
        val current = store.orders.get(id) ?: throw NoSuchElementException("order $id not found")
        return store.orders.put(
            current.copy(status = patch.status ?: current.status, updatedAt = ChangeFeedStore.now(), deleted = false),
        )
    }

    @DeleteMapping("/orders/{id}")
    fun deleteOrder(
        @PathVariable id: String,
    ): Order {
        val current = store.orders.get(id) ?: throw NoSuchElementException("order $id not found")
        return store.orders.put(current.copy(deleted = true, updatedAt = ChangeFeedStore.now()))
    }

    @PostMapping("/activity")
    @Operation(summary = "Simulate business activity: random customer edits and order status changes")
    fun activity(
        @RequestParam(defaultValue = "3") customers: Int,
        @RequestParam(defaultValue = "5") orders: Int,
        @RequestParam(required = false) seed: Long?,
    ): ActivityResult {
        val random = seed?.let { Random(it) } ?: Random.Default
        val now = ChangeFeedStore.now()
        val touchedCustomers =
            store.customers
                .all()
                .filterNot { it.deleted }
                .shuffled(random)
                .take(customers)
                .map { c ->
                    val next = CustomerTier.entries[(c.tier.ordinal + 1) % CustomerTier.entries.size]
                    store.customers.put(c.copy(tier = next, tags = (c.tags + "updated").distinct(), updatedAt = now)).id
                }
        val touchedOrders =
            store.orders
                .all()
                .filterNot { it.deleted }
                .shuffled(random)
                .take(orders)
                .map { o ->
                    val next = OrderStatus.entries[(o.status.ordinal + 1) % OrderStatus.entries.size]
                    store.orders.put(o.copy(status = next, updatedAt = now)).id
                }
        return ActivityResult(touchedCustomers, touchedOrders)
    }

    @GetMapping("/drift")
    fun drift() = DriftResponse(driftState.active(), DriftScenario.entries.associateWith { it.description })

    @PutMapping("/drift")
    @Operation(summary = "Replace the set of active schema-drift scenarios (empty set = no drift)")
    fun setDrift(
        @RequestBody body: DriftRequest,
    ): DriftResponse {
        driftState.replace(body.scenarios)
        return drift()
    }
}
