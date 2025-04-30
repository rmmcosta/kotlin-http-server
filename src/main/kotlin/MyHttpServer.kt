import java.io.File
import java.io.FileNotFoundException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

const val SERVER_PORT = 4221

class MyHttpServer(
    private val serverSocket: ServerSocket,
    private val defaultDir: String = "",
    private val httpRequestParser: HttpRequestParser,
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
            val typedRequest = httpRequestParser.parse(inputStream)
            println("the typed request: $typedRequest")
            val responseBytes = try {

                val responseStatus = determineStatus(typedRequest.urlPath, typedRequest.method)
                println("response status: $responseStatus")

                handleRequest(responseStatus, typedRequest)
            } catch (_: Exception) {
                "HTTP/1.1 ${HttpStatus.BAD_REQUEST}\r\n\r\n".toByteArray()
            }

            println("request handled:${String(responseBytes)}")

            socket.getOutputStream().use { out ->
                out.write(responseBytes)
                out.flush()
            }

            if (typedRequest.urlPath == KnownUrlPaths.STOP.urlPath) {
                closeServer()
            }
        }
    }

    private fun handleRequest(
        responseStatus: HttpStatus,
        request: HttpRequest,
    ): ByteArray = if (request.method == HttpMethod.GET) buildResponse(responseStatus, request.urlPath, request.headers)
    else handlePost(responseStatus, request.urlPath, request.payload ?: "")

    private fun handlePost(responseStatus: HttpStatus, urlPath: String, payload: String): ByteArray =
        if (responseStatus == HttpStatus.CREATED) {
            createResource(urlPath, payload)
            "HTTP/1.1 $responseStatus\r\n\r\n".toByteArray()
        } else "".toByteArray()

    private fun createResource(urlPath: String, payload: String) {
        val firstResource = getFirstResource(urlPath)
        val resourceName = urlPath.removePrefix("$firstResource/")
        val file = File("$defaultDir$resourceName")
        file.createNewFile()
        file.writeText(payload)
        println("file written")
    }

    private fun buildResponse(httpStatus: HttpStatus, urlPath: String, headers: Map<String, String>): ByteArray {
        return try {
            val responseBody = buildResponseBody(urlPath, headers)
            val response = "HTTP/1.1 $httpStatus\r\n$responseBody"
            println("Final response: $response")
            println("End of final response")
            response.toByteArray()
        } catch (_: FileNotFoundException) {
            "HTTP/1.1 ${HttpStatus.NOT_FOUND}\r\n\r\n".toByteArray()
        }
    }

    private fun buildResponseBody(urlPath: String, headers: Map<String, String>): String {
        val (body, type) = when (val resource = getFirstResource(urlPath)) {
            KnownUrlPaths.ECHO.urlPath -> urlPath.removePrefix(resource).trimStart('/') to "text/plain"
            KnownUrlPaths.USER_AGENT.urlPath -> headers["User-Agent"] to "text/plain"
            KnownUrlPaths.FILES.urlPath -> getFileContents(urlPath.removePrefix("$resource/")) to "application/octet-stream"
            else -> "" to ""
        }
        return if (body == null || body.isEmpty()) "\r\n" else "Content-Type: $type\r\nContent-Length: ${body.length}\r\n\r\n$body"
    }

    private fun getFileContents(filePath: String): String =
        File("$defaultDir$filePath").useLines { it.joinToString("\n") }

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
