package im.bigs.pg.api.docs

import im.bigs.pg.api.payment.PaymentController
import im.bigs.pg.api.payment.dto.request.CreatePaymentRequest
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import im.bigs.pg.application.payment.port.`in`.QueryResult
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.payment.PaymentSummary
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime

class PaymentControllerDocsTest : RestDocsSupport() {

    private val paymentUseCase: PaymentUseCase = mockk()
    private val queryPaymentsUseCase: QueryPaymentsUseCase = mockk()

    override fun initController(): Any = PaymentController(paymentUseCase, queryPaymentsUseCase)

    @AfterEach
    fun tearDown() {
        clearMocks(paymentUseCase, queryPaymentsUseCase)
    }

    @Test
    @DisplayName("POST /api/v1/payments 문서화")
    fun create() {
        // given
        val payment = Payment(
            id = 3L,
            partnerId = 2L,
            amount = BigDecimal("30000"),
            appliedFeeRate = BigDecimal("0.030000"),
            feeAmount = BigDecimal("1000"),
            netAmount = BigDecimal("29000"),
            cardBin = "111111",
            cardLast4 = "1111",
            approvalCode = "10192333",
            approvedAt = LocalDateTime.of(2025, 10, 19, 14, 31, 56),
            status = PaymentStatus.APPROVED,
            createdAt = LocalDateTime.of(2025, 10, 19, 23, 31, 56),
            updatedAt = LocalDateTime.of(2025, 10, 19, 23, 31, 56),
        )

        every { paymentUseCase.pay(any()) } returns payment

        val request = CreatePaymentRequest(
            partnerId = 2L,
            amount = BigDecimal("30000"),
            cardNumber = "1111-1111-1111-1111",
            birthDate = "19900101",
            expiry = "1227",
            cardPassword = "12",
            productName = "샘플",
        )

        // when & then
        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "payments-create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("partnerId").description("제휴사 ID"),
                        fieldWithPath("amount").description("결제 금액"),
                        fieldWithPath("cardNumber").description("결제 카드 번호"),
                        fieldWithPath("birthDate").description("생년월일 (YYYYMMDD)"),
                        fieldWithPath("expiry").description("카드 만료 (MMYY)"),
                        fieldWithPath("cardPassword").description("카드 비밀번호 앞 2자리"),
                        fieldWithPath("productName").optional().description("상품명"),
                    ),
                    responseFields(
                        fieldWithPath("id").description("결제 ID"),
                        fieldWithPath("partnerId").description("제휴사 ID"),
                        fieldWithPath("amount").description("결제 금액"),
                        fieldWithPath("appliedFeeRate").description("적용 수수료율"),
                        fieldWithPath("feeAmount").description("수수료 금액"),
                        fieldWithPath("netAmount").description("정산 금액"),
                        fieldWithPath("cardLast4").description("카드 마지막 4자리"),
                        fieldWithPath("approvalCode").description("PG 승인 코드"),
                        fieldWithPath("approvedAt").description("승인 시각"),
                        fieldWithPath("status").description("결제 상태"),
                        fieldWithPath("createdAt").description("생성 시각"),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("GET /api/v1/payments 문서화")
    fun query() {
        // given
        val payment = Payment(
            id = 1L,
            partnerId = 2L,
            amount = BigDecimal("30000"),
            appliedFeeRate = BigDecimal("0.030000"),
            feeAmount = BigDecimal("1000"),
            netAmount = BigDecimal("29000"),
            cardBin = "111111",
            cardLast4 = "1111",
            approvalCode = "10193622",
            approvedAt = LocalDateTime.of(2025, 10, 19, 14, 31, 54),
            status = PaymentStatus.APPROVED,
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

        // when & then
        mockMvc.perform(
            get("/api/v1/payments")
                .param("partnerId", "2")
                .param("status", "APPROVED")
                .param("from", "2024-02-02T00:00:00Z")
                .param("to", "2024-02-03T00:00:00Z")
                .param("cursor", "cursor-token")
                .param("limit", "10"),
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "payments-query",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    queryParameters(
                        parameterWithName("partnerId").optional().description("제휴사 ID 필터"),
                        parameterWithName("status").optional().description("결제 상태 필터"),
                        parameterWithName("from").optional().description("조회 시작 시각 (ISO-8601)"),
                        parameterWithName("to").optional().description("조회 종료 시각 (ISO-8601)"),
                        parameterWithName("cursor").optional().description("커서 토큰"),
                        parameterWithName("limit").optional().description("조회 건수 (기본 20)"),
                    ),
                    responseFields(
                        fieldWithPath("items").description("결제 목록"),
                        fieldWithPath("items[].id").description("결제 ID"),
                        fieldWithPath("items[].partnerId").description("제휴사 ID"),
                        fieldWithPath("items[].amount").description("결제 금액"),
                        fieldWithPath("items[].appliedFeeRate").description("적용 수수료율"),
                        fieldWithPath("items[].feeAmount").description("수수료 금액"),
                        fieldWithPath("items[].netAmount").description("정산 금액"),
                        fieldWithPath("items[].cardLast4").optional().description("카드 마지막 4자리"),
                        fieldWithPath("items[].approvalCode").description("PG 승인 코드"),
                        fieldWithPath("items[].approvedAt").description("승인 시각"),
                        fieldWithPath("items[].status").description("결제 상태"),
                        fieldWithPath("items[].createdAt").description("생성 시각"),
                        fieldWithPath("summary.count").description("총 건수"),
                        fieldWithPath("summary.totalAmount").description("총 결제 금액"),
                        fieldWithPath("summary.totalNetAmount").description("총 정산 금액"),
                        fieldWithPath("nextCursor").optional().description("다음 페이지 커서"),
                        fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                    ),
                ),
            )
    }
}
