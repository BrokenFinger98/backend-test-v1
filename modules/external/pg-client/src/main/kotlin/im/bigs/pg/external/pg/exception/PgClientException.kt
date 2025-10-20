package im.bigs.pg.external.pg.exception

sealed class PgClientException(
    val clientType: String,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause) {

    class ConfigurationMissing(clientType: String, partnerId: Long) :
        PgClientException(clientType, "[$clientType] PG configuration missing for partner $partnerId")

    class HttpError(
        clientType: String,
        val statusCode: Int,
        val responseBody: String?,
        cause: Throwable? = null,
    ) : PgClientException(
        clientType,
        "[$clientType] PG approval failed with status $statusCode${responseBody.suffix()}",
        cause,
    )

    class NetworkError(clientType: String, cause: Throwable) :
        PgClientException(clientType, "[$clientType] PG approval failed due to network error", cause)

    class Unexpected(clientType: String, cause: Throwable) :
        PgClientException(clientType, "[$clientType] PG approval failed unexpectedly", cause)
}

private fun String?.suffix(): String =
    this?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""

fun pgConfigurationMissing(clientType: String, partnerId: Long) =
    PgClientException.ConfigurationMissing(clientType, partnerId)

fun pgHttpError(clientType: String, statusCode: Int, responseBody: String?, cause: Throwable? = null) =
    PgClientException.HttpError(clientType, statusCode, responseBody, cause)

fun pgNetworkError(clientType: String, cause: Throwable) =
    PgClientException.NetworkError(clientType, cause)

fun pgUnexpectedError(clientType: String, cause: Throwable) =
    PgClientException.Unexpected(clientType, cause)
