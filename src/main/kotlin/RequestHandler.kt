import jdk.internal.net.http.common.Log.headers
import java.io.File
import java.io.FileNotFoundException
import java.net.Socket

class RequestHandler(private val httpRequestParser: HttpRequestParser) {
    fun handleClient(clientSocket: Socket, defaultDir: String): Boolean {
        clientSocket.use { socket ->
            println("Accepted new connection")

            try {
                val inputStream = socket.getInputStream()
                val typedRequest = httpRequestParser.parse(inputStream)
                println("the typed request: $typedRequest")
                val responseStatus = determineStatus(typedRequest.urlPath, typedRequest.method)
                println("response status: $responseStatus")

                val responseBytes = handleRequest(responseStatus, typedRequest, defaultDir)
                println("request handled:${String(responseBytes)}")

                socket.getOutputStream().use { out ->
                    out.write(responseBytes)
                    out.flush()
                }

                if (typedRequest.urlPath == KnownUrlPaths.STOP.urlPath) {
                    return false
                }
            } catch (_: Exception) {
                val badRequestResponse = "HTTP/1.1 ${HttpStatus.BAD_REQUEST}\r\n\r\n"
                socket.getOutputStream().use { out ->
                    out.write(badRequestResponse.toByteArray())
                    out.flush()
                }
            }
            return true
        }
    }

    private fun handleRequest(
        responseStatus: HttpStatus,
        request: HttpRequest,
        defaultDir: String,
    ): ByteArray = if (request.method == HttpMethod.GET) buildResponse(
        responseStatus, request.urlPath, request.headers, defaultDir
    )
    else handlePost(responseStatus, request.urlPath, request.payload ?: "", defaultDir)

    private fun handlePost(
        responseStatus: HttpStatus, urlPath: String, payload: String, defaultDir: String
    ): ByteArray = if (responseStatus == HttpStatus.CREATED) {
        createResource(urlPath, payload, defaultDir)
        "HTTP/1.1 $responseStatus\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()
    } else "".toByteArray()

    private fun createResource(urlPath: String, payload: String, defaultDir: String) {
        val firstResource = getFirstResource(urlPath)
        val resourceName = urlPath.removePrefix("$firstResource/")
        val file = File("$defaultDir$resourceName")
        file.createNewFile()
        file.writeText(payload)
        println("file written")
    }

    private fun buildResponse(
        httpStatus: HttpStatus, urlPath: String, headers: Map<String, String>, defaultDir: String
    ): ByteArray {
        return try {
            val responseBody = buildResponseBody(urlPath, headers, defaultDir)
            val isContentCompressionSupported = headers["Accept-Encoding"] == "gzip"
            val contentEncodingHeader = if (isContentCompressionSupported) "Content-Encoding: gzip\r\n" else ""
            val response = "HTTP/1.1 $httpStatus\r\n$contentEncodingHeader$responseBody"
            println("Final response: $response")
            println("End of final response")
            response.toByteArray()
        } catch (_: FileNotFoundException) {
            "HTTP/1.1 ${HttpStatus.NOT_FOUND}\r\n\r\n".toByteArray()
        }
    }

    private fun buildResponseBody(urlPath: String, headers: Map<String, String>, defaultDir: String): String {
        val (body, type) = when (val resource = getFirstResource(urlPath)) {
            KnownUrlPaths.ECHO.urlPath -> urlPath.removePrefix(resource).trimStart('/') to "text/plain"
            KnownUrlPaths.USER_AGENT.urlPath -> headers["User-Agent"] to "text/plain"
            KnownUrlPaths.FILES.urlPath -> getFileContents(
                urlPath.removePrefix("$resource/"), defaultDir
            ) to "application/octet-stream"

            else -> "" to ""
        }
        return if (body == null || body.isEmpty()) "\r\n" else "Content-Type: $type\r\nContent-Length: ${body.length}\r\n\r\n$body"
    }

    private fun getFileContents(filePath: String, defaultDir: String): String =
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
