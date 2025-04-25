import java.net.ServerSocket;
import java.net.Socket

const val SERVER_PORT = 4221

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")
    MyHttpServer(ServerSocket(SERVER_PORT)).initServer()
}

class MyHttpServer(private val serverSocket: ServerSocket) {
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
            out.write(buildResponse(responseStatus))
            out.flush()
            clientSocket.close()
            ranOnce = true
        }
    }

    fun closeServer() {
        serverSocket.close()
    }

    private fun buildResponse(httpStatus: HttpStatus): ByteArray = "HTTP/1.1 $httpStatus\r\n\r\n".toByteArray()

    private fun extractUrlPath(clientSocket: Socket): String {
        val request = clientSocket.getInputStream().bufferedReader()
        val request1stLine = request.readLine() ?: return ""
        val firstSlashIndex = request1stLine.indexOf("/")
        val httpIndex = request1stLine.indexOf("HTTP")
        return request1stLine.substring(firstSlashIndex, httpIndex).trim()
    }

    private fun handleUrlPath(urlPath: String): HttpStatus =
        when {
            urlPath.isEmpty() -> HttpStatus.BAD_REQUEST
            KnownUrlPaths.isUrlPathKnown(urlPath) -> HttpStatus.OK
            else -> HttpStatus.NOT_FOUND
        }
}

enum class HttpStatus(private val code: Int, private val status: String) {
    OK(200, "OK"), BAD_REQUEST(400, "Bad Request"), NOT_FOUND(404, "Not Found");

    override fun toString(): String = "$code $status"
}

enum class KnownUrlPaths(private val urlPath: String) {
    ROOT("/"), ROOT_2("");

    companion object {
        fun isUrlPathKnown(urlPath: String): Boolean = entries.any { it.urlPath == urlPath }
    }
}
