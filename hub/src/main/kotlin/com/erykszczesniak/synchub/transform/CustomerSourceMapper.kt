package com.erykszczesniak.synchub.transform

import com.erykszczesniak.synchub.canonical.CanonicalAddress
import com.erykszczesniak.synchub.canonical.CanonicalCustomer
import com.erykszczesniak.synchub.canonical.CustomerLifecycle
import com.erykszczesniak.synchub.canonical.LoyaltyTier
import com.erykszczesniak.synchub.source.SourceRecord
import org.springframework.stereotype.Component

/**
 * System A customer JSON → [CanonicalCustomer].
 * Field names on the left are System A's, on the right the canonical ones.
 */
@Component
class CustomerSourceMapper : SourceMapper<CanonicalCustomer> {
    override val feed: String = "customers"

    override fun map(
        record: SourceRecord,
        reader: PayloadReader,
    ): CanonicalCustomer {
        val line1 = reader.text("address.line1")
        val line2 = reader.textOrNull("address.line2")
        return CanonicalCustomer(
            businessKey = record.businessKey,
            email = reader.text("email").trim().lowercase(),
            givenName = reader.text("firstName").trim(),
            familyName = reader.text("lastName").trim(),
            lifecycle = reader.enum<CustomerLifecycle>("status"),
            tier = reader.enum<LoyaltyTier>("tier"),
            address =
                CanonicalAddress(
                    street = listOfNotNull(line1, line2).filter { it.isNotBlank() }.joinToString(", "),
                    city = reader.text("address.city").trim(),
                    postalCode = reader.text("address.postalCode").trim(),
                    countryCode = reader.text("address.country").trim().uppercase(),
                ),
            tags =
                reader
                    .textList("tags")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
            createdAt = reader.instant("createdAt"),
            updatedAt = reader.instant("updatedAt"),
        )
    }
}
