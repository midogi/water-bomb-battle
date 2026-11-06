package server;

import common.Constants;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import server.net.ClientHandler;

public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Constants.PORT;
        AtomicInteger nextUserId = new AtomicInteger(1);
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Connection-link server listening on port " + server.getLocalPort());
            while (true) {
                Socket socket = server.accept();
                socket.setTcpNoDelay(true);
                int userId = nextUserId.getAndIncrement();
                new Thread(new ClientHandler(socket, userId), "client-" + userId).start();
            }
        }
    }
}
