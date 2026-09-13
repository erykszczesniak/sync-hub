package com.erykszczesniak.synchub.source

/**
 * A page-at-a-time client of one feed of a source system. Implementations own protocol details
 * (REST, GraphQL, auth, pagination) and resilience; they never see watermarks, which belong to the
 * orchestration layer.
 */
interface SourceExtractor {
    val feed: String

    fun fetchChanges(
        window: ExtractionWindow,
        cursor: String?,
        pageSize: Int,
    ): SourcePage
}

/**
 * Every page of [window], in order, bounded by [maxPages] so a runaway source can never hang a run.
 * Deliberately an extension and not an interface default method: a default method would call
 * `fetchChanges` on `this` inside the bean and bypass the resilience4j proxy (retry, breaker, rate limit).
 */
fun SourceExtractor.pages(
    window: ExtractionWindow,
    pageSize: Int,
    maxPages: Int,
): Sequence<SourcePage> =
    sequence {
        var cursor: String? = null
        var fetched = 0
        do {
            val page = fetchChanges(window, cursor, pageSize)
            yield(page)
            cursor = page.nextCursor
            fetched++
        } while (page.hasMore && cursor != null && fetched < maxPages)
    }
