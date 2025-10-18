package im.bigs.pg.application.payment.service

import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.domain.calculation.FeeCalculator
import im.bigs.pg.domain.payment.Payment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 생성 유스케이스 구현체.
 * - 입력(REST 등) → 도메인/외부PG/영속성 포트를 순차적으로 호출하는 흐름을 담당합니다.
 * - 수수료 정책 조회 및 적용(계산)은 도메인 유틸리티를 통해 수행합니다.
 */
@Service
@Transactional
class PaymentService(
    private val partnerRepository: PartnerOutPort,
    private val feePolicyRepository: FeePolicyOutPort,
    private val paymentRepository: PaymentOutPort,
    private val pgClients: List<PgClientOutPort>,
) : PaymentUseCase {
    /**
     * 결제 승인/수수료 계산/저장을 순차적으로 수행합니다.
     * - 현재 예시 구현은 하드코드된 수수료(3% + 100)로 계산합니다.
     * - 과제: 제휴사별 수수료 정책을 적용하도록 개선해 보세요.
     */
    override fun pay(command: PaymentCommand): Payment {
        val partner = partnerRepository.findById(command.partnerId)
            ?: throw IllegalArgumentException("Partner not found: ${command.partnerId}")
        require(partner.active) { "Partner is inactive: ${partner.id}" }

        val pgClient = pgClients.firstOrNull { it.supports(partner.id) }
            ?: throw IllegalStateException("No PG client for partner ${partner.id}")

        val normalizedCardNumber = normalizeCardNumber(command.cardNumber)
        require(normalizedCardNumber.length >= 10) { "Card number must contain at least 10 digits" }
        val cardBin = normalizedCardNumber.take(6)
        val cardLast4 = normalizedCardNumber.takeLast(4)

        val approve = pgClient.approve(
            PgApproveRequest(
                partnerId = partner.id,
                amount = command.amount,
                cardNumber = normalizedCardNumber,
                birthDate = command.birthDate,
                expiry = command.cardExpiry,
                password = command.cardPassword,
                cardBin = cardBin,
                cardLast4 = cardLast4,
                productName = command.productName,
            ),
        )

        val feePolicy = feePolicyRepository.findEffectivePolicy(partner.id, approve.approvedAt)
            ?: throw IllegalArgumentException("Fee Policy not found: ${partner.id}")

        val (fee, net) = FeeCalculator.calculateFee(command.amount, feePolicy.percentage, feePolicy.fixedFee)
        val payment = Payment(
            partnerId = partner.id,
            amount = command.amount,
            appliedFeeRate = feePolicy.percentage,
            feeAmount = fee,
            netAmount = net,
            cardBin = cardBin,
            cardLast4 = cardLast4,
            approvalCode = approve.approvalCode,
            approvedAt = approve.approvedAt,
            status = approve.status,
        )

        return paymentRepository.save(payment)
    }

    private fun normalizeCardNumber(value: String): String = value.filter { it.isDigit() }
}
