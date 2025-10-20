package im.bigs.pg.api.config

import im.bigs.pg.application.core.exception.ApplicationException
import im.bigs.pg.external.pg.exception.PgClientException
import im.bigs.pg.external.pg.exception.PgClientException.ConfigurationMissing
import im.bigs.pg.external.pg.exception.PgClientException.HttpError
import im.bigs.pg.external.pg.exception.PgClientException.NetworkError
import im.bigs.pg.external.pg.exception.PgClientException.Unexpected
import im.bigs.pg.application.core.exception.ApplicationException.BadRequest
import im.bigs.pg.application.core.exception.ApplicationException.Conflict
import im.bigs.pg.application.core.exception.ApplicationException.NotFound
import jakarta.validation.ConstraintViolationException
import org.slf4j.Logger
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

    @ExceptionHandler(PgClientException::class)
    fun handlePg(ex: PgClientException): ResponseEntity<ErrorResponse> {
        val (status, logLevel) = when (ex) {
            is ConfigurationMissing -> HttpStatus.CONFLICT to LogLevel.WARN
            is HttpError -> mapPgHttpStatus(ex.statusCode)
            is NetworkError -> HttpStatus.BAD_GATEWAY to LogLevel.ERROR
            is Unexpected -> HttpStatus.BAD_GATEWAY to LogLevel.ERROR
        }
        log.log(logLevel, "PG client error from ${ex.clientType}", ex)
        val message = ex.message
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message))
    }

    private fun mapPgHttpStatus(statusCode: Int): Pair<HttpStatus, LogLevel> = when (statusCode) {
        400 -> HttpStatus.BAD_REQUEST to LogLevel.DEBUG
        401 -> HttpStatus.UNAUTHORIZED to LogLevel.WARN
        403 -> HttpStatus.FORBIDDEN to LogLevel.WARN
        404 -> HttpStatus.NOT_FOUND to LogLevel.WARN
        422 -> HttpStatus.UNPROCESSABLE_ENTITY to LogLevel.WARN
        else -> HttpStatus.BAD_GATEWAY to LogLevel.ERROR
    }

    private enum class LogLevel { DEBUG, WARN, ERROR }

    private fun Logger.log(level: LogLevel, message: String, throwable: Throwable) {
        when (level) {
            LogLevel.DEBUG -> debug(message, throwable)
            LogLevel.WARN -> warn(message, throwable)
            LogLevel.ERROR -> error(message, throwable)
        }
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
