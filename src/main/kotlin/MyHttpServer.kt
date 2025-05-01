import khttp.request
import java.io.File
import java.io.FileNotFoundException
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

const val SERVER_PORT = 4221

class MyHttpServer(
    private val serverSocket: ServerSocket,
    private val defaultDir: String = "",
    private val requestHandler: RequestHandler,
) {

    fun initServer(endlessLoop: Boolean = true): Thread? {
        println("working dir: $defaultDir")
        serverSocket.reuseAddress = true
        var ranOnce = false
        var clientThread: Thread? = null

        while (!ranOnce || endlessLoop) {
            try {
                val clientSocket = serverSocket.accept()
                clientThread = thread {
                    val handleResult = requestHandler.handleClient(clientSocket, defaultDir)
                    if (!handleResult) closeServer()
                }
                ranOnce = true
            } catch (e: java.net.SocketException) {
                println("Server socket closed, shutting down server loop.")
                break
            }
        }
        return clientThread
    }

    fun closeServer() {
        serverSocket.close()
    }
}
