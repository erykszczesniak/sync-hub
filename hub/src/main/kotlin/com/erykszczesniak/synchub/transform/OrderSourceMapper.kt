package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.CanonicalMoney
import com.erykszczesniak.synchub.canonical.CanonicalOrder
import com.erykszczesniak.synchub.canonical.CanonicalOrderLine
import com.erykszczesniak.synchub.canonical.OrderState
import com.erykszczesniak.synchub.source.SourceRecord
import org.springframework.stereotype.Component
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
            total = CanonicalMoney(toMinor(reader, "total.amount"), reader.text("total.currency").uppercase()),
            lines =
                reader.objects("lines").map { line ->
                    CanonicalOrderLine(
                        sku = line.text("sku"),
                        quantity = line.int("quantity"),
                        unitPriceMinor = toMinor(line, "unitPrice"),
                    )
                },
            placedAt = reader.instant("placedAt"),
            updatedAt = reader.instant("updatedAt"),
        )

    /** Decimal string → minor units. More than two decimals cannot be represented and is reported, not rounded. */
    private fun toMinor(
        reader: PayloadReader,
        path: String,
    ): Long =
        try {
            reader
                .decimal(path)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
        } catch (ex: ArithmeticException) {
            reader.report(path, "must have at most two decimal places and fit in minor units (${ex.message})")
            0L
        }
}
