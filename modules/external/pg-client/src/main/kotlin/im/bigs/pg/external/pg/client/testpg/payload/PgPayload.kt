package im.bigs.pg.external.pg.client.testpg.payload

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import java.math.BigDecimal

data class EncryptedPayload(val value: String) {
    fun asRequestBody(): Map<String, String> = mapOf("enc" to value)
}

internal data class PgApprovePlaintext(
    private val cardDetails: CardDetails,
    private val birthDate: BirthDate,
    private val amount: Amount,
) {
    fun toJson(mapper: ObjectMapper): String {
        val payload = linkedMapOf<String, Any>()
        cardDetails.appendTo(payload)
        payload["birthDate"] = birthDate.value
        payload["amount"] = amount.value
        return mapper.writeValueAsString(payload)
    }

    companion object {
        fun from(request: PgApproveRequest): PgApprovePlaintext = PgApprovePlaintext(
            cardDetails = CardDetails.from(request),
            birthDate = BirthDate(request.birthDate),
            amount = Amount.from(request.amount),
        )
    }
}

internal data class CardDetails(
    private val cardNumber: String,
    private val expiry: String,
    private val password: String,
) {
    fun appendTo(target: MutableMap<String, Any>) {
        target["cardNumber"] = cardNumber
        target["expiry"] = expiry
        target["password"] = password
    }

    companion object {
        fun from(request: PgApproveRequest): CardDetails = CardDetails(
            cardNumber = request.cardNumber,
            expiry = request.expiry,
            password = request.password,
        )
    }
}

internal data class BirthDate(val value: String)

internal data class Amount(val value: Long) {
    companion object {
        fun from(amount: BigDecimal): Amount = Amount(amount.longValueExact())
    }
}
