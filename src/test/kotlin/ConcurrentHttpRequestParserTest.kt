import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.RepeatedTest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ConcurrentHttpRequestParserTest {

    private val parser = HttpRequestParser()

    private val request = """
        POST /files/number HTTP/1.1
        Host: localhost:4221
        User-Agent: curl/7.64.1
        Accept: */*
        Content-Type: application/octet-stream
        Content-Length: 5

        12345
    """.trimIndent().replace("\n", "\r\n")

    private val expectedResult = HttpRequest(
        HttpMethod.POST,
        "/files/number",
        mapOf(
            "Host" to "localhost:4221",
            "User-Agent" to "curl/7.64.1",
            "Accept" to "*/*",
            "Content-Type" to "application/octet-stream",
            "Content-Length" to "5"
        ),
        "12345"
    )

    @RepeatedTest(5)
    fun `parser should work correctly under concurrent access`() {
        val threadCount = 20
        val executor = Executors.newFixedThreadPool(threadCount)

        val tasks = (1..threadCount).map {
            Callable {
                val parsed = parser.parse(request.byteInputStream())
                assertEquals(expectedResult, parsed)
            }
        }

        val futures = executor.invokeAll(tasks)
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        // Propagate any assertion errors
        futures.forEach { it.get() }
    }
}
