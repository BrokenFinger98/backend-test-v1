package im.bigs.pg.application.payment.service

import im.bigs.pg.application.core.exception.ApplicationException
import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.payment.port.out.PaymentPage
import im.bigs.pg.application.payment.port.out.PaymentQuery
import im.bigs.pg.application.payment.port.out.PaymentSummaryFilter
import im.bigs.pg.application.payment.port.out.PaymentSummaryProjection
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class QueryPaymentsServiceTest {

    @MockK lateinit var paymentRepository: PaymentOutPort

    private lateinit var service: QueryPaymentsService

    @BeforeEach
    fun setUp() {
        clearMocks(paymentRepository)
        service = QueryPaymentsService(paymentRepository)
    }

    @Test
    @DisplayName("조회 결과와 통계를 그대로 반환한다")
    fun query() {
        // given
        val partnerId = 1L
        val from = LocalDateTime.of(2024, 1, 1, 0, 0)
        val to = LocalDateTime.of(2024, 1, 2, 0, 0)
        val filter = QueryFilter(
            partnerId = partnerId,
            status = "APPROVED",
            from = from,
            to = to,
            limit = 30,
        )

        val captureQuery = slot<PaymentQuery>()
        val captureSummary = slot<PaymentSummaryFilter>()

        val nextCursorCreatedAt = LocalDateTime.of(2024, 1, 3, 12, 30)
        val nextCursorId = 55L
        val payments = listOf(
            payment(
                id = 10L,
                partnerId = partnerId,
                amount = BigDecimal("1000"),
                approvalCode = "APP-1",
            ),
        )

        every { paymentRepository.findBy(capture(captureQuery)) } returns PaymentPage(
            items = payments,
            hasNext = true,
            nextCursorCreatedAt = nextCursorCreatedAt,
            nextCursorId = nextCursorId,
        )
        every { paymentRepository.summary(capture(captureSummary)) } returns PaymentSummaryProjection(
            count = 5L,
            totalAmount = BigDecimal("5000"),
            totalNetAmount = BigDecimal("4850"),
        )

        // when
        val result = service.query(filter)

        // then
        assertEquals(payments, result.items)
        assertEquals(5L, result.summary.count)
        assertEquals(BigDecimal("5000"), result.summary.totalAmount)
        assertEquals(BigDecimal("4850"), result.summary.totalNetAmount)
        assertTrue(result.hasNext)
        assertEquals(encodeCursor(nextCursorCreatedAt, nextCursorId), result.nextCursor)

        val query = captureQuery.captured
        assertEquals(partnerId, query.partnerId)
        assertEquals(PaymentStatus.APPROVED, query.status)
        assertEquals(from, query.from)
        assertEquals(to, query.to)
        assertEquals(30, query.limit)
        assertNull(query.cursorCreatedAt)
        assertNull(query.cursorId)

        val summaryFilter = captureSummary.captured
        assertEquals(partnerId, summaryFilter.partnerId)
        assertEquals(PaymentStatus.APPROVED, summaryFilter.status)
        assertEquals(from, summaryFilter.from)
        assertEquals(to, summaryFilter.to)

        verify(exactly = 1) { paymentRepository.findBy(any()) }
        verify(exactly = 1) { paymentRepository.summary(any()) }
    }

    @Test
    @DisplayName("limit 이 0 이하이면 기본값 20으로 동작한다")
    fun queryWithNegativeLimit() {
        // given
        val cursorCreatedAt = LocalDateTime.of(2024, 1, 5, 15, 45)
        val cursorId = 99L
        val cursorToken = encodeCursor(cursorCreatedAt, cursorId)

        val filter = QueryFilter(
            cursor = cursorToken,
            limit = -10,
        )

        val captureQuery = slot<PaymentQuery>()

        every { paymentRepository.findBy(capture(captureQuery)) } returns PaymentPage(
            items = emptyList(),
            hasNext = false,
            nextCursorCreatedAt = null,
            nextCursorId = null,
        )
        every { paymentRepository.summary(any()) } returns PaymentSummaryProjection(
            count = 0L,
            totalAmount = BigDecimal.ZERO,
            totalNetAmount = BigDecimal.ZERO,
        )

        // when
        val result = service.query(filter)

        // then
        assertEquals(20, captureQuery.captured.limit)
        assertEquals(cursorCreatedAt, captureQuery.captured.cursorCreatedAt)
        assertEquals(cursorId, captureQuery.captured.cursorId)
        assertNull(result.nextCursor)
        assertFalse(result.hasNext)

        verify(exactly = 1) { paymentRepository.findBy(any()) }
        verify(exactly = 1) { paymentRepository.summary(any()) }
    }

    @Test
    @DisplayName("잘못된 상태 값이면 예외가 발생해야 한다")
    fun queryWithInvalidStatus() {
        // given
        val filter = QueryFilter(status = "invalid")

        // when & then
        val ex = assertFailsWith<ApplicationException> { service.query(filter) }
        assertTrue(ex is ApplicationException.BadRequest)

        verify(exactly = 0) { paymentRepository.findBy(any()) }
        verify(exactly = 0) { paymentRepository.summary(any()) }
    }
}

private fun encodeCursor(createdAt: LocalDateTime, id: Long): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(
        "${createdAt.toInstant(ZoneOffset.UTC).toEpochMilli()}:$id".toByteArray(),
    )

private fun payment(
    id: Long,
    partnerId: Long,
    amount: BigDecimal,
    approvalCode: String,
    approvedAt: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 0),
): Payment =
    Payment(
        id = id,
        partnerId = partnerId,
        amount = amount,
        appliedFeeRate = BigDecimal("0.0300"),
        feeAmount = BigDecimal("30"),
        netAmount = amount - BigDecimal("30"),
        cardLast4 = "1234",
        cardBin = "111111",
        approvalCode = approvalCode,
        approvedAt = approvedAt,
        status = PaymentStatus.APPROVED,
    )
