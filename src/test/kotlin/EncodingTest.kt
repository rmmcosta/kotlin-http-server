import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class EncodingTest {
    private val httpRequestParser = mockk<HttpRequestParser>()
    private val requestHandler = RequestHandler(httpRequestParser)

    @Test
    fun `given a supported accept encoding request, when building the response, then a content encoding with the right encoding must be returned`() {
        //given
        every { httpRequestParser.parse(any()) } returns HttpRequest(
            HttpMethod.GET, "/echo/xpto", mapOf("Accept-Encoding" to "gzip")
        )

        //when
        val clientSocket = mockk<Socket> {
            every { inputStream } returns mockk<InputStream>()
            every { outputStream } returns mockk<OutputStream> {
                every { write(any<ByteArray>()) } just runs
                every { close() } just runs
                every { flush() } just runs
            }
            every { close() } just runs
        }
        requestHandler.handleClient(clientSocket, "")

        //then
        val expectedResult =
            "HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Type: text/plain\r\nContent-Length: 4\r\n\r\nxpto"
        println("expected result:$expectedResult")

        verify {
            clientSocket.outputStream.write(expectedResult.toByteArray())
        }
    }
}