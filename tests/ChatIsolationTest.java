package tests;

import common.Constants;
import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import server.lobby.RoomManager;
import server.net.ClientHandler;
import server.net.SessionRegistry;

/** 실제 소켓으로 서버를 띄워 로비 채팅과 방 채팅이 서로 섞이지 않는지 확인합니다. */
public final class ChatIsolationTest {
    private static final long WAIT_MS = 2000;
    private static final long QUIET_MS = 300;

    private ChatIsolationTest() {
    }

    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            startServer(server);
            int port = server.getLocalPort();
            try (Client host = new Client("host", port);
                 Client guest = new Client("guest", port);
                 Client lobby = new Client("lobby", port)) {
                for (Client client : List.of(host, guest, lobby)) {
                    client.login();
                }
                host.send(MsgType.ROOM_CREATE, Constants.LOBBY_ROOM_ID, "chat-test");
                int roomId = host.await(MsgType.ROOM_INFO).roomId();
                guest.send(MsgType.ROOM_JOIN, Constants.LOBBY_ROOM_ID, Integer.toString(roomId));
                guest.await(MsgType.ROOM_INFO);
                drainAll(host, guest, lobby);

                host.send(MsgType.CHAT, roomId, "room-hello");
                List<Message> h = host.drain();
                List<Message> g = guest.drain();
                List<Message> l = lobby.drain();
                check(has(h, MsgType.CHAT, "room-hello") && has(g, MsgType.CHAT, "room-hello"),
                        "room chat reaches every room member");
                check(!hasChat(l, "room-hello"), "room chat does not reach the lobby");

                lobby.send(MsgType.LOBBY_CHAT, Constants.LOBBY_ROOM_ID, "lobby-hello");
                h = host.drain();
                g = guest.drain();
                l = lobby.drain();
                check(has(l, MsgType.LOBBY_CHAT, "lobby-hello"), "lobby chat reaches the lobby");
                check(!hasChat(h, "lobby-hello") && !hasChat(g, "lobby-hello"),
                        "lobby chat does not reach room members");

                host.send(MsgType.LOBBY_CHAT, Constants.LOBBY_ROOM_ID, "room-to-lobby");
                h = host.drain();
                l = lobby.drain();
                check(!hasChat(l, "room-to-lobby") && has(h, MsgType.ERROR, ""),
                        "room member cannot send LOBBY_CHAT");

                lobby.send(MsgType.CHAT, roomId, "lobby-to-room");
                h = host.drain();
                g = guest.drain();
                lobby.drain();
                check(!hasChat(h, "lobby-to-room") && !hasChat(g, "lobby-to-room"),
                        "forged roomId cannot inject chat into a room");

                guest.send(MsgType.ROOM_LEAVE, roomId, "");
                guest.drain();
                lobby.send(MsgType.LOBBY_CHAT, Constants.LOBBY_ROOM_ID, "after-leave");
                h = host.drain();
                g = guest.drain();
                check(has(g, MsgType.LOBBY_CHAT, "after-leave") && !hasChat(h, "after-leave"),
                        "player who left the room receives lobby chat again");

                host.send(MsgType.EMOJI, roomId, "1");
                h = host.drain();
                l = lobby.drain();
                check(has(h, MsgType.EMOJI, "") && l.stream().noneMatch(m -> m.type() == MsgType.EMOJI),
                        "room emoji stays in the room");
            }
        }
        System.out.println("Chat isolation tests passed: room, lobby, forged roomId, leave, emoji");
    }

    private static void startServer(ServerSocket server) {
        SessionRegistry registry = new SessionRegistry();
        RoomManager rooms = new RoomManager();
        AtomicInteger nextUserId = new AtomicInteger(1);
        Thread acceptor = new Thread(() -> {
            try {
                while (!server.isClosed()) {
                    Socket socket = server.accept();
                    int userId = nextUserId.getAndIncrement();
                    Thread handler = new Thread(new ClientHandler(socket, userId, registry, rooms),
                            "test-client-" + userId);
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (IOException closed) {
                // 테스트가 끝나 서버 소켓을 닫으면 accept()가 종료된다.
            }
        }, "test-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private static void drainAll(Client... clients) throws InterruptedException {
        for (Client client : clients) {
            client.drain();
        }
    }

    private static boolean has(List<Message> messages, MsgType type, String text) {
        return messages.stream().anyMatch(m -> m.type() == type && m.body().contains(text));
    }

    private static boolean hasChat(List<Message> messages, String text) {
        return has(messages, MsgType.CHAT, text) || has(messages, MsgType.LOBBY_CHAT, text);
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("FAIL " + name);
        }
        System.out.println("PASS " + name);
    }

    private static final class Client implements AutoCloseable {
        private final String nickname;
        private final Socket socket;
        private final DataOutputStream output;
        private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
        private int userId;

        Client(String nickname, int port) throws IOException {
            this.nickname = nickname;
            socket = new Socket("127.0.0.1", port);
            output = new DataOutputStream(socket.getOutputStream());
            DataInputStream input = new DataInputStream(socket.getInputStream());
            Thread reader = new Thread(() -> {
                try {
                    Message message;
                    while ((message = MessageCodec.read(input)) != null) {
                        inbox.add(message);
                    }
                } catch (IOException closed) {
                    // 테스트 종료 시 소켓을 닫으면 수신도 끝난다.
                }
            }, "test-reader-" + nickname);
            reader.setDaemon(true);
            reader.start();
        }

        void login() throws Exception {
            send(MsgType.LOGIN, Constants.LOBBY_ROOM_ID, nickname);
            userId = Integer.parseInt(await(MsgType.LOGIN_OK).body());
            send(MsgType.LOBBY_WATCH, Constants.LOBBY_ROOM_ID, "");
        }

        void send(MsgType type, int roomId, String body) throws IOException {
            MessageCodec.write(output, new Message(type, roomId, userId, body));
        }

        Message await(MsgType type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + WAIT_MS;
            while (System.currentTimeMillis() < deadline) {
                Message message = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (message != null && message.type() == type) {
                    return message;
                }
            }
            throw new AssertionError(nickname + " did not receive " + type);
        }

        List<Message> drain() throws InterruptedException {
            Thread.sleep(QUIET_MS);
            List<Message> messages = new ArrayList<>();
            inbox.drainTo(messages);
            return messages;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
