import java.net.ServerSocket;

fun main(args: Array<String>) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")
    val port = if (args.size != 2) SERVER_PORT else args[1].toInt()
    MyHttpServer(ServerSocket(port)).initServer()
}
