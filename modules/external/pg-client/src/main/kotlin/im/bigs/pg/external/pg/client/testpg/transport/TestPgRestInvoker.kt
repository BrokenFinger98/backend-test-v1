package im.bigs.pg.external.pg.client.testpg.transport

import im.bigs.pg.external.pg.client.testpg.crypto.ClientConfig
import im.bigs.pg.external.pg.client.testpg.payload.EncryptedPayload
import im.bigs.pg.external.pg.config.TestPgProperties
import im.bigs.pg.external.pg.exception.pgHttpError
import im.bigs.pg.external.pg.exception.pgNetworkError
import im.bigs.pg.external.pg.exception.pgUnexpectedError
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

@Component
class TestPgRestInvoker(
    properties: TestPgProperties,
    restClientBuilder: RestClient.Builder,
) {

    private val restClient: RestClient = restClientBuilder.baseUrl(properties.baseUrl).build()

    fun requestApproval(client: ClientConfig, payload: EncryptedPayload): TestPgSuccessResponse {
        val response = try {
            executeApproval(client, payload)
        } catch (ex: RestClientResponseException) {
            val statusCode = ex.statusCode.value()
            val responseBody = ex.responseBodyAsString
            throw pgHttpError(CLIENT_TYPE, statusCode, responseBody, ex)
        } catch (ex: RestClientException) {
            throw pgNetworkError(CLIENT_TYPE, ex)
        } catch (ex: Exception) {
            throw pgUnexpectedError(CLIENT_TYPE, ex)
        }
        if (response != null) return response
        throw pgUnexpectedError(CLIENT_TYPE, NullPointerException("Empty response from PG"))
    }

    private fun executeApproval(client: ClientConfig, payload: EncryptedPayload): TestPgSuccessResponse? {
        val postSpec = restClient.post()
        val uriSpec = postSpec.uri(PAY_PATH)
        val contentSpec = uriSpec.contentType(MediaType.APPLICATION_JSON)
        val headerSpec = contentSpec.header(API_KEY_HEADER, client.headerValue())
        val bodySpec = headerSpec.body(payload.asRequestBody())
        val retrievalSpec = bodySpec.retrieve()
        return retrievalSpec.body(TestPgSuccessResponse::class.java)
    }

    companion object {
        private const val CLIENT_TYPE = "TEST_PG"
        private const val API_KEY_HEADER = "API-KEY"
        private const val PAY_PATH = "/api/v1/pay/credit-card"
    }
}
