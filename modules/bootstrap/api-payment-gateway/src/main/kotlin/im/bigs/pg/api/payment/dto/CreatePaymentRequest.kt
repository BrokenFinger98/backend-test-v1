package im.bigs.pg.api.payment.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.math.BigDecimal

data class CreatePaymentRequest(
    val partnerId: Long,
    @field:Min(1)
    val amount: BigDecimal,
    @field:NotBlank
    @field:Pattern(regexp = "^(?=(?:.*\\d){10,})[0-9-]+$", message = "cardNumber must contain at least 10 digits and only digits or hyphens")
    val cardNumber: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{8}$", message = "birthDate must be 8 digits in YYYYMMDD format")
    val birthDate: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{4}$", message = "expiry must be 4 digits in MMYY format")
    val expiry: String,
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{2}$", message = "cardPassword must be 2 digits")
    val cardPassword: String,
    val productName: String? = null,
)
