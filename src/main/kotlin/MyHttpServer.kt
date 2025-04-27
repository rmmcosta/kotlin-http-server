import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

const val SERVER_PORT = 4221

class MyHttpServer(private val serverSocket: ServerSocket) {

    fun initServer(endlessLoop: Boolean = true) {
        serverSocket.reuseAddress = true
        var ranOnce = false

        while (!ranOnce || endlessLoop) {
            val clientSocket = serverSocket.accept()
            thread {
                handleClient(clientSocket)
            }
            ranOnce = true
        }
    }

    private fun handleClient(clientSocket: Socket) {
        clientSocket.use { socket ->
            println("Accepted new connection")

            val bufferedReader = socket.getInputStream().bufferedReader()
            val requestLine = bufferedReader.readLine().orEmpty()
            val urlPath = extractUrlPath(requestLine)

            println("urlPath: $urlPath")

            val responseStatus = determineStatus(urlPath)
            println("response status: $responseStatus")

            val responseBytes = buildResponse(responseStatus, urlPath, bufferedReader)
            socket.getOutputStream().use { out ->
                out.write(responseBytes)
                out.flush()
            }

            if (urlPath == KnownUrlPaths.STOP.urlPath) {
                closeServer()
            }
        }
    }

    private fun buildResponse(httpStatus: HttpStatus, urlPath: String, reader: BufferedReader): ByteArray {
        val responseBody = buildResponseBody(urlPath, reader)
        val response = "HTTP/1.1 $httpStatus\r\n$responseBody"
        println("Final response: $response")
        println("End of final response")
        return response.toByteArray()
    }

    private fun buildResponseBody(urlPath: String, reader: BufferedReader): String {
        val body = when (val resource = getFirstResource(urlPath)) {
            KnownUrlPaths.ECHO.urlPath -> urlPath.removePrefix(resource).trimStart('/')
            KnownUrlPaths.USER_AGENT.urlPath -> extractUserAgent(reader)
            else -> ""
        }
        return if (body.isEmpty()) "\r\n" else
            "Content-Type: text/plain\r\nContent-Length: ${body.length}\r\n\r\n$body"
    }

    private fun extractUserAgent(reader: BufferedReader): String {
        val userAgentPrefix = "User-Agent:"
        return generateSequence { reader.readLine() }
            .firstOrNull { it.startsWith(userAgentPrefix) }
            ?.removePrefix(userAgentPrefix)
            ?.trim()
            .orEmpty()
    }

    private fun extractUrlPath(requestLine: String): String {
        val firstSlash = requestLine.indexOf('/')
        val httpIndex = requestLine.indexOf("HTTP")
        return if (firstSlash != -1 && httpIndex != -1) {
            requestLine.substring(firstSlash, httpIndex).trim()
        } else ""
    }

    private fun determineStatus(urlPath: String): HttpStatus {
        val resource = getFirstResource(urlPath)
        return when {
            urlPath.isEmpty() -> HttpStatus.BAD_REQUEST
            KnownUrlPaths.isUrlPathKnown(resource) -> HttpStatus.OK
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
    OK(200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    NOT_FOUND(404, "Not Found");

    override fun toString(): String = "$code $statusText"
}

enum class KnownUrlPaths(val urlPath: String) {
    ROOT("/"),
    ROOT_2(""),
    ECHO("/echo"),
    USER_AGENT("/user-agent"),
    STOP("/stop");

    companion object {
        fun isUrlPathKnown(path: String): Boolean =
            entries.any { it.urlPath == path }
    }
}
