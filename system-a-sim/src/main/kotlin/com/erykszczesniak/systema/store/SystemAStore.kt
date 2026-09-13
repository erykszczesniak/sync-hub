package com.erykszczesniak.systema.store

import com.erykszczesniak.systema.model.Customer
import com.erykszczesniak.systema.model.Order
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/** The whole state of System A: two collections plus id sequences. */
@Component
class SystemAStore {
    val customers = ChangeFeedStore<Customer>()
    val orders = ChangeFeedStore<Order>()
    private val customerSeq = AtomicLong(0)
    private val orderSeq = AtomicLong(0)

    fun nextCustomerId(): String = "cus_%05d".format(customerSeq.incrementAndGet())

    fun nextOrderId(): String = "ord_%06d".format(orderSeq.incrementAndGet())

    fun reset() {
        customers.clear()
        orders.clear()
        customerSeq.set(0)
        orderSeq.set(0)
    }
}
