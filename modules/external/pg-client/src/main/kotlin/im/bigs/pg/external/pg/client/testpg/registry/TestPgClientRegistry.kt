package im.bigs.pg.external.pg.client.testpg.registry

import im.bigs.pg.external.pg.client.testpg.crypto.ClientConfig
import im.bigs.pg.external.pg.config.TestPgProperties
import im.bigs.pg.external.pg.exception.pgConfigurationMissing
import org.springframework.stereotype.Component

@Component
class TestPgClientRegistry(
    private val properties: TestPgProperties,
) {

    fun supports(partnerId: Long): Boolean = properties.findClient(partnerId) != null

    fun clientFor(partnerId: Long): ClientConfig {
        val rawClient = properties.findClient(partnerId) ?: throw pgConfigurationMissing(CLIENT_TYPE, partnerId)
        return ClientConfig.from(rawClient)
    }

    companion object {
        private const val CLIENT_TYPE = "TEST_PG"
    }
}
