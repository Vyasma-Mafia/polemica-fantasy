package io.github.mralex1810.fantasy.controller

import io.github.mralex1810.fantasy.dto.admin.response.ApiErrorBody
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiErrorBody> {
        val fieldErrors = e.bindingResult.fieldErrors.associate { fe ->
            fe.field to (fe.defaultMessage ?: "invalid")
        }
        val body = ApiErrorBody("Validation failed", fieldErrors)
        return ResponseEntity.badRequest().body(body)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(e: ResponseStatusException): ResponseEntity<ApiErrorBody> {
        val msg = e.reason ?: "Request failed"
        val body = ApiErrorBody(msg)
        return ResponseEntity.status(e.statusCode).body(body)
    }
}
