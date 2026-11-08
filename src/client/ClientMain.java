package client;

import client.net.Connection;
import client.net.ReceiverThread;
import common.Constants;
import common.protocol.Message;
import common.protocol.MsgType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 초기 접속 확인용 콘솔 클라이언트. 추후 MainFrame의 시작점으로 교체합니다. */
public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String host = args.length > 0 ? args[0] : Constants.DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Constants.PORT;
        String nickname = args.length > 2 ? args[2] : "player";
        try (Connection connection = new Connection(host, port)) {
            ReceiverThread receiver = new ReceiverThread(connection,
                    message -> System.out.println("< " + message.toWire()),
                    () -> System.out.println("Server closed the connection.");
                    error -> System.err.println("Connection closed: " + error.getMessage()));
            receiver.setDaemon(true);
            receiver.start();
            connection.send(new Message(MsgType.LOGIN, 0, 0, nickname));
            connection.send(new Message(MsgType.ROOM_LIST, 0, 0, ""));
            System.out.println("Connected. Press Enter to disconnect. Game UI is not implemented yet.");
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        }
    }
}
