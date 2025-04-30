import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.CharBuffer
import kotlin.concurrent.thread

const val SERVER_PORT = 4221

class MyHttpServer(
    private val serverSocket: ServerSocket,
    private val defaultDir: String = "",
) {

    fun initServer(endlessLoop: Boolean = true): Thread? {
        println("working dir: $defaultDir")
        serverSocket.reuseAddress = true
        var ranOnce = false
        var clientThread: Thread? = null

        while (!ranOnce || endlessLoop) {
            val clientSocket = serverSocket.accept()
            clientThread = thread {
                handleClient(clientSocket)
            }
            ranOnce = true
        }
        return clientThread
    }

    private fun handleClient(clientSocket: Socket) {
        clientSocket.use { socket ->
            println("Accepted new connection")

            val inputStream = socket.getInputStream()
            val bufferedReader = inputStream.bufferedReader()
            val requestLine = bufferedReader.readLine().orEmpty()
            val urlPath = extractUrlPath(requestLine)
            val responseBytes = try {
                val method = extractMethod(requestLine)
                println("urlPath: $urlPath")

                val responseStatus = determineStatus(urlPath, method)
                println("response status: $responseStatus")

                handleRequest(method, responseStatus, urlPath, bufferedReader, inputStream)
            } catch (_: Exception) {
                "HTTP/1.1 ${HttpStatus.BAD_REQUEST}\r\n\r\n".toByteArray()
            }

            println("request handled:${String(responseBytes)}")

            socket.getOutputStream().use { out ->
                out.write(responseBytes)
                out.flush()
            }

            if (urlPath == KnownUrlPaths.STOP.urlPath) {
                closeServer()
            }
        }
    }

    private fun handleRequest(
        httpMethod: HttpMethod,
        responseStatus: HttpStatus,
        urlPath: String,
        bufferedReader: BufferedReader,
        inputStream: InputStream
    ): ByteArray = if (httpMethod == HttpMethod.GET) buildResponse(responseStatus, urlPath, bufferedReader)
    else handlePost(responseStatus, urlPath, bufferedReader, inputStream)

    private fun handlePost(
        responseStatus: HttpStatus, urlPath: String, bufferedReader: BufferedReader, inputStream: InputStream
    ): ByteArray = if (responseStatus == HttpStatus.CREATED) {
        createResource(urlPath, bufferedReader, inputStream)
        "HTTP/1.1 $responseStatus\r\n\r\n".toByteArray()
    } else "".toByteArray()

    private fun createResource(urlPath: String, bufferedReader: BufferedReader, inputStream: InputStream) {
        val firstResource = getFirstResource(urlPath)
        val resourceName = urlPath.removePrefix("$firstResource/")
        val contentLength = extractContentLength(bufferedReader)
        println("content length extracted")
        val payload = extractPayload(bufferedReader, contentLength)
        println("payload extracted")
        val file = File("$defaultDir$resourceName")
        file.createNewFile()
        file.writeText(payload)
        println("file written")
    }

    private fun extractPayload(bufferedReader: BufferedReader, contentLength: Int): String {
        val buffer = mutableListOf<String>()
        var bytesRead = 0
        while (bytesRead < contentLength) {
            val line = bufferedReader.readLine()
            buffer.add(line)
            bytesRead += line.length
            println("bytes read:$bytesRead")
        }
        println("final bytes read:$bytesRead")
        return buffer.joinToString("\n")
    }

    private fun extractContentLength(bufferedReader: BufferedReader): Int =
        extractFromRequestHeaders("Content-Length", bufferedReader).toInt()

    private fun extractMethod(requestLine: String): HttpMethod {
        val firstBlankSpace = requestLine.indexOf(" ")
        return firstBlankSpace.takeIf { it != -1 }?.let { HttpMethod.fromValue(requestLine.substring(0, it)) }
            ?: throw RuntimeException("Invalid http method")
    }

    private fun buildResponse(httpStatus: HttpStatus, urlPath: String, reader: BufferedReader): ByteArray {
        return try {
            val responseBody = buildResponseBody(urlPath, reader)
            val response = "HTTP/1.1 $httpStatus\r\n$responseBody"
            println("Final response: $response")
            println("End of final response")
            response.toByteArray()
        } catch (_: FileNotFoundException) {
            "HTTP/1.1 ${HttpStatus.NOT_FOUND}\r\n\r\n".toByteArray()
        }
    }

    private fun buildResponseBody(urlPath: String, reader: BufferedReader): String {
        val (body, type) = when (val resource = getFirstResource(urlPath)) {
            KnownUrlPaths.ECHO.urlPath -> urlPath.removePrefix(resource).trimStart('/') to "text/plain"
            KnownUrlPaths.USER_AGENT.urlPath -> extractFromRequestHeaders("User-Agent", reader) to "text/plain"
            KnownUrlPaths.FILES.urlPath -> getFileContents(urlPath.removePrefix("$resource/")) to "application/octet-stream"
            else -> "" to ""
        }
        return if (body.isEmpty()) "\r\n" else "Content-Type: $type\r\nContent-Length: ${body.length}\r\n\r\n$body"
    }

    private fun getFileContents(filePath: String): String =
        File("$defaultDir$filePath").useLines { it.joinToString("\n") }

    private fun extractFromRequestHeaders(header: String, reader: BufferedReader): String {
        return generateSequence { reader.readLine() }.firstOrNull { it.startsWith("$header:") }
            ?.removePrefix("$header:")?.trim().orEmpty()
    }

    private fun extractUrlPath(requestLine: String): String {
        val firstSlash = requestLine.indexOf('/')
        val httpIndex = requestLine.indexOf("HTTP")
        return if (firstSlash != -1 && httpIndex != -1) {
            requestLine.substring(firstSlash, httpIndex).trim()
        } else ""
    }

    private fun determineStatus(urlPath: String, httpMethod: HttpMethod): HttpStatus {
        val resource = getFirstResource(urlPath)
        return when {
            urlPath.isEmpty() -> HttpStatus.BAD_REQUEST
            KnownUrlPaths.isUrlPathKnown(resource) -> if (httpMethod == HttpMethod.POST) HttpStatus.CREATED else HttpStatus.OK
            else -> HttpStatus.NOT_FOUND
        }
    }

    private fun getFirstResource(urlPath: String): String {
        val withoutFirstSlash = urlPath.removePrefix("/")
        val nextSlashIndex = withoutFirstSlash.indexOf('/')
        return if (nextSlashIndex == -1) "/$withoutFirstSlash" else "/${withoutFirstSlash.substring(0, nextSlashIndex)}"
    }

    fun closeServer() {
        serverSocket.close()
    }
}

enum class HttpStatus(private val code: Int, private val statusText: String) {
    OK(200, "OK"), BAD_REQUEST(400, "Bad Request"), NOT_FOUND(404, "Not Found"), CREATED(201, "Created");

    override fun toString(): String = "$code $statusText"
}

enum class KnownUrlPaths(val urlPath: String) {
    ROOT("/"), ROOT_2(""), ECHO("/echo"), USER_AGENT("/user-agent"), FILES("/files"), STOP("/stop");

    companion object {
        fun isUrlPathKnown(path: String): Boolean = entries.any { it.urlPath == path }
    }
}
