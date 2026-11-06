package server.net;

import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/** 접속 확인용 시작점. 방·게임 입력은 추후 세션과 방별 큐에 연결합니다. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final int userId;
    private String nickname;

    public ClientHandler(Socket socket, int userId) {
        this.socket = socket;
        this.userId = userId;
    }

    @Override
    public void run() {
        try (Socket connection = socket;
                DataInputStream input = new DataInputStream(connection.getInputStream());
                DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
            Message request;
            while ((request = MessageCodec.read(input)) != null) {
                handle(request, output);
            }
        } catch (IOException error) {
            System.err.println("Client " + userId + " disconnected: " + error.getMessage());
        }
    }

    private void handle(Message request, DataOutputStream output) throws IOException {
        // request.userId()/roomId()가 아닌 이 소켓에 배정된 ID와 실제 세션을 사용합니다.
        if (request.type() == MsgType.LOGIN) {
            if (nickname != null) {
                reply(output, MsgType.ERROR, "ALREADY_LOGGED_IN:이미 로그인했습니다");
                return;
            }
            String candidate = request.body().strip();
            if (candidate.isEmpty() || candidate.indexOf('|') >= 0
                    || candidate.indexOf(':') >= 0 || candidate.indexOf(',') >= 0) {
                reply(output, MsgType.ERROR, "INVALID_NICK:닉네임과 구분자를 확인하세요");
                return;
            }
            nickname = candidate;
            reply(output, MsgType.LOGIN_OK, Integer.toString(userId));
        } else if (nickname == null) {
            reply(output, MsgType.ERROR, "NOT_LOGGED_IN:먼저 로그인하세요");
        } else if (request.type() == MsgType.ROOM_LIST) {
            reply(output, MsgType.ROOM_LIST, "");
        } else {
            reply(output, MsgType.ERROR, "NOT_IMPLEMENTED:아직 구현하지 않은 요청입니다");
        }
    }

    private void reply(DataOutputStream output, MsgType type, String body) throws IOException {
        MessageCodec.write(output, new Message(type, 0, userId, body));
    }
}
