import org.junit.jupiter.api.Test
import khttp.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

private const val SERVER_PORT_TEST = 4222

class MainKtTest {
    @Test
    fun `given and endless loop running http server, when making multiple valid requests, then the server responds well`() {
        val userAgentValue = "Agent 47"

        runBlocking {
            thread {
                main(arrayOf("httpServer", SERVER_PORT_TEST.toString()))
            }

            delay(500)

            buildList {
                add(launch {
                    simpleRootHttpCall()
                })
                add(launch {
                    simpleRootHttpCall()
                })
                add(launch {
                    notFoundHttpCall()
                })
                add(launch {
                    notFoundHttpCall()
                })
                add(launch {
                    userAgentHttpCall(userAgentValue)
                })
                add(launch {
                    userAgentHttpCall(userAgentValue)
                })
            }.joinAll()
        }

        get("http://localhost:$SERVER_PORT_TEST/stop")
    }

    private fun userAgentHttpCall(userAgentValue: String) {
        val result = get("http://localhost:$SERVER_PORT_TEST/user-agent", mapOf("User-Agent" to userAgentValue))
        val statusCode = result.statusCode
        val body = String(result.content)
        assert(statusCode == 200) { "status code was $statusCode" }
        assert(body == userAgentValue) { "user agent was $body" }
    }

    private fun notFoundHttpCall() {
        val result = get("http://localhost:$SERVER_PORT_TEST/abcde")
        val statusCode = result.statusCode
        assert(statusCode == 404) { "status code was $statusCode" }
    }

    private fun simpleRootHttpCall() {
        val result = get("http://localhost:$SERVER_PORT_TEST")
        val statusCode = result.statusCode
        val body = String(result.content)
        assert(statusCode == 200) { "status code was $statusCode" }
        assert(body == "") { "body was $body" }
    }
}