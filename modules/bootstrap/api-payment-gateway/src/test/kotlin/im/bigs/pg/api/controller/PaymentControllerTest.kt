package im.bigs.pg.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.ninjasquad.springmockk.MockkBean
import im.bigs.pg.api.config.GlobalExceptionHandler
import im.bigs.pg.api.payment.PaymentController
import im.bigs.pg.api.payment.dto.request.CreatePaymentRequest
import im.bigs.pg.application.core.exception.ApplicationException
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import im.bigs.pg.application.payment.port.`in`.QueryResult
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.payment.PaymentSummary
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(PaymentController::class)
@Import(GlobalExceptionHandler::class)
class PaymentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var paymentUseCase: PaymentUseCase
    @MockkBean
    private lateinit var queryPaymentsUseCase: QueryPaymentsUseCase

    @BeforeEach
    fun setUp() {
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    @Test
    @DisplayName("결제 생성에 성공하면 200과 응답을 반환한다")
    fun create() {
        // given
        val saved = payment()
        every { paymentUseCase.pay(any()) } returns saved
        val request = defaultCreateRequest()

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(saved.id!!.toInt()))
            .andExpect(jsonPath("$.partnerId").value(saved.partnerId.toInt()))
            .andExpect(jsonPath("$.amount").value(saved.amount.toInt()))
            .andExpect(jsonPath("$.appliedFeeRate").value(saved.appliedFeeRate.toDouble()))
            .andExpect(jsonPath("$.status").value(saved.status.name))

        val captured = slot<PaymentCommand>()
        verify(exactly = 1) { paymentUseCase.pay(capture(captured)) }
        assertEquals(saved.partnerId, captured.captured.partnerId)
        assertEquals(saved.amount, captured.captured.amount)
        assertEquals("1111-1111-1111-1111", captured.captured.cardNumber)
    }

    @Test
    @DisplayName("금액이 1 미만이면 400과 검증 메시지를 반환한다")
    fun createAmountBelowMinimum() {
        // given
        val request = defaultCreateRequest(amount = BigDecimal.ZERO)

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("must be greater than or equal to 1"))
    }

    @Test
    @DisplayName("카드 번호 패턴을 위반하면 400과 검증 메시지를 반환한다")
    fun createInvalidCardNumberPattern() {
        // given
        val request = defaultCreateRequest(cardNumber = "abcd")

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("cardNumber must contain at least 10 digits and only digits or hyphens"))
    }

    @Test
    @DisplayName("생년월일 형식이 잘못되면 400과 검증 메시지를 반환한다")
    fun createInvalidBirthDatePattern() {
        // given
        val request = defaultCreateRequest(birthDate = "1990-01-01")

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("birthDate must be 8 digits in YYYYMMDD format"))
    }

    @Test
    @DisplayName("만료일 형식이 잘못되면 400과 검증 메시지를 반환한다")
    fun createInvalidExpiryPattern() {
        // given
        val request = defaultCreateRequest(expiry = "123")

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("expiry must be 4 digits in MMYY format"))
    }

    @Test
    @DisplayName("카드 비밀번호 형식이 잘못되면 400과 검증 메시지를 반환한다")
    fun createInvalidCardPasswordPattern() {
        // given
        val request = defaultCreateRequest(cardPassword = "1")

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("cardPassword must be 2 digits"))
    }

    @Test
    @DisplayName("도메인 예외가 발생하면 400을 반환한다")
    fun createApplicationBadRequest() {
        // given
        every { paymentUseCase.pay(any()) } throws ApplicationException.BadRequest("결제 불가: 한도 초과")
        val request = defaultCreateRequest(partnerId = 1L)

        // when
        val result = performCreate(request)

        // then
        result
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("결제 불가: 한도 초과"))
    }

    @Test
    @DisplayName("결제 내역을 조회한다")
    fun query() {
        // given
        val payment = payment(
            id = 1L,
            partnerId = 2L,
            approvalCode = "10193622",
            approvedAt = LocalDateTime.of(2025, 10, 19, 14, 31, 54),
            createdAt = LocalDateTime.of(2025, 10, 19, 23, 31, 54),
            updatedAt = LocalDateTime.of(2025, 10, 19, 23, 31, 54),
        )
        val summary = PaymentSummary(
            count = 3,
            totalAmount = BigDecimal("90000"),
            totalNetAmount = BigDecimal("87000"),
        )
        every { queryPaymentsUseCase.query(any()) } returns QueryResult(
            items = listOf(payment),
            summary = summary,
            nextCursor = null,
            hasNext = false,
        )
        val params = arrayOf(
            "partnerId" to "2",
            "status" to "APPROVED",
            "from" to "2025-10-19T00:00:00Z",
            "to" to "2025-10-20T00:00:00Z",
            "limit" to "10",
        )

        // when
        val result = performQuery(*params)

        // then
        result
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(payment.id!!.toInt()))
            .andExpect(jsonPath("$.summary.count").value(summary.count))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
            .andExpect(jsonPath("$.hasNext").value(false))

        val captured = slot<QueryFilter>()
        verify(exactly = 1) { queryPaymentsUseCase.query(capture(captured)) }
        assertEquals(payment.partnerId, captured.captured.partnerId)
        assertEquals(payment.status.name, captured.captured.status)
        assertEquals(10, captured.captured.limit)
        assertNull(captured.captured.cursor)
    }

    @Test
    @DisplayName("잘못된 날짜 포맷이면 400을 반환한다")
    fun queryInvalidDateFormat() {
        // given
        val params = arrayOf(
            "from" to "not-a-date",
            "to" to "also-bad",
        )

        // when
        val result = performQuery(*params)

        // then
        result.andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("도메인 예외가 발생하면 404를 반환한다")
    fun queryApplicationNotFound() {
        // given
        every { queryPaymentsUseCase.query(any()) } throws ApplicationException.NotFound("조회 결과 없음")
        val params = arrayOf("partnerId" to "999")

        // when
        val result = performQuery(*params)

        // then
        result
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("조회 결과 없음"))
    }

    private fun performCreate(request: CreatePaymentRequest): ResultActions =
        mockMvc.perform(
            post(PAYMENTS_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )

    private fun performQuery(vararg params: Pair<String, String>): ResultActions {
        val builder = get(PAYMENTS_URL)
        params.forEach { (key, value) -> builder.param(key, value) }
        return mockMvc.perform(builder)
    }

    private fun defaultCreateRequest(
        partnerId: Long = 2L,
        amount: BigDecimal = BigDecimal("30000"),
        cardNumber: String = "1111-1111-1111-1111",
        birthDate: String = "19900101",
        expiry: String = "1227",
        cardPassword: String = "12",
        productName: String? = "샘플",
    ) =
        CreatePaymentRequest(
            partnerId = partnerId,
            amount = amount,
            cardNumber = cardNumber,
            birthDate = birthDate,
            expiry = expiry,
            cardPassword = cardPassword,
            productName = productName,
        )

    private fun payment(
        id: Long = 3L,
        partnerId: Long = 2L,
        amount: BigDecimal = BigDecimal("30000"),
        appliedFeeRate: BigDecimal = BigDecimal("0.030000"),
        feeAmount: BigDecimal = BigDecimal("1000"),
        netAmount: BigDecimal = BigDecimal("29000"),
        cardBin: String? = "111111",
        cardLast4: String? = "1111",
        approvalCode: String = "10192333",
        approvedAt: LocalDateTime = LocalDateTime.of(2025, 10, 19, 14, 31, 56),
        status: PaymentStatus = PaymentStatus.APPROVED,
        createdAt: LocalDateTime = LocalDateTime.of(2025, 10, 19, 23, 31, 56),
        updatedAt: LocalDateTime = LocalDateTime.of(2025, 10, 19, 23, 31, 56),
    ) =
        Payment(
            id = id,
            partnerId = partnerId,
            amount = amount,
            appliedFeeRate = appliedFeeRate,
            feeAmount = feeAmount,
            netAmount = netAmount,
            cardBin = cardBin,
            cardLast4 = cardLast4,
            approvalCode = approvalCode,
            approvedAt = approvedAt,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    companion object {
        private const val PAYMENTS_URL = "/api/v1/payments"
    }
}
