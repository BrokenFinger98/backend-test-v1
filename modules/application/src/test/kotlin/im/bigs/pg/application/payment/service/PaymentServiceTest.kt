package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.partner.Partner
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
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class PaymentServiceTest {

    @MockK lateinit var partnerRepo: PartnerOutPort
    @MockK lateinit var feeRepo: FeePolicyOutPort
    @MockK lateinit var paymentRepo: PaymentOutPort
    @MockK lateinit var pgClient: PgClientOutPort

    private lateinit var paymentService: PaymentService

    @BeforeEach
    fun setUp() {
        clearMocks(partnerRepo, feeRepo, paymentRepo, pgClient)
        paymentService = PaymentService(partnerRepo, feeRepo, paymentRepo, listOf(pgClient))
    }

    @Test
    @DisplayName("기본 결제 플로우를 처리해야 한다")
    fun pay() {
        // given
        val partnerId = 1L
        val amount = BigDecimal("10000")
        val cardBin = "123456"
        val cardLast4 = "7890"
        val productName = "테스트 상품"

        val command = PaymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardBin = cardBin,
            cardLast4 = cardLast4,
            productName = productName,
        )
        every { partnerRepo.findById(partnerId) } returns Partner(partnerId, "CODE", "Partner", true)

        val approveAt = LocalDateTime.of(2024, 2, 1, 12, 0)
        val requestSlot = slot<PgApproveRequest>()
        every { pgClient.supports(partnerId) } returns true
        every { pgClient.approve(capture(requestSlot)) } returns PgApproveResult(
            approvalCode = "APP-456",
            approvedAt = approveAt,
            status = PaymentStatus.CANCELED,
        )

        val policy = FeePolicy(
            id = 5L,
            partnerId = partnerId,
            effectiveFrom = approveAt.minusDays(1),
            percentage = BigDecimal("0.0300"),
            fixedFee = BigDecimal("200"),
        )
        every { feeRepo.findEffectivePolicy(partnerId, approveAt) } returns policy

        val savedSlot = slot<Payment>()
        every { paymentRepo.save(capture(savedSlot)) } answers { savedSlot.captured.copy(id = 100L) }

        // when
        val result = paymentService.pay(command)

        // then
        assertEquals(100L, result.id)
        assertEquals(BigDecimal("500"), result.feeAmount)
        assertEquals(BigDecimal("9500"), result.netAmount)
        assertEquals(PaymentStatus.CANCELED, result.status)
        assertEquals(approveAt, savedSlot.captured.approvedAt)
        assertEquals("7890", savedSlot.captured.cardLast4)

        assertEquals(partnerId, requestSlot.captured.partnerId)
        assertEquals(amount, requestSlot.captured.amount)
        assertEquals(cardBin, requestSlot.captured.cardBin)
        assertEquals(cardLast4, requestSlot.captured.cardLast4)
        assertEquals(productName, requestSlot.captured.productName)

        verify(exactly = 1) { pgClient.supports(partnerId) }
        verify(exactly = 1) { pgClient.approve(any()) }
        verify(exactly = 1) { feeRepo.findEffectivePolicy(partnerId, approveAt) }
        verify(exactly = 1) { paymentRepo.save(any()) }
    }

    @Test
    @DisplayName("제휴사가 없으면 예외가 발생해야 한다")
    fun payThrowsWhenPartnerMissing() {
        // given
        val partnerId = 99L
        val amount = BigDecimal("1000")
        val command = PaymentCommand(partnerId = partnerId, amount = amount)
        every { partnerRepo.findById(partnerId) } returns null

        // when & then
        assertFailsWith<IllegalArgumentException> { paymentService.pay(command) }
        verify(exactly = 0) { pgClient.supports(any()) }
        verify(exactly = 0) { feeRepo.findEffectivePolicy(any(), any()) }
        verify(exactly = 0) { paymentRepo.save(any()) }
    }

    @Test
    @DisplayName("비활성 제휴사는 결제가 거부되어야 한다")
    fun payThrowsWhenPartnerInactive() {
        // given
        val partnerId = 1L
        val amount = BigDecimal("2000")
        val command = PaymentCommand(partnerId = partnerId, amount = amount)
        every { partnerRepo.findById(partnerId) } returns Partner(partnerId, "INACTIVE", "Inactive", false)

        // when & then
        assertFailsWith<IllegalArgumentException> { paymentService.pay(command) }
        verify(exactly = 0) { pgClient.supports(any()) }
        verify(exactly = 0) { pgClient.approve(any()) }
        verify(exactly = 0) { feeRepo.findEffectivePolicy(any(), any()) }
        verify(exactly = 0) { paymentRepo.save(any()) }
    }

    @Test
    @DisplayName("지원 PG 가 없으면 예외가 발생해야 한다")
    fun payThrowsWhenNoPgClientSupports() {
        // given
        val partnerId = 1L
        val amount = BigDecimal("3000")
        val command = PaymentCommand(partnerId = partnerId, amount = amount)
        every { partnerRepo.findById(partnerId) } returns Partner(partnerId, "CODE", "Partner", true)
        every { pgClient.supports(partnerId) } returns false

        // when & then
        assertFailsWith<IllegalStateException> { paymentService.pay(command) }
        verify(exactly = 1) { pgClient.supports(partnerId) }
        verify(exactly = 0) { pgClient.approve(any()) }
        verify(exactly = 0) { feeRepo.findEffectivePolicy(any(), any()) }
        verify(exactly = 0) { paymentRepo.save(any()) }
    }

    @Test
    @DisplayName("수수료 정책이 없으면 예외가 발생해야 한다")
    fun payThrowsWhenFeePolicyMissing() {
        // given
        val partnerId = 1L
        val amount = BigDecimal("5000")
        val command = PaymentCommand(partnerId = partnerId, amount = amount)
        every { partnerRepo.findById(partnerId) } returns Partner(partnerId, "CODE", "Partner", true)

        val approveAt = LocalDateTime.of(2024, 3, 1, 10, 0)
        every { pgClient.supports(partnerId) } returns true
        every { pgClient.approve(any()) } returns PgApproveResult(
            approvalCode = "APP-789",
            approvedAt = approveAt,
            status = PaymentStatus.APPROVED,
        )
        every { feeRepo.findEffectivePolicy(partnerId, approveAt) } returns null

        // when & then
        assertFailsWith<IllegalArgumentException> { paymentService.pay(command) }
        verify(exactly = 1) { pgClient.supports(partnerId) }
        verify(exactly = 1) { pgClient.approve(any()) }
        verify(exactly = 1) { feeRepo.findEffectivePolicy(partnerId, approveAt) }
        verify(exactly = 0) { paymentRepo.save(any()) }
    }
}
