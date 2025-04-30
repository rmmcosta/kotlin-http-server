import java.io.BufferedReader
import java.io.InputStream

class HttpRequestParser {
    fun parse(request: InputStream): HttpRequest {
        val bufferedReader = request.bufferedReader()
        val requestLine = bufferedReader.readLine()
        val (method, urlPath) = parseRequestLine(requestLine)
        val typedMethod = HttpMethod.fromValue(method) ?: throw Exception("Not a supported HTTP Method")
        val headers = parseHeaders(bufferedReader)
        val contentLength = headers.getOrDefault("Content-Length", "").toIntOrNull()
        val body = contentLength?.let { parseBody(bufferedReader, it) }
        return HttpRequest(typedMethod, urlPath, headers, body)
    }

    private fun parseBody(bufferedReader: BufferedReader, contentLength: Int): String {
        var bodySize = 0
        val lines = mutableListOf<String>()
        while (bodySize < contentLength) {
            val nextLine = bufferedReader.readLine()
            lines.add(nextLine)
            bodySize += nextLine.length
        }
        return lines.joinToString("\n")
    }

    private fun parseHeaders(bufferedReader: BufferedReader): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var nextLine = bufferedReader.readLine()
        while (nextLine.isNotBlank()) {
            val parts = nextLine.split(":").filter { it.isNotBlank() }
            headers.put(parts[0].trim(), parts.subList(1, parts.size).joinToString(":").trim())
            nextLine = bufferedReader.readLine()
        }
        return headers
    }

    private fun parseRequestLine(requestLine: String): Pair<String, String> {
        val parts = requestLine.split(" ").filter { it.isNotBlank() }
        return parts[0].trim() to parts[1].trim()
    }
}

data class HttpRequest(
    val method: HttpMethod,
    val urlPath: String,
    val headers: Map<String, String>,
    val payload: String? = null,
)

enum class HttpMethod {
    GET, POST;

    companion object {
        fun fromValue(value: String): HttpMethod? = entries.find { it.name == value }
    }
}
