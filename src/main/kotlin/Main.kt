import java.net.ServerSocket;

fun main(args: Array<String>) {
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    println("Logs from your program will appear here!")
    val argsMsg =
        if (args.isEmpty()) "the args array is empty" else "the args passed to the main: ${args.joinToString(",")}"
    println(argsMsg)
    val serverPortKey = "server-port:"
    val port: Int =
        args.firstOrNull { it.contains(serverPortKey) }?.removePrefix(serverPortKey)?.toInt() ?: SERVER_PORT
    val directoryArgIndex = args.indexOf("--directory") + 1
    val defaultDir: String = args.getOrNull(directoryArgIndex) ?: System.getProperty("user.dir")
    println("default dir: $defaultDir")
    MyHttpServer(ServerSocket(port), defaultDir).initServer()
}
