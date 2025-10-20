package im.bigs.pg.application.payment.service

import im.bigs.pg.application.core.exception.badRequest
import im.bigs.pg.application.core.exception.conflict
import im.bigs.pg.application.core.exception.notFound
import im.bigs.pg.application.partner.port.out.FeePolicyOutPort
import im.bigs.pg.application.partner.port.out.PartnerOutPort
import im.bigs.pg.application.payment.port.`in`.PaymentCommand
import im.bigs.pg.application.payment.port.`in`.PaymentUseCase
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.domain.calculation.FeeCalculator
import im.bigs.pg.domain.partner.FeePolicy
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.partner.Partner
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

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
        val partner = findActivePartner(command.partnerId)
        val pgClient = selectPgClient(partner.id)
        val card = CardDetails.from(command.cardNumber)
        val approval = pgClient.approve(command.toPgApproveRequest(partner.id, card))
        val policy = fetchPolicy(partner.id, approval.approvedAt)
        val snapshot = command.toPaymentSnapshot(partner.id, card, approval, policy)
        return paymentRepository.save(snapshot)
    }

    private fun findActivePartner(partnerId: Long): Partner {
        val partner = partnerRepository.findById(partnerId)
            ?: throw notFound("Partner not found: $partnerId")
        if (!partner.active) {
            throw conflict("Partner is inactive: ${partner.id}")
        }
        return partner
    }

    private fun selectPgClient(partnerId: Long): PgClientOutPort =
        pgClients.firstOrNull { it.supports(partnerId) }
            ?: throw conflict("No PG client for partner $partnerId")

    private fun fetchPolicy(partnerId: Long, approvedAt: LocalDateTime): FeePolicy =
        feePolicyRepository.findEffectivePolicy(partnerId, approvedAt)
            ?: throw notFound("Fee Policy not found: $partnerId")

    private fun PaymentCommand.toPgApproveRequest(partnerId: Long, card: CardDetails): PgApproveRequest =
        PgApproveRequest(
            partnerId = partnerId,
            amount = amount,
            cardNumber = card.normalized,
            birthDate = birthDate,
            expiry = cardExpiry,
            password = cardPassword,
            cardBin = card.bin,
            cardLast4 = card.last4,
            productName = productName,
        )

    private fun PaymentCommand.toPaymentSnapshot(
        partnerId: Long,
        card: CardDetails,
        approval: PgApproveResult,
        policy: FeePolicy,
    ): Payment {
        val (feeAmount, netAmount) = FeeCalculator.calculateFee(amount, policy.percentage, policy.fixedFee)
        return Payment(
            partnerId = partnerId,
            amount = amount,
            appliedFeeRate = policy.percentage,
            feeAmount = feeAmount,
            netAmount = netAmount,
            cardBin = card.bin,
            cardLast4 = card.last4,
            approvalCode = approval.approvalCode,
            approvedAt = approval.approvedAt,
            status = approval.status,
        )
    }

    private data class CardDetails(
        val normalized: String,
        val bin: String,
        val last4: String,
    ) {
        companion object {
            private const val MIN_DIGITS = 10

            fun from(raw: String): CardDetails {
                val digits = raw.filter(Char::isDigit)
                if (digits.length < MIN_DIGITS) {
                    throw badRequest("Card number must contain at least $MIN_DIGITS digits")
                }
                return CardDetails(
                    normalized = digits,
                    bin = digits.take(6),
                    last4 = digits.takeLast(4),
                )
            }
        }
    }
}
