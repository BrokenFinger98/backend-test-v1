package im.bigs.pg.external.pg.client.testpg.crypto

import im.bigs.pg.external.pg.config.TestPgProperties
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class ClientConfig(
    private val apiKey: ApiKey,
    private val initializationVector: InitializationVector,
) {
    fun secretKey(): SecretKeySpec = apiKey.secretKeySpec()

    fun initializationVector(): GCMParameterSpec = initializationVector.gcmParameterSpec()

    fun headerValue(): String = apiKey.header()

    companion object {
        fun from(raw: TestPgProperties.Client): ClientConfig = ClientConfig(
            apiKey = ApiKey(raw.apiKey),
            initializationVector = InitializationVector(raw.iv),
        )
    }
}

data class ApiKey(private val value: String) {
    fun secretKeySpec(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val plainBytes = value.toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(plainBytes)
        return SecretKeySpec(hashed, "AES")
    }

    fun header(): String = value
}

data class InitializationVector(private val encoded: String) {
    fun gcmParameterSpec(): GCMParameterSpec {
        val decoder = Base64.getUrlDecoder()
        val ivBytes = decoder.decode(encoded)
        return GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes)
    }

    companion object {
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
