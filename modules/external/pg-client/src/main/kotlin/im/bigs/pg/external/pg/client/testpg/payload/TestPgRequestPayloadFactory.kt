package im.bigs.pg.external.pg.client.testpg.payload

import com.fasterxml.jackson.databind.ObjectMapper
import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.external.pg.client.testpg.crypto.AesGcmEncryptor
import im.bigs.pg.external.pg.client.testpg.crypto.ClientConfig
import org.springframework.stereotype.Component

@Component
class TestPgRequestPayloadFactory(
    private val objectMapper: ObjectMapper,
    private val encryptor: AesGcmEncryptor,
) {

    fun create(request: PgApproveRequest, client: ClientConfig): EncryptedPayload {
        val plaintext = PgApprovePlaintext.from(request)
        val json = plaintext.toJson(objectMapper)
        val encrypted = encryptor.encrypt(json, client)
        return EncryptedPayload(encrypted)
    }
}
