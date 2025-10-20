package im.bigs.pg.api.payment.dto.response

import com.fasterxml.jackson.annotation.JsonFormat
import im.bigs.pg.domain.payment.Payment
import im.bigs.pg.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentResponse(
    val id: Long?,
    val partnerId: Long,
    val amount: BigDecimal,
    val appliedFeeRate: BigDecimal,
    val feeAmount: BigDecimal,
    val netAmount: BigDecimal,
    val cardLast4: String?,
    val approvalCode: String,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val approvedAt: LocalDateTime,
    val status: PaymentStatus,
    @get:JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            id = payment.id,
            partnerId = payment.partnerId,
            amount = payment.amount,
            appliedFeeRate = payment.appliedFeeRate,
            feeAmount = payment.feeAmount,
            netAmount = payment.netAmount,
            cardLast4 = payment.cardLast4,
            approvalCode = payment.approvalCode,
            approvedAt = payment.approvedAt,
            status = payment.status,
            createdAt = payment.createdAt,
        )
    }
}
