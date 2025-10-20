package im.bigs.pg.application.core.exception

sealed class ApplicationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class BadRequest(message: String, cause: Throwable? = null) : ApplicationException(message, cause)
    class NotFound(message: String, cause: Throwable? = null) : ApplicationException(message, cause)
    class Conflict(message: String, cause: Throwable? = null) : ApplicationException(message, cause)
}

fun badRequest(message: String, cause: Throwable? = null) = ApplicationException.BadRequest(message, cause)
fun notFound(message: String, cause: Throwable? = null) = ApplicationException.NotFound(message, cause)
fun conflict(message: String, cause: Throwable? = null) = ApplicationException.Conflict(message, cause)
