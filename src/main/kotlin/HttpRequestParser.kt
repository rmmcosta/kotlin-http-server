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

        val headers = headerLines.drop(1).filter { it.contains(":") }.associate {
            val (key, value) = it.split(":", limit = 2)
            key.trim() to value.trim()
        }

        val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
        val body = if (contentLength > 0) readBody(inputStream, contentLength) else null

        return HttpRequest(method, urlPath, headers, body)
    }

    private fun readUntilHeadersEnd(inputStream: InputStream, maxHeaderSize: Int = 8192): ByteArray {
        val buffer = mutableListOf<Byte>()
        return try {
            var lastFour = byteArrayOf()

            while (true) {
                val next = inputStream.read()
                if (next == -1) break
                buffer.add(next.toByte())

                if (buffer.size > maxHeaderSize) throw Exception("Request malformed or headers too large")

                lastFour = (lastFour + next.toByte()).takeLast(4).toByteArray()
                if (validEndOfRequest(lastFour)) break
            }

            if (!validEndOfRequest(lastFour)) throw Exception("Malformed request - Headers not properly ended")

            buffer.toByteArray()
        } catch (exception: Exception) {
            if (buffer.isEmpty()) throw RequestTimeoutException("No data received") else throw BadRequestException(
                exception.message ?: ""
            )
        }
    }

    private fun validEndOfRequest(lastFour: ByteArray): Boolean = lastFour.contentEquals("\r\n\r\n".toByteArray())

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
