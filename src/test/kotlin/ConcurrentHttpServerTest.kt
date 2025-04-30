import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrentHttpServerTest {

    companion object {
        private const val PORT = 4222
        private lateinit var serverThread: Thread
        private lateinit var serverSocket: ServerSocket

        @BeforeAll
        @JvmStatic
        fun setup() {
            serverSocket = ServerSocket(PORT)
            serverThread = Thread {
                val server = MyHttpServer(serverSocket, System.getProperty("user.dir"), HttpRequestParser())
                server.initServer(endlessLoop = true)
            }
            serverThread.start()
            Thread.sleep(1000) // Give the server time to start
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            try {
                URL("http://localhost:$PORT/stop").openConnection().apply {
                    connectTimeout = 500
                    readTimeout = 500
                }.getInputStream().close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    @Test
    fun `test concurrent GET requests`() {
        val numClients = 10
        val executor = Executors.newFixedThreadPool(numClients)
        val results = mutableListOf<Pair<String, String>>() // Pair of expected path segment and response body
        val lock = Any()

        repeat(numClients) { i ->
            executor.submit {
                val clientPath = "client-$i"
                val url = URL("http://localhost:$PORT/echo/$clientPath")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1000
                connection.readTimeout = 1000

                val responseCode = connection.responseCode
                val responseBody = connection.inputStream.bufferedReader().readText()

                synchronized(lock) {
                    if (responseCode == 200) {
                        results.add(clientPath to responseBody)
                    }
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)
        assertTrue(finished, "All threads should complete within timeout")

        // Validate that each client-X got echoed back
        assertEquals(numClients, results.size, "All requests should have succeeded")
        results.forEach { (clientPath, body) ->
            assertTrue(body.contains(clientPath), "Response body should contain '$clientPath'")
        }
    }
}
