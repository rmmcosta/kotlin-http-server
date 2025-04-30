import io.mockk.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

class MyHttpServerTest {
    private val out = ByteArrayOutputStream()
    private val client = mockk<Socket> {
        every { getOutputStream() } returns out
        every { close() } just runs
    }
    private val serverSocket = mockk<ServerSocket> {
        every { accept() } returns client
        every { setReuseAddress(true) } just runs
        every { close() } just runs
    }
    private val httpServer = MyHttpServer(serverSocket, httpRequestParser = HttpRequestParser())

    @Test
    fun `given an http server, when sending an http get request, then a 200 ok should be received back`() {
        //given
        val input =
            ByteArrayInputStream("GET / HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: curl/7.64.1\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        val clientThread = httpServer.initServer(endlessLoop = false)
        val expectedResponse = "HTTP/1.1 200 OK\r\n\r\n".toByteArray()

        clientThread?.join()

        assert(out.toByteArray().contentEquals(expectedResponse)) { "the outcome is not as expected: $out" }

        //then
        verify {
            client.close()
        }
        httpServer.closeServer()
    }

    @Test
    fun `given an http server, when sending an http get request to an unknown url path, then a 400 Not Found should be received back`() {
        //given
        val input =
            ByteArrayInputStream("GET /abcde HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: curl/7.64.1\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        val clientThread = httpServer.initServer(endlessLoop = false)
        val expectedResponse = "HTTP/1.1 404 Not Found\r\n\r\n".toByteArray()

        clientThread?.join()

        assert(out.toByteArray().contentEquals(expectedResponse)) { "the outcome is not as expected: $out" }

        //then
        verify {
            client.close()
        }
        httpServer.closeServer()
    }

    @Test
    fun `given an http server, when sending an http get request to a known url path, then a 200 OK should be received back`() {
        //given
        val input =
            ByteArrayInputStream("GET / HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: curl/7.64.1\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        val clientThread = httpServer.initServer(endlessLoop = false)
        val expectedResponse = "HTTP/1.1 200 OK\r\n\r\n".toByteArray()

        clientThread?.join()

        assert(out.toByteArray().contentEquals(expectedResponse)) { "the outcome is not as expected: $out" }

        //then
        verify {
            client.close()
        }
        httpServer.closeServer()
    }

    @Test
    fun `given an http server, when sending an http get request to the echo str url path, then a 200 OK should be received back and the str in the response body`() {
        //given
        val input =
            ByteArrayInputStream("GET /echo/hello HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: curl/7.64.1\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        val clientThread = httpServer.initServer(endlessLoop = false)
        val expectedResponseString = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 5\r\n\r\nhello"
        val expectedResponse = expectedResponseString.toByteArray()

        clientThread?.join()

        assert(
            out.toByteArray().contentEquals(expectedResponse)
        ) { "the outcome is not as expected: $out vs $expectedResponseString" }

        //then
        verify {
            client.close()
        }
        httpServer.closeServer()
    }

    @Test
    fun `given an http server, when sending an http get request to the user agent url path, then a 200 OK should be received back and the value of the user agent header in the response body`() {
        //given
        val userAgentValue = "curl/7.64.1"
        val input =
            ByteArrayInputStream("GET /user-agent HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: $userAgentValue\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        val clientThread = httpServer.initServer(endlessLoop = false)
        val expectedResponseString =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${userAgentValue.length}\r\n\r\n$userAgentValue"
        val expectedResponse = expectedResponseString.toByteArray()

        clientThread?.join()

        assert(
            out.toByteArray().contentEquals(expectedResponse)
        ) { "the outcome is not as expected: $out vs $expectedResponseString" }

        //then
        verify {
            client.close()
        }
        httpServer.closeServer()
    }

    @Test
    fun `given an http server, when sending an http get request to the files url path followed by a valid file, then a 200 OK should be received back and the file content in the response body`() {
        //given
        val fileContent = "Hello, World!"

        val defaultDir = System.getProperty("user.dir")
        println("default dir in tests: $defaultDir")
        val path = Paths.get("xpto")
        if (Files.notExists(path)) {
            val file = Files.createFile(path)
            //file.createNewFile()
            file.writeText(fileContent)
        }
        val input =
            ByteArrayInputStream("GET /files/xpto HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: firefox\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        try {
            val clientThread = httpServer.initServer(endlessLoop = false)
            val expectedResponseString =
                "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${fileContent.length}\r\n\r\n$fileContent"
            val expectedResponse = expectedResponseString.toByteArray()

            clientThread?.join()

            assert(
                out.toByteArray().contentEquals(expectedResponse)
            ) { "the outcome is not as expected: $out vs $expectedResponseString" }

            //then
            verify {
                client.close()
            }
        } finally {
            httpServer.closeServer()
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `given an http server, when sending an http get request to the files url path followed by a invalid file, then a 404 Not Found should be received back and the file content in the response body`() {
        //given
        val defaultDir = System.getProperty("user.dir")
        println("default dir in tests: $defaultDir")

        val input =
            ByteArrayInputStream("GET /files/xpto HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: firefox\r\nAccept: */*\r\n\r\n".toByteArray())
        every { client.getInputStream() } returns input
        try {
            val clientThread = httpServer.initServer(endlessLoop = false)
            val expectedResponseString =
                "HTTP/1.1 404 Not Found\r\n\r\n"
            val expectedResponse = expectedResponseString.toByteArray()

            clientThread?.join()

            assert(
                out.toByteArray().contentEquals(expectedResponse)
            ) { "the outcome is not as expected: $out vs $expectedResponseString" }

            //then
            verify {
                client.close()
            }
        } finally {
            httpServer.closeServer()
        }
    }

    @Test
    fun `given an http server, when sending an http post request to the files url path followed by a valid file, then a 201 Created should be received back`() {
        //given
        val input =
            ByteArrayInputStream("POST /files/xpto HTTP/1.1\r\nHost: localhost:4221\r\nUser-Agent: firefox\r\nAccept: */*\r\nContent-Type: application/octet-stream\r\nContent-Length: 5\r\n\r\n12345".toByteArray())
        every { client.getInputStream() } returns input
        try {
            val clientThread = httpServer.initServer(endlessLoop = false)
            val expectedResponseString = "HTTP/1.1 201 Created\r\n\r\n"
            val expectedResponse = expectedResponseString.toByteArray()

            clientThread?.join()

            assert(
                out.toByteArray().contentEquals(expectedResponse)
            ) { "the outcome is not as expected: $out vs $expectedResponseString" }

            //then
            verify {
                client.close()
            }
        } finally {
            httpServer.closeServer()
            File("${System.getProperty("user.dir")}/xpto").delete()
        }
    }
}
