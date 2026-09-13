package com.erykszczesniak.systema.api

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

data class ApiError(
    val error: String,
)

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(
        IllegalArgumentException::class,
        ConstraintViolationException::class,
        MethodArgumentTypeMismatchException::class,
        MethodArgumentNotValidException::class,
        BindException::class,
    )
    fun badRequest(ex: Exception): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError(ex.message ?: "bad request"))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(ex: NoSuchElementException): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError(ex.message ?: "not found"))
}
