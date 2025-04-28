import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket

fun sendRequestAndReadResponse(request: String): String {
    Socket("localhost", SERVER_PORT).use { socket ->
        val out: OutputStream = socket.getOutputStream()
        val input = BufferedReader(InputStreamReader(socket.getInputStream()))

        out.write(request.toByteArray())
        out.flush()

        return buildString {
            var line: String? = input.readLine()
            while (line != null) {
                appendLine(line)
                if (line.isEmpty()) break // Headers end
                line = input.readLine()
            }
            // Read body if present
            line = input.readLine()
            while (line != null) {
                appendLine(line)
                line = input.readLine()
            }
        }
    }
}