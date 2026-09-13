package com.erykszczesniak.systema.api

import com.fasterxml.jackson.databind.node.ObjectNode

/** One page of the customer change feed. Items are raw JSON so drift scenarios can reshape them. */
data class CustomerChangesResponse(
    val items: List<ObjectNode>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
