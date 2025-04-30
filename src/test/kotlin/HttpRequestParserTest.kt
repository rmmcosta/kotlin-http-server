import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HttpRequestParserTest {
    @Test
    fun `given a valid http post request, when calling the http request parser, then the request should be correctly parsed`() {
        //given
        val request =
            "POST /files/number HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: curl/7.64.1\r\nAccept: */*\r\nContent-Type: application/octet-stream\r\nContent-Length: 5\r\n\r\n12345"
        val parser = HttpRequestParser()
        val expectedResult = HttpRequest(
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

        //when
        val result = parser.parse(request.byteInputStream())

        //then
        assertEquals(expectedResult, result)
    }
}