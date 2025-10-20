package im.bigs.pg.external.pg.client.testpg.crypto

import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Cipher

@Component
class AesGcmEncryptor {

    fun encrypt(payload: String, client: ClientConfig): String {
        val secretKey = client.secretKey()
        val initializationVector = client.initializationVector()
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, initializationVector)
        val plainBytes = payload.toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(plainBytes)
        val encoder = Base64.getUrlEncoder()
        return encoder.withoutPadding().encodeToString(encrypted)
    }

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }
}
