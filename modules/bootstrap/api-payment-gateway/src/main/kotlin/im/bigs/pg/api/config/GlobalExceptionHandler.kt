package im.bigs.pg.api.config

import im.bigs.pg.application.core.exception.ApplicationException
import im.bigs.pg.application.core.exception.ApplicationException.BadRequest
import im.bigs.pg.application.core.exception.ApplicationException.Conflict
import im.bigs.pg.application.core.exception.ApplicationException.NotFound
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBinding(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.debug("Validation failure", ex)
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Invalid request"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        log.debug("Constraint violation", ex)
        val message = ex.constraintViolations.firstOrNull()?.message ?: "Invalid request"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(HttpStatus.BAD_REQUEST, message))
    }

    @ExceptionHandler(ApplicationException::class)
    fun handleApplication(ex: ApplicationException): ResponseEntity<ErrorResponse> {
        val (status, level) = when (ex) {
            is BadRequest -> HttpStatus.BAD_REQUEST to "debug"
            is NotFound -> HttpStatus.NOT_FOUND to "warn"
            is Conflict -> HttpStatus.CONFLICT to "warn"
        }
        when (level) {
            "debug" -> log.debug("Application exception", ex)
            "warn" -> log.warn("Application exception", ex)
            else -> log.error("Application exception", ex)
        }
        return ResponseEntity.status(status).body(ErrorResponse.of(status, ex.message))
    }

    data class ErrorResponse(
        val timestamp: LocalDateTime,
        val status: Int,
        val error: String,
        val message: String?,
    ) {
        companion object {
            fun of(status: HttpStatus, message: String? = null) = ErrorResponse(
                timestamp = LocalDateTime.now(),
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
            )
        }
    }
}
