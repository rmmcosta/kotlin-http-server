import java.io.InputStream

class HttpRequestParser {
    fun parse(inputStream: InputStream): HttpRequest {
        val requestBytes = readUntilHeadersEnd(inputStream)
        val requestText = String(requestBytes)
        val headerSections = requestText.split("\r\n\r\n", limit = 2)
        val headerLines = headerSections[0].split("\r\n")

        val requestLine = headerLines.first()

        val (methodStr, urlPath, _) = requestLine.split(" ")
        val method = HttpMethod.fromValue(methodStr) ?: throw Exception("Unsupported HTTP method")

        val headers = headerLines
            .drop(1)
            .filter { it.contains(":") }
            .associate {
                val (key, value) = it.split(":", limit = 2)
                key.trim() to value.trim()
            }

        val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) readBody(inputStream, contentLength) else null

        return HttpRequest(method, urlPath, headers, body)
    }

    private fun parseRequestLine(requestLine: String?): Triple<String, String, String?> {
        requireNotNull(requestLine) { "Missing request line" }

        val parts = requestLine.trim().split(" ")
        require(parts.size >= 2) { "Malformed request line: '$requestLine'" }

        val method = parts[0]
        val path = parts[1]
        val version = parts.getOrNull(2) // Optional

        return Triple(method, path, version)
    }


    private fun readUntilHeadersEnd(inputStream: InputStream): ByteArray {
        val buffer = mutableListOf<Byte>()
        var lastFour = byteArrayOf()

        while (true) {
            val next = inputStream.read()
            if (next == -1) break
            buffer.add(next.toByte())

            lastFour = (lastFour + next.toByte()).takeLast(4).toByteArray()
            if (lastFour.contentEquals("\r\n\r\n".toByteArray())) break
        }

        return buffer.toByteArray()
    }

    private fun readBody(inputStream: InputStream, length: Int): String {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = inputStream.read(buffer, read, length - read)
            if (r == -1) break
            read += r
        }
        return String(buffer, 0, read)
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
