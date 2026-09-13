package com.erykszczesniak.systema.store

import com.erykszczesniak.systema.config.SystemAProperties
import com.erykszczesniak.systema.model.Address
import com.erykszczesniak.systema.model.Customer
import com.erykszczesniak.systema.model.CustomerStatus
import com.erykszczesniak.systema.model.CustomerTier
import com.erykszczesniak.systema.model.Money
import com.erykszczesniak.systema.model.Order
import com.erykszczesniak.systema.model.OrderLine
import com.erykszczesniak.systema.model.OrderStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Deterministic seed data so demos and tests see the same System A every time. */
@Suppress("MagicNumber") // a generator of sample data is nothing but literal ranges
@Component
class DataSeeder(
    private val store: SystemAStore,
    private val properties: SystemAProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun seedOnStartup() {
        seed(properties.seed.customers, properties.seed.orders)
    }

    fun seed(
        customerCount: Int,
        orderCount: Int,
    ) {
        store.reset()
        val random = Random(properties.seed.random)
        val end = ChangeFeedStore.now()
        val start = end.minus(Duration.ofDays(properties.seed.historyDays))

        repeat(customerCount) { store.customers.put(randomCustomer(random, start, end)) }
        val customerIds = store.customers.all().map { it.id }
        repeat(orderCount) { store.orders.put(randomOrder(random, customerIds, start, end)) }
        log.info("Seeded System A with {} customers and {} orders", customerCount, orderCount)
    }

    fun randomCustomer(
        random: Random,
        start: Instant,
        end: Instant,
    ): Customer {
        val id = store.nextCustomerId()
        val first = FIRST_NAMES.random(random)
        val last = LAST_NAMES.random(random)
        val created = randomInstant(random, start, end)
        val updated = randomInstant(random, created, end)
        val city = CITIES.random(random)
        return Customer(
            id = id,
            email = "${first.lowercase()}.${last.lowercase()}.${id.removePrefix("cus_")}@example.com",
            firstName = first,
            lastName = last,
            status =
                weighted(
                    random,
                    CustomerStatus.ACTIVE to 8,
                    CustomerStatus.INACTIVE to 1,
                    CustomerStatus.CHURNED to 1,
                ),
            tier = CustomerTier.entries.random(random),
            address =
                Address(
                    line1 = "${random.nextInt(1, 200)} ${STREETS.random(random)}",
                    line2 = if (random.nextInt(4) == 0) "Apt ${random.nextInt(1, 40)}" else null,
                    city = city.first,
                    postalCode = "%02d-%03d".format(random.nextInt(100), random.nextInt(1000)),
                    country = city.second,
                ),
            tags = TAGS.shuffled(random).take(random.nextInt(0, 3)),
            createdAt = created,
            updatedAt = updated,
        )
    }

    fun randomOrder(
        random: Random,
        customerIds: List<String>,
        start: Instant,
        end: Instant,
    ): Order {
        val lines =
            List(random.nextInt(1, 4)) {
                OrderLine(
                    sku = SKUS.random(random),
                    quantity = random.nextInt(1, 5),
                    unitPrice = BigDecimal(random.nextInt(500, 25000)).movePointLeft(2),
                )
            }
        val total =
            lines
                .fold(BigDecimal.ZERO) { acc, l -> acc + l.unitPrice.multiply(BigDecimal(l.quantity)) }
                .setScale(2, RoundingMode.HALF_UP)
        val placed = randomInstant(random, start, end)
        return Order(
            id = store.nextOrderId(),
            customerId = customerIds.random(random),
            status = OrderStatus.entries.random(random),
            total = Money(total, "EUR"),
            lines = lines,
            placedAt = placed,
            updatedAt = randomInstant(random, placed, end),
        )
    }

    private fun randomInstant(
        random: Random,
        from: Instant,
        to: Instant,
    ): Instant {
        val span = Duration.between(from, to).toMillis().coerceAtLeast(1)
        return from.plusMillis(random.nextLong(span)).truncatedTo(ChronoUnit.MILLIS)
    }

    private fun <T> weighted(
        random: Random,
        vararg options: Pair<T, Int>,
    ): T {
        var roll = random.nextInt(options.sumOf { it.second })
        for ((value, weight) in options) {
            if (roll < weight) return value
            roll -= weight
        }
        return options.last().first
    }

    companion object {
        private val FIRST_NAMES =
            listOf("Anna", "Jan", "Maria", "Piotr", "Ewa", "Tomasz", "Kasia", "Marek", "Ola", "Adam")
        private val LAST_NAMES =
            listOf("Nowak", "Kowalski", "Wisniewska", "Wojcik", "Kaminski", "Lewandowska", "Zielinski")
        private val STREETS = listOf("Long Street", "Market Square", "Harbour Road", "Mill Lane", "Station Avenue")
        private val CITIES =
            listOf(
                "Warsaw" to "PL",
                "Gdansk" to "PL",
                "Berlin" to "DE",
                "Prague" to "CZ",
                "Vienna" to "AT",
            )
        private val TAGS = listOf("newsletter", "vip", "wholesale", "trial", "referral")
        private val SKUS =
            listOf("SKU-BIKE-01", "SKU-HELMET-02", "SKU-WETSUIT-03", "SKU-SHOES-04", "SKU-GOGGLES-05", "SKU-WATCH-06")
    }
}
