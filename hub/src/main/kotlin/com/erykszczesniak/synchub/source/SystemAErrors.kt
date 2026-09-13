package com.erykszczesniak.synchub.source

import com.erykszczesniak.synchub.common.SourceAuthException
import com.erykszczesniak.synchub.common.SourceException
import com.erykszczesniak.synchub.common.SourceResponseException
import com.erykszczesniak.synchub.common.SourceUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException

/** One place that decides which transport failures are retryable; shared by the REST and GraphQL clients. */
object SystemAErrors {
    fun translate(ex: RuntimeException): SourceException {
        val root =
            generateSequence<Throwable>(ex) { it.cause }.firstOrNull {
                it is WebClientResponseException ||
                    it is WebClientRequestException
            }
        return when (root) {
            is WebClientResponseException -> translateStatus(root)
            is WebClientRequestException -> SourceUnavailableException("System A unreachable: ${root.message}", root)
            else ->
                when (ex) {
                    is SourceException -> ex
                    else -> SourceResponseException("System A call failed: ${ex.message}", ex)
                }
        }
    }

    private fun translateStatus(ex: WebClientResponseException): SourceException =
        when {
            ex.statusCode == HttpStatus.UNAUTHORIZED || ex.statusCode == HttpStatus.FORBIDDEN ->
                SourceAuthException("System A rejected the API key (${ex.statusCode.value()})")
            ex.statusCode.is5xxServerError || ex.statusCode == HttpStatus.TOO_MANY_REQUESTS ->
                SourceUnavailableException("System A answered ${ex.statusCode.value()}", ex)
            else ->
                SourceResponseException(
                    "System A answered ${ex.statusCode.value()}: ${ex.responseBodyAsString}",
                    ex,
                )
        }
}
