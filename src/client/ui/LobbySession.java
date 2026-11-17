package client.ui;

import client.net.Connection;
import client.game.GameBoard;
import client.resource.Avatar;
import client.net.ReceiverThread;
import common.Constants;
import common.model.Direction;
import common.protocol.Message;
import common.protocol.MsgType;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;

// 로그인 때 연 소켓을 방 생성과 입장에도 사용한다.
final class LobbySession implements Runnable {
    interface Listener {
        void loggedIn(String nickname);
        void roomsReceived(List<RoomSummary> rooms);
        void roomReceived(RoomInfo room, int myId);
        void gameStarted(int roomId, GameBoard board, int myId);
        void playersReceived(int roomId, List<GameBoard.Player> players);
        void chatReceived(int roomId, String line);
        void requestRejected(String reason);
        void sessionEnded(String reason);
    }

    private final String nickname;
    private final Listener listener;
    private Connection connection;
    private int userId;
    // 한 번의 접속을 담당하는 통신 스레드에서 로그인 화면에 전달할 안내를 정한다.
    private String endNotice = "서버와의 연결이 종료되었습니다.";
    // 이동과 STOP이 뒤바뀌지 않도록 접속마다 송신 작업을 한 줄로 처리한다.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "client-sender");
        thread.setDaemon(true);
        return thread;
    });

    LobbySession(String nickname, Listener listener) {
        this.nickname = nickname;
        this.listener = listener;
    }

    @Override
    public void run() {
        try (Connection opened = new Connection(Constants.DEFAULT_HOST, Constants.PORT)) {
            opened.send(new Message(MsgType.LOGIN, Constants.LOBBY_ROOM_ID, 0, nickname));
            Message reply = opened.receive();
            if (reply == null || reply.type() != MsgType.LOGIN_OK) {
                throw new IOException(reply != null && reply.type() == MsgType.ERROR
                        ? reply.body() : "로그인 응답을 받지 못했습니다.");
            }

            userId = Integer.parseInt(reply.body());
            connection = opened;
            opened.send(new Message(MsgType.ROOM_LIST, Constants.LOBBY_ROOM_ID, userId, ""));
            SwingUtilities.invokeLater(() -> listener.loggedIn(nickname));

            // 이후의 모든 응답은 이 수신 루프 한 곳에서 읽는다.
            new ReceiverThread(opened, this::handleMessage, this::receiveFailed).run();
        } catch (IOException | IllegalArgumentException error) {
            endNotice = "접속을 계속할 수 없습니다. " + error.getMessage();
        }
        SwingUtilities.invokeLater(() -> {
            sender.shutdownNow();
            listener.sessionEnded(endNotice);
        });
    }

    private void receiveFailed(IOException error) {
        // 자세한 원인은 콘솔에 남기고, 화면에는 사용자가 할 수 있는 행동을 안내한다.
        System.err.println("수신 오류: " + error);
        endNotice = "서버 데이터를 받는 중 오류가 발생했습니다. 다시 접속해 주세요.";
    }

    void createRoom(String title) {
        sendRoomRequest(MsgType.ROOM_CREATE, Constants.LOBBY_ROOM_ID, title);
    }

    void joinRoom(int roomId) {
        sendRoomRequest(MsgType.ROOM_JOIN, Constants.LOBBY_ROOM_ID, Integer.toString(roomId));
    }

    void sendChat(int roomId, String text) {
        sendRoomRequest(MsgType.CHAT, roomId, text);
    }

    void setReady(int roomId, boolean ready) {
        sendRoomRequest(MsgType.READY, roomId, Boolean.toString(ready));
    }

    void selectCharacter(int roomId, Avatar avatar) {
        // 네트워크 담당이 공통 타입을 등록하면 같은 송신 경로를 그대로 사용한다.
        MsgType type;
        try {
            type = MsgType.valueOf("CHARACTER_SELECT");
        } catch (IllegalArgumentException missingType) {
            listener.requestRejected("현재 접속 버전은 캐릭터 선택을 지원하지 않습니다.");
            return;
        }
        sendRoomRequest(type, roomId, avatar.name());
    }

    void startGame(int roomId) {
        sendRoomRequest(MsgType.START, roomId, "");
    }

    void move(int roomId, Direction direction) {
        sendRoomRequest(MsgType.INPUT, roomId, direction.name());
    }

    private void sendRoomRequest(MsgType type, int roomId, String body) {
        sender.execute(() -> {
            try {
                connection.send(new Message(type, roomId, userId, body));
            } catch (IOException error) {
                // 통신을 이어갈 수 없으면 수신 루프도 깨워 종료 처리를 한 곳에 모은다.
                System.err.println("방 요청 전송 실패: " + error.getMessage());
                try {
                    connection.close();
                } catch (IOException closeError) {
                    System.err.println("연결 종료 실패: " + closeError.getMessage());
                }
            }
        });
    }

    private void handleMessage(Message message) {
        switch (message.type()) {
            case ROOM_LIST -> {
                List<RoomSummary> rooms = RoomSummary.parseList(message.body());
                SwingUtilities.invokeLater(() -> listener.roomsReceived(rooms));
            }
            case ROOM_INFO -> {
                RoomInfo room = RoomInfo.parse(message.roomId(), message.body());
                SwingUtilities.invokeLater(() -> listener.roomReceived(room, userId));
            }
            case GAME_INIT -> {
                GameBoard board = GameBoard.parse(message.body());
                SwingUtilities.invokeLater(() -> listener.gameStarted(message.roomId(), board, userId));
            }
            case STATE -> {
                List<GameBoard.Player> players = GameBoard.playersFromState(message.body());
                SwingUtilities.invokeLater(() -> listener.playersReceived(message.roomId(), players));
            }
            case CHAT -> {
                // 본문 첫 구분자 뒤는 채팅 원문이다. 메시지 속 | 문자도 보존한다.
                String[] parts = message.body().split("[|]", 2);
                if (parts.length == 2) {
                    String[] senderInfo = parts[0].split(":", 2);
                    String name = senderInfo.length == 2 ? senderInfo[1] : senderInfo[0];
                    String line = name + "  ·  " + parts[1];
                    SwingUtilities.invokeLater(() -> listener.chatReceived(message.roomId(), line));
                }
            }
            case ERROR -> SwingUtilities.invokeLater(() -> listener.requestRejected(message.body()));
            default -> { } // 게임·채팅 메시지는 이후 해당 화면에서 처리한다.
        }
    }
}
