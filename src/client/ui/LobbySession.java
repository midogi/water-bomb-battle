package client.ui;

import client.net.Connection;
import client.net.ReceiverThread;
import common.Constants;
import common.protocol.Message;
import common.protocol.MsgType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

// 한 번의 접속에서 로그인 결과와 방 목록을 받아 화면에 전달한다.
final class LobbySession implements Runnable {
    private final String nickname;
    private final Runnable onLogin;
    private final Consumer<List<String>> onRooms;
    private final Consumer<String> onEnd;

    LobbySession(String nickname, Runnable onLogin, Consumer<List<String>> onRooms,
            Consumer<String> onEnd) {
        this.nickname = nickname;
        this.onLogin = onLogin;
        this.onRooms = onRooms;
        this.onEnd = onEnd;
    }

    @Override
    public void run() {
        String reason = "서버와의 연결이 종료되었습니다.";
        try (Connection connection = new Connection(Constants.DEFAULT_HOST, Constants.PORT)) {
            connection.send(new Message(MsgType.LOGIN, Constants.LOBBY_ROOM_ID, 0, nickname));
            Message reply = connection.receive();
            if (reply == null || reply.type() != MsgType.LOGIN_OK) {
                throw new IOException(reply != null && reply.type() == MsgType.ERROR
                        ? reply.body() : "로그인 응답을 받지 못했습니다.");
            }

            // 서버가 승인한 뒤에만 로비를 열고, 배정된 ID로 방 목록을 요청한다.
            int userId = Integer.parseInt(reply.body());
            SwingUtilities.invokeLater(onLogin);
            connection.send(new Message(MsgType.ROOM_LIST, Constants.LOBBY_ROOM_ID, userId, ""));

            // 현재 스레드가 이미 통신 전용이므로 수신 루프를 여기서 실행한다.
            ReceiverThread receiver = new ReceiverThread(connection, this::handleMessage,
                    error -> System.err.println("수신 종료: " + error.getMessage()));
            receiver.run();
        } catch (IOException | IllegalArgumentException error) {
            reason = "접속을 계속할 수 없습니다. " + error.getMessage();
        }
        String notice = reason;
        SwingUtilities.invokeLater(() -> onEnd.accept(notice));
    }

    private void handleMessage(Message message) {
        if (message.type() == MsgType.ERROR) {
            throw new IllegalArgumentException(message.body());
        }
        if (message.type() != MsgType.ROOM_LIST) {
            return;
        }

        // 방 하나의 형식은 방ID:제목:인원:상태이며, 현재 로비에는 제목을 표시한다.
        List<String> names = new ArrayList<>();
        if (!message.body().isEmpty()) {
            for (String room : message.body().split(",")) {
                String[] fields = room.split(":", -1);
                if (fields.length != 4) {
                    throw new IllegalArgumentException("방 목록 형식을 확인해 주세요.");
                }
                names.add(fields[1]);
            }
        }
        SwingUtilities.invokeLater(() -> onRooms.accept(names));
    }
}
