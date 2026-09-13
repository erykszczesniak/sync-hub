package com.erykszczesniak.synchub.common

import com.erykszczesniak.synchub.sync.FeedBusyException
import com.erykszczesniak.synchub.sync.UnknownFeedException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/** RFC 9457 problem details for the control and status API; entities never leak, exceptions never stack-trace. */
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(UnknownFeedException::class, NoSuchElementException::class)
    fun notFound(ex: RuntimeException): ProblemDetail = problem(HttpStatus.NOT_FOUND, ex.message)

    @ExceptionHandler(FeedBusyException::class)
    fun busy(ex: FeedBusyException): ProblemDetail = problem(HttpStatus.CONFLICT, ex.message)

    @ExceptionHandler(
        IllegalArgumentException::class,
        MethodArgumentNotValidException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun badRequest(ex: Exception): ProblemDetail = problem(HttpStatus.BAD_REQUEST, ex.message)

    @ExceptionHandler(SourceException::class)
    fun source(ex: SourceException): ProblemDetail = problem(HttpStatus.BAD_GATEWAY, ex.message)

    private fun problem(
        status: HttpStatus,
        detail: String?,
    ): ProblemDetail = ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase)
}
