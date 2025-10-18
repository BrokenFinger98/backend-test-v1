package im.bigs.pg.external.pg.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "pg.test")
class TestPgProperties {
    var baseUrl: String = "https://api-test-pg.bigs.im"
    var clients: List<Client> = emptyList()

    fun findClient(partnerId: Long): Client? = clients.firstOrNull { it.partnerId == partnerId }

    class Client {
        var partnerId: Long = 0L
        lateinit var apiKey: String
        lateinit var iv: String
    }
}
