package im.bigs.pg.external.pg.client.testpg.transport

import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class TestPgSuccessResponse(
    val approvalCode: String,
    val approvedAt: String,
    val maskedCardLast4: String,
    val amount: BigDecimal,
    val status: String,
)

fun TestPgSuccessResponse.toDomain(): PgApproveResult {
    val normalizedStatus = status.uppercase()
    val paymentStatus = PaymentStatus.valueOf(normalizedStatus)
    val parsedApprovedAt = LocalDateTime.parse(approvedAt)
    return PgApproveResult(
        approvalCode = approvalCode,
        approvedAt = parsedApprovedAt,
        status = paymentStatus,
    )
}
