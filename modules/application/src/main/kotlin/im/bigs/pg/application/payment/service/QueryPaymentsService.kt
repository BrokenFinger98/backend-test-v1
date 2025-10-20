package im.bigs.pg.application.payment.service

import im.bigs.pg.application.core.exception.badRequest
import im.bigs.pg.application.payment.port.`in`.QueryFilter
import im.bigs.pg.application.payment.port.`in`.QueryPaymentsUseCase
import im.bigs.pg.application.payment.port.`in`.QueryResult
import im.bigs.pg.application.payment.port.out.PaymentOutPort
import im.bigs.pg.application.payment.port.out.PaymentPage
import im.bigs.pg.application.payment.port.out.PaymentQuery
import im.bigs.pg.application.payment.port.out.PaymentSummaryFilter
import im.bigs.pg.application.payment.port.out.PaymentSummaryProjection
import im.bigs.pg.domain.payment.PaymentStatus
import im.bigs.pg.domain.payment.PaymentSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

/**
 * 결제 이력 조회 유스케이스 구현체.
 * - 커서 토큰은 createdAt/id를 안전하게 인코딩해 전달/복원합니다.
 * - 통계는 조회 조건과 동일한 집합을 대상으로 계산됩니다.
 */
@Service
@Transactional(readOnly = true)
class QueryPaymentsService(
    private val paymentRepository: PaymentOutPort,
) : QueryPaymentsUseCase {
    /**
     * 필터를 기반으로 결제 내역을 조회합니다.
     * 커서 기준(createdAt, id) 페이지네이션을 수행하며 limit가 0 이하일 경우 기본값을 적용합니다.
     * 조회된 목록과 동일 조건으로 요약 통계를 계산해 함께 반환합니다.
     *
     * @param filter 파트너/상태/기간/커서/페이지 크기
     * @return 조회 결과(목록/통계/커서)
     */
    override fun query(filter: QueryFilter): QueryResult {
        val status = parseStatus(filter.status)
        val limit = filter.limit.takeIf { it > 0 } ?: DEFAULT_LIMIT

        val cursor = CursorCodec.decode(filter.cursor)
        val page = paymentRepository.findBy(
            PaymentQuery(
                partnerId = filter.partnerId,
                status = status,
                from = filter.from,
                to = filter.to,
                limit = limit,
                cursorCreatedAt = cursor?.createdAt,
                cursorId = cursor?.id,
            ),
        )

        val summaryProjection = paymentRepository.summary(
            PaymentSummaryFilter(
                partnerId = filter.partnerId,
                status = status,
                from = filter.from,
                to = filter.to,
            ),
        )

        return QueryResult(
            items = page.items,
            summary = summaryProjection.toSummary(),
            nextCursor = CursorCodec.encode(page),
            hasNext = page.hasNext,
        )
    }

    private fun parseStatus(status: String?): PaymentStatus? {
        if (status.isNullOrBlank()) return null
        return runCatching { PaymentStatus.valueOf(status.uppercase()) }
            .getOrElse { throw badRequest("Invalid status: $status") }
    }

    companion object {
        private const val DEFAULT_LIMIT = 20
    }

    private data class Cursor(
        val createdAt: java.time.LocalDateTime,
        val id: Long,
    )

    private object CursorCodec {
        fun decode(token: String?): Cursor? {
            if (token.isNullOrBlank()) return null
            return runCatching {
                val raw = String(Base64.getUrlDecoder().decode(token))
                val (millis, id) = raw.split(":", limit = 2)
                val instant = Instant.ofEpochMilli(millis.toLong())
                Cursor(
                    createdAt = instant.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                    id = id.toLong(),
                )
            }.getOrNull()
        }

        fun encode(page: PaymentPage): String? {
            if (!page.hasNext) return null
            val createdAt = page.nextCursorCreatedAt ?: return null
            val id = page.nextCursorId ?: return null
            val instant = createdAt.toInstant(ZoneOffset.UTC)
            val raw = "${instant.toEpochMilli()}:$id"
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        }
    }

    private fun PaymentSummaryProjection.toSummary(): PaymentSummary =
        PaymentSummary(count = count, totalAmount = totalAmount, totalNetAmount = totalNetAmount)
}
