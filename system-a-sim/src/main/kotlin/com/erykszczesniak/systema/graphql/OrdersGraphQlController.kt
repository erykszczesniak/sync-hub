package com.erykszczesniak.systema.graphql

import com.erykszczesniak.systema.drift.DriftMutator
import com.erykszczesniak.systema.model.Order
import com.erykszczesniak.systema.store.ChangeFeedStore
import com.erykszczesniak.systema.store.SystemAStore
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller
import java.time.Instant

data class MoneyNode(
    val amount: String,
    val currency: String,
)

data class OrderLineNode(
    val sku: String,
    val quantity: Int,
    val unitPrice: String,
)

data class OrderNode(
    val id: String,
    val customerId: String,
    val status: String,
    val total: MoneyNode,
    val lines: List<OrderLineNode>,
    val placedAt: String,
    val updatedAt: String,
    val deleted: Boolean,
)

data class OrderEdge(
    val cursor: String,
    val node: OrderNode,
)

data class PageInfo(
    val endCursor: String?,
    val hasNextPage: Boolean,
)

data class OrderConnection(
    val edges: List<OrderEdge>,
    val pageInfo: PageInfo,
)

@Controller
class OrdersGraphQlController(
    private val store: SystemAStore,
    private val drift: DriftMutator,
) {
    @QueryMapping
    fun orders(
        @Argument updatedSince: String?,
        @Argument updatedUntil: String?,
        @Argument after: String?,
        @Argument first: Int?,
    ): OrderConnection {
        val limit = (first ?: DEFAULT_PAGE).coerceIn(1, MAX_PAGE)
        val page =
            store.orders.changes(
                updatedSince?.let(Instant::parse),
                updatedUntil?.let(Instant::parse),
                after,
                limit,
            )
        val edges = page.items.map { OrderEdge(ChangeFeedStore.Cursor.encode(it), toNode(it)) }
        return OrderConnection(edges, PageInfo(page.nextCursor, page.hasMore))
    }

    @QueryMapping
    fun order(
        @Argument id: String,
    ): OrderNode? = store.orders.get(id)?.let(::toNode)

    private fun toNode(order: Order) =
        OrderNode(
            id = order.id,
            customerId = order.customerId,
            status = drift.orderStatus(order),
            total = MoneyNode(order.total.amount.toPlainString(), order.total.currency),
            lines = order.lines.map { OrderLineNode(it.sku, it.quantity, it.unitPrice.toPlainString()) },
            placedAt = order.placedAt.toString(),
            updatedAt = order.updatedAt.toString(),
            deleted = order.deleted,
        )

    companion object {
        private const val DEFAULT_PAGE = 100
        private const val MAX_PAGE = 500
    }
}
