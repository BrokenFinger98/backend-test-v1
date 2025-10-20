package im.bigs.pg.external.pg.client

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.external.pg.config.TestPgProperties
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Base64
import java.util.LinkedHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val GCM_TAG_LENGTH_BITS = 128

@Component
class TestPgClient(
    private val properties: TestPgProperties,
    private val objectMapper: ObjectMapper,
    restClientBuilder: RestClient.Builder,
) : PgClientOutPort {

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl)
        .build()

    override fun supports(partnerId: Long): Boolean = properties.findClient(partnerId) != null

    override fun approve(request: PgApproveRequest): PgApproveResult {
        val client = clientConfig(request.partnerId)
        val payload = encryptedPayload(request, client)
        val response = requestApproval(client, payload)
        return response.toDomain()
    }

    private fun clientConfig(partnerId: Long): ClientConfig {
        val rawClient = properties.findClient(partnerId)
            ?: throw IllegalStateException("No PG configuration for partner $partnerId")
        return ClientConfig.from(rawClient)
    }

    private fun encryptedPayload(request: PgApproveRequest, client: ClientConfig): EncryptedPayload {
        val plaintext = PgApprovePlaintext.from(request).json(objectMapper)
        val secretKey = client.secretKey()
        val cipher = cipher(secretKey, client.initializationVector())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted)
        return EncryptedPayload(encoded)
    }

    private fun cipher(secretKey: SecretKeySpec, iv: GCMParameterSpec): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        return cipher
    }

    private fun requestApproval(client: ClientConfig, payload: EncryptedPayload): TestPgSuccessResponse {
        val response = restClient.post()
            .uri(PAY_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header(API_KEY_HEADER, client.headerValue())
            .body(payload.asRequestBody())
            .retrieve()
            .body(TestPgSuccessResponse::class.java)
        return response ?: throw IllegalStateException("Empty response from PG")
    }

    companion object {
        private const val API_KEY_HEADER = "API-KEY"
        private const val PAY_PATH = "/api/v1/pay/credit-card"
    }
}

private data class ClientConfig(
    private val apiKey: ApiKey,
    private val initializationVector: InitializationVector,
) {
    fun secretKey(): SecretKeySpec = apiKey.secretKeySpec()

    fun initializationVector(): GCMParameterSpec = initializationVector.gcmParameterSpec()

    fun headerValue(): String = apiKey.header()

    companion object {
        fun from(raw: TestPgProperties.Client): ClientConfig = ClientConfig(
            apiKey = ApiKey(raw.apiKey),
            initializationVector = InitializationVector(raw.iv),
        )
    }
}

private data class EncryptedPayload(private val value: String) {
    fun asRequestBody(): Map<String, String> = mapOf("enc" to value)
}

private data class PgApprovePlaintext(
    private val cardDetails: CardDetails,
    private val birthDate: BirthDate,
    private val amount: Amount,
) {
    fun json(objectMapper: ObjectMapper): String {
        val payload = LinkedHashMap<String, Any>()
        payload.putAll(cardDetails.asMap())
        payload["birthDate"] = birthDate.value
        payload["amount"] = amount.value
        return objectMapper.writeValueAsString(payload)
    }

    companion object {
        fun from(request: PgApproveRequest): PgApprovePlaintext = PgApprovePlaintext(
            cardDetails = CardDetails.from(request),
            birthDate = BirthDate(request.birthDate),
            amount = Amount.from(request.amount),
        )
    }
}

private data class CardDetails(
    private val cardNumber: String,
    private val expiry: String,
    private val password: String,
) {
    fun asMap(): Map<String, String> = mapOf(
        "cardNumber" to cardNumber,
        "expiry" to expiry,
        "password" to password,
    )

    companion object {
        fun from(request: PgApproveRequest): CardDetails = CardDetails(
            cardNumber = request.cardNumber,
            expiry = request.expiry,
            password = request.password,
        )
    }
}

private data class BirthDate(val value: String)

private data class Amount(val value: Long) {
    companion object {
        fun from(amount: java.math.BigDecimal): Amount = Amount(amount.longValueExact())
    }
}

private data class ApiKey(private val value: String) {
    fun secretKeySpec(): SecretKeySpec {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, "AES")
    }

    fun header(): String = value
}

private data class InitializationVector(private val encoded: String) {
    fun gcmParameterSpec(): GCMParameterSpec {
        val bytes = Base64.getUrlDecoder().decode(encoded)
        return GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes)
    }
}

private fun TestPgSuccessResponse.toDomain(): PgApproveResult = PgApproveResult(
    approvalCode = approvalCode,
    approvedAt = LocalDateTime.parse(approvedAt),
    status = PaymentStatus.valueOf(status.uppercase()),
)

data class TestPgSuccessResponse(
    val approvalCode: String,
    val approvedAt: String,
    val maskedCardLast4: String,
    val amount: BigDecimal,
    val status: String,
)