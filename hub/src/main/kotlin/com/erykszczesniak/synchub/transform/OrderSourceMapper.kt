package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.CanonicalMoney
import com.erykszczesniak.synchub.canonical.CanonicalOrder
import com.erykszczesniak.synchub.canonical.CanonicalOrderLine
import com.erykszczesniak.synchub.canonical.OrderState
import com.erykszczesniak.synchub.source.SourceRecord
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * System A order (GraphQL node) → [CanonicalOrder].
 * Decimal strings become minor units; the total is reconciled by the canonical model.
 */
@Component
class OrderSourceMapper : SourceMapper<CanonicalOrder> {
    override val feed: String = "orders"

    override fun map(
        record: SourceRecord,
        reader: PayloadReader,
    ): CanonicalOrder =
        CanonicalOrder(
            businessKey = record.businessKey,
            customerKey = reader.text("customerId"),
            state = reader.enum<OrderState>("status"),
            total = CanonicalMoney(toMinor(reader.decimal("total.amount")), reader.text("total.currency").uppercase()),
            lines =
                reader.objects("lines").map { line ->
                    CanonicalOrderLine(
                        sku = line.text("sku"),
                        quantity = line.int("quantity"),
                        unitPriceMinor = toMinor(line.decimal("unitPrice")),
                    )
                },
            placedAt = reader.instant("placedAt"),
            updatedAt = reader.instant("updatedAt"),
        )

    private fun toMinor(amount: BigDecimal): Long =
        amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
}
