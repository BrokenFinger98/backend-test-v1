package im.bigs.pg.external.pg

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.external.pg.client.TestPgClient
import im.bigs.pg.external.pg.config.TestPgProperties
import im.bigs.pg.external.pg.exception.PgClientException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.math.BigDecimal
import java.time.LocalDateTime
import org.junit.jupiter.api.DisplayName
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TestPgClientTest {

    private lateinit var server: MockWebServer
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        objectMapper = ObjectMapper().findAndRegisterModules()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @DisplayName("성공 응답을 매핑해야 한다")
    fun approveMapsSuccessResponse() {
        // given
        val baseUrl = server.url("/").toString().trimEnd('/')
        val partnerId = 2L
        val apiKey = "11111111-1111-4111-8111-111111111111"
        val iv = "AAAAAAAAAAAAAAAA"
        val amount = BigDecimal("10000")
        val cardNumber = "1111-1111-1111-1111"
        val birthDate = "19900101"
        val expiry = "1227"
        val password = "12"

        val properties = TestPgProperties().apply {
            this.baseUrl = baseUrl
            clients = listOf(TestPgProperties.Client().apply {
                this.partnerId = partnerId
                this.apiKey = apiKey
                this.iv = iv
            })
        }

        val testClient = TestPgClient(properties, objectMapper, RestClient.builder())

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "approvalCode": "10080728",
                        "approvedAt": "2025-10-08T03:31:34.181568",
                        "maskedCardLast4": "1111",
                        "amount": 10000,
                        "status": "APPROVED"
                    }"""
                        .trimIndent(),
                ),
        )

        val request = PgApproveRequest(
            partnerId = partnerId,
            amount = amount,
            cardNumber = cardNumber,
            birthDate = birthDate,
            expiry = expiry,
            password = password,
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트",
        )

        // when
        val result = testClient.approve(request)

        // then
        assertEquals("10080728", result.approvalCode)
        assertEquals(LocalDateTime.parse("2025-10-08T03:31:34.181568"), result.approvedAt)
        assertEquals(PaymentStatus.APPROVED, result.status)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/pay/credit-card", recorded.path)
        assertEquals(apiKey, recorded.getHeader("API-KEY"))

        val requestBody = objectMapper.readTree(recorded.body.readUtf8())
        val encValue = requestBody.get("enc")?.asText()
        assertTrue(!encValue.isNullOrBlank())
    }

    @Test
    @DisplayName("PG 오류를 예외로 전파해야 한다")
    fun approvePropagatesPgError() {
        // given
        val baseUrl = server.url("/").toString().trimEnd('/')
        val partnerId = 2L
        val apiKey = "11111111-1111-4111-8111-111111111111"
        val iv = "AAAAAAAAAAAAAAAA"
        val amount = BigDecimal("10000")
        val cardNumber = "1111-1111-1111-1111"
        val birthDate = "19900101"
        val expiry = "1227"
        val password = "12"

        val properties = TestPgProperties().apply {
            this.baseUrl = baseUrl
            clients = listOf(TestPgProperties.Client().apply {
                this.partnerId = partnerId
                this.apiKey = apiKey
                this.iv = iv
            })
        }

        val testClient = TestPgClient(properties, objectMapper, RestClient.builder())

        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{
                        "code": 1001,
                        "errorCode": "STOLEN_OR_LOST",
                        "message": "도난 또는 분실된 카드입니다.",
                        "referenceId": "ref-1"
                    }"""
                        .trimIndent(),
                ),
        )

        val request = PgApproveRequest(
            partnerId = partnerId,
            amount = amount,
            cardNumber = cardNumber,
            birthDate = birthDate,
            expiry = expiry,
            password = password,
            cardBin = "111111",
            cardLast4 = "1111",
            productName = "테스트",
        )

        // when && then
        val ex = assertFailsWith<PgClientException.HttpError> { testClient.approve(request) }
        assertEquals(422, ex.statusCode)
        assertEquals("TEST_PG", ex.clientType)
        assertTrue(ex.message.contains("STOLEN_OR_LOST"))
        server.takeRequest()
    }
}
