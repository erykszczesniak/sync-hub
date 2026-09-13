package com.erykszczesniak.synchub.sync

import com.erykszczesniak.synchub.canonical.CanonicalCustomer
import com.erykszczesniak.synchub.canonical.CanonicalOrder
import com.erykszczesniak.synchub.sink.CustomerSinkLoader
import com.erykszczesniak.synchub.sink.CustomerSinkMapper
import com.erykszczesniak.synchub.sink.OrderSinkLoader
import com.erykszczesniak.synchub.sink.OrderSinkMapper
import com.erykszczesniak.synchub.sink.SystemBCustomerRecord
import com.erykszczesniak.synchub.sink.SystemBOrderRecord
import com.erykszczesniak.synchub.source.graphql.OrderGraphQlExtractor
import com.erykszczesniak.synchub.source.rest.CustomerRestExtractor
import com.erykszczesniak.synchub.transform.CustomerSourceMapper
import com.erykszczesniak.synchub.transform.OrderSourceMapper
import com.erykszczesniak.synchub.transform.SchemaContracts
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** The two feeds the hub keeps in sync. Adding a feed is adding a bean here. */
@Configuration
class FeedPipelinesConfig {
    @Bean
    fun customersPipeline(
        extractor: CustomerRestExtractor,
        sourceMapper: CustomerSourceMapper,
        sinkMapper: CustomerSinkMapper,
        loader: CustomerSinkLoader,
    ): FeedPipeline<CanonicalCustomer, SystemBCustomerRecord> =
        FeedPipeline("customers", "REST", extractor, SchemaContracts.CUSTOMERS, sourceMapper, sinkMapper, loader)

    @Bean
    fun ordersPipeline(
        extractor: OrderGraphQlExtractor,
        sourceMapper: OrderSourceMapper,
        sinkMapper: OrderSinkMapper,
        loader: OrderSinkLoader,
    ): FeedPipeline<CanonicalOrder, SystemBOrderRecord> =
        FeedPipeline("orders", "GraphQL", extractor, SchemaContracts.ORDERS, sourceMapper, sinkMapper, loader)
}

@org.springframework.stereotype.Component
class FeedRegistry(
    pipelines: List<FeedPipeline<*, *>>,
) {
    private val byName = pipelines.associateBy { it.name }

    val names: List<String> = pipelines.map { it.name }

    fun get(feed: String): FeedPipeline<*, *> = byName[feed] ?: throw UnknownFeedException(feed)

    fun all(): Collection<FeedPipeline<*, *>> = byName.values
}

class UnknownFeedException(
    feed: String,
) : RuntimeException("Unknown feed '$feed'")
