import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket

const val SERVER_PORT = 4221

class MyHttpServer(private val serverSocket: ServerSocket) {
    private var clientBufferedReader: BufferedReader? = null

    fun initServer(endlessLoop: Boolean = true) {
        // // Since the tester restarts your program quite often, setting SO_REUSEADDR
        // // ensures that we don't run into 'Address already in use' errors
        serverSocket.reuseAddress = true
        var ranOnce = false

        while (!ranOnce || endlessLoop) {
            val clientSocket = serverSocket.accept() // Wait for connection from client.
            println("accepted new connection")
            val urlPath = extractUrlPath(clientSocket)
            println("urlPath: $urlPath")
            val responseStatus = handleUrlPath(urlPath)
            println("response status: $responseStatus")
            val out = clientSocket.getOutputStream()
            out.write(buildResponse(responseStatus, urlPath, clientSocket))
            out.flush()
            clientSocket.close()
            ranOnce = true
            if (urlPath == KnownUrlPaths.STOP.urlPath) {
                closeServer()
                return
            }
        }
    }

    fun closeServer() {
        serverSocket.close()
    }

    private fun buildResponse(httpStatus: HttpStatus, urlPath: String, clientSocket: Socket): ByteArray {
        val responseBody = buildResponseBody(urlPath)
        val response = "HTTP/1.1 $httpStatus\r\n$responseBody"
        println("final response:$response")
        println("end of final response")
        return response.toByteArray()
    }

    private fun buildResponseBody(urlPath: String): String {
        val body = when (val firstResource = getFirstResource(urlPath)) {
            KnownUrlPaths.ECHO.urlPath -> urlPath.replace(firstResource, " ").trim().substring(1)

            KnownUrlPaths.USER_AGENT.urlPath -> getUserAgentValue()
            else -> ""
        }
        return if (body.isEmpty()) "\r\n" else "Content-Type: text/plain\r\nContent-Length: ${body.length}\r\n\r\n$body"
    }

    private fun getUserAgentValue(): String {
        val userAgentHeader = "User-Agent:"
        while (true) {
            val line = clientBufferedReader?.readLine() ?: return ""
            if (line.startsWith(userAgentHeader)) return line.replace(userAgentHeader, " ").trim()
        }
    }

    private fun extractUrlPath(clientSocket: Socket): String {
        clientBufferedReader = clientSocket.getInputStream().bufferedReader()
        val request1stLine = clientBufferedReader?.readLine() ?: return ""
        val firstSlashIndex = request1stLine.indexOf("/")
        val httpIndex = request1stLine.indexOf("HTTP")
        return request1stLine.substring(firstSlashIndex, httpIndex).trim()
    }

    private fun handleUrlPath(urlPath: String): HttpStatus {
        val firstResource = getFirstResource(urlPath)
        return when {
            urlPath.isEmpty() -> HttpStatus.BAD_REQUEST
            KnownUrlPaths.isUrlPathKnown(firstResource) -> HttpStatus.OK
            else -> HttpStatus.NOT_FOUND
        }
    }

    private fun getFirstResource(urlPath: String): String {
        val secondResourceIndex = urlPath.replaceFirst('/', ' ').indexOf('/')
        return if (secondResourceIndex == -1) urlPath else urlPath.substring(0, secondResourceIndex)
    }
}

enum class HttpStatus(private val code: Int, private val status: String) {
    OK(200, "OK"), BAD_REQUEST(400, "Bad Request"), NOT_FOUND(404, "Not Found");

    override fun toString(): String = "$code $status"
}

enum class KnownUrlPaths(val urlPath: String) {
    ROOT("/"), ROOT_2(""), ECHO("/echo"), USER_AGENT("/user-agent"), STOP("/stop");

    companion object {
        fun isUrlPathKnown(urlPath: String): Boolean = entries.any { it.urlPath == urlPath }
    }
}
