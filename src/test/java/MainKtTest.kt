import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import khttp.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class MainKtTest {
    @Test
    fun `given and endless loop running http server, when making multiple valid requests, then the server responds well`() {
        val okSimpleResponse = "HTTP/1.1 200 OK\r\n\r\n".toByteArray()
        val notFoundResponse = "HTTP/1.1 404 Not Found\r\n\r\n".toByteArray()
        val userAgentValue = "Agent 47"
        val userAgentResponse =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${userAgentValue.length}\r\n\r\n$userAgentValue".toByteArray()

        runBlocking {
            thread {
                main()
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

        get("http://localhost:$SERVER_PORT/stop")
    }

    private fun userAgentHttpCall(userAgentValue: String) {
        val result = get("http://localhost:$SERVER_PORT/user-agent", mapOf("User-Agent" to userAgentValue))
        val statusCode = result.statusCode
        val body = String(result.content)
        assert(statusCode == 200) { "status code was $statusCode" }
        assert(body == userAgentValue) { "user agent was $body" }
    }

    private fun notFoundHttpCall() {
        val result = get("http://localhost:$SERVER_PORT/abcde")
        val statusCode = result.statusCode
        assert(statusCode == 404) { "status code was $statusCode" }
    }

    private fun simpleRootHttpCall() {
        val result = get("http://localhost:$SERVER_PORT")
        val statusCode = result.statusCode
        val body = String(result.content)
        assert(statusCode == 200) { "status code was $statusCode" }
        assert(body == "") { "body was $body" }
    }
}