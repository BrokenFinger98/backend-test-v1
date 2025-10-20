package im.bigs.pg.application.payment.service

import im.bigs.pg.application.core.exception.ApplicationException
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
        val cardNumber = "1234-5678-9012-3456"
        val birthDate = "19900101"
        val expiry = "1227"
        val password = "12"
        val productName = "테스트 상품"

        val command = paymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardNumber = cardNumber,
            birthDate = birthDate,
            expiry = expiry,
            password = password,
            productName = productName,
        )
        every { partnerRepo.findById(partnerId) } returns activePartner(partnerId)

        val approveAt = LocalDateTime.of(2024, 2, 1, 12, 0)
        val requestSlot = slot<PgApproveRequest>()
        every { pgClient.supports(partnerId) } returns true
        every { pgClient.approve(capture(requestSlot)) } returns PgApproveResult(
            approvalCode = "APP-456",
            approvedAt = approveAt,
            status = PaymentStatus.CANCELED,
        )

        val policy = feePolicy(
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
        val normalized = cardNumber.filter { it.isDigit() }
        val expectedBin = normalized.take(6)
        val expectedLast4 = normalized.takeLast(4)

        assertEquals(100L, result.id)
        assertEquals(BigDecimal("500"), result.feeAmount)
        assertEquals(BigDecimal("9500"), result.netAmount)
        assertEquals(PaymentStatus.CANCELED, result.status)
        assertEquals(approveAt, savedSlot.captured.approvedAt)
        assertEquals(expectedBin, savedSlot.captured.cardBin)
        assertEquals(expectedLast4, savedSlot.captured.cardLast4)

        assertEquals(partnerId, requestSlot.captured.partnerId)
        assertEquals(amount, requestSlot.captured.amount)
        assertEquals(normalized, requestSlot.captured.cardNumber)
        assertEquals(birthDate, requestSlot.captured.birthDate)
        assertEquals(expiry, requestSlot.captured.expiry)
        assertEquals(password, requestSlot.captured.password)
        assertEquals(expectedBin, requestSlot.captured.cardBin)
        assertEquals(expectedLast4, requestSlot.captured.cardLast4)
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
        val command = paymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardNumber = "1234567890123456",
        )
        every { partnerRepo.findById(partnerId) } returns null

        // when & then
        val ex = assertFailsWith<ApplicationException> { paymentService.pay(command) }
        assertTrue(ex is ApplicationException.NotFound)
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
        val command = PaymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardNumber = "1234567890123456",
            cardExpiry = "1227",
            birthDate = "19900101",
            cardPassword = "12",
        )
        every { partnerRepo.findById(partnerId) } returns inactivePartner(partnerId)

        // when & then
        val ex = assertFailsWith<ApplicationException> { paymentService.pay(command) }
        assertTrue(ex is ApplicationException.Conflict)
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
        val command = paymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardNumber = "1234567890123456",
        )
        every { partnerRepo.findById(partnerId) } returns activePartner(partnerId)
        every { pgClient.supports(partnerId) } returns false

        // when & then
        val ex = assertFailsWith<ApplicationException> { paymentService.pay(command) }
        assertTrue(ex is ApplicationException.Conflict)
        verify(exactly = 1) { pgClient.supports(partnerId) }
        verify(exactly = 0) { pgClient.approve(any()) }
        verify(exactly = 0) { feeRepo.findEffectivePolicy(any(), any()) }
        verify(exactly = 0) { paymentRepo.save(any()) }
    }

    @Test
    @DisplayName("카드 번호 자릿수가 부족하면 예외가 발생해야 한다")
    fun payThrowsWhenCardNumberTooShort() {
        // given
        val partnerId = 1L
        val command = paymentCommand(
            partnerId = partnerId,
            amount = BigDecimal("1000"),
            cardNumber = "1234-5678",
        )
        every { partnerRepo.findById(partnerId) } returns activePartner(partnerId)
        every { pgClient.supports(partnerId) } returns true

        // when & then
        val ex = assertFailsWith<ApplicationException> { paymentService.pay(command) }
        assertTrue(ex is ApplicationException.BadRequest)

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
        val command = paymentCommand(
            partnerId = partnerId,
            amount = amount,
            cardNumber = "1234567890123456",
        )
        every { partnerRepo.findById(partnerId) } returns activePartner(partnerId)

        val approveAt = LocalDateTime.of(2024, 3, 1, 10, 0)
        every { pgClient.supports(partnerId) } returns true
        every { pgClient.approve(any()) } returns PgApproveResult(
            approvalCode = "APP-789",
            approvedAt = approveAt,
            status = PaymentStatus.APPROVED,
        )
        every { feeRepo.findEffectivePolicy(partnerId, approveAt) } returns null

        // when & then
        val ex = assertFailsWith<ApplicationException> { paymentService.pay(command) }
        assertTrue(ex is ApplicationException.NotFound)
        verify(exactly = 1) { pgClient.supports(partnerId) }
        verify(exactly = 1) { pgClient.approve(any()) }
        verify(exactly = 1) { feeRepo.findEffectivePolicy(partnerId, approveAt) }
        verify(exactly = 0) { paymentRepo.save(any()) }
    }
}

private fun paymentCommand(
    partnerId: Long,
    amount: BigDecimal,
    cardNumber: String,
    birthDate: String = "19900101",
    expiry: String = "1227",
    password: String = "12",
    productName: String? = null,
): PaymentCommand =
    PaymentCommand(
        partnerId = partnerId,
        amount = amount,
        cardNumber = cardNumber,
        cardExpiry = expiry,
        birthDate = birthDate,
        cardPassword = password,
        productName = productName,
    )

private fun activePartner(id: Long) = Partner(id, "CODE$id", "Partner$id", true)

private fun inactivePartner(id: Long) = Partner(id, "CODE$id", "Partner$id", false)

private fun feePolicy(
    partnerId: Long,
    effectiveFrom: LocalDateTime,
    percentage: BigDecimal,
    fixedFee: BigDecimal,
) = FeePolicy(
    id = null,
    partnerId = partnerId,
    effectiveFrom = effectiveFrom,
    percentage = percentage,
    fixedFee = fixedFee,
)
