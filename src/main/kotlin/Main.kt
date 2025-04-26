import java.net.ServerSocket;

fun main() {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")
    MyHttpServer(ServerSocket(SERVER_PORT)).initServer()
}
