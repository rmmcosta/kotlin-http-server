import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.net.ServerSocket

class ConcurrentConnectionsTest {

    private lateinit var server: MyHttpServer
    private lateinit var serverThread: Thread

    @BeforeEach
    fun setUp() {
        val serverSocket = ServerSocket(SERVER_PORT)
        server = MyHttpServer(serverSocket, httpRequestParser = HttpRequestParser())

        serverThread = Thread {
            server.initServer(endlessLoop = true)
        }
        serverThread.start()

        // Give the server a moment to start
        Thread.sleep(200)
    }

    @AfterEach
    fun tearDown() {
        server.closeServer()
        serverThread.join(500)
    }

    @Test
    fun `test concurrent requests`() = runBlocking {
        val clientCount = 20

        val responses = (1..clientCount).map {
            async(Dispatchers.IO) {
                sendRequestAndReadResponse(
                    "GET /echo/hello-$it HTTP/1.1\r\nHost: localhost\r\n\r\n"
                )
            }
        }.awaitAll()

        responses.forEachIndexed { index, response ->
            val expected = "hello-${index + 1}"
            assert(response.contains(expected)) {
                "Expected response to contain '$expected', but was: $response"
            }
        }
    }

    @Test
    @Disabled
    fun `test concurrent bad requests`() = runBlocking {
        val clientCount = 10

        val responses = (1..clientCount).map {
            async(Dispatchers.IO) {
                sendRequestAndReadResponse(
                    "INVALID_REQUEST\r\n"
                )
            }
        }.awaitAll()

        responses.forEach { response ->
            assert(response.contains("400 Bad Request")) {
                "Expected 400 Bad Request, but was: $response"
            }
        }
    }
}
