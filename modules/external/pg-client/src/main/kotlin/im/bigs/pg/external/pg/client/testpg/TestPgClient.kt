package im.bigs.pg.external.pg.client.testpg

import im.bigs.pg.application.pg.port.out.PgApproveRequest
import im.bigs.pg.application.pg.port.out.PgApproveResult
import im.bigs.pg.application.pg.port.out.PgClientOutPort
import im.bigs.pg.external.pg.client.testpg.payload.TestPgRequestPayloadFactory
import im.bigs.pg.external.pg.client.testpg.registry.TestPgClientRegistry
import im.bigs.pg.external.pg.client.testpg.transport.TestPgRestInvoker
import im.bigs.pg.external.pg.client.testpg.transport.toDomain
import org.springframework.stereotype.Component

@Component
class TestPgClient(
    private val registry: TestPgClientRegistry,
    private val payloadFactory: TestPgRequestPayloadFactory,
    private val restInvoker: TestPgRestInvoker,
) : PgClientOutPort {

    override fun supports(partnerId: Long): Boolean = registry.supports(partnerId)

    override fun approve(request: PgApproveRequest): PgApproveResult {
        val client = registry.clientFor(request.partnerId)
        val payload = payloadFactory.create(request, client)
        val response = restInvoker.requestApproval(client, payload)
        return response.toDomain()
    }
}
