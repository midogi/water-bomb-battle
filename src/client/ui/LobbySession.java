package client.ui;

import client.net.Connection;
import client.game.GameBoard;
import client.resource.Avatar;
import client.net.ReceiverThread;
import common.Constants;
import common.model.ArenaMap;
import common.model.Direction;
import common.model.ItemType;
import common.protocol.ItemState;
import common.protocol.Message;
import common.protocol.MsgType;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;

// 로그인 때 연 소켓으로 방 요청과 경기 입력을 보내고, 수신 결과는 Swing 이벤트 스레드에서 MainFrame에 알린다.
final class LobbySession implements Runnable {
    interface Listener {
        /** LOGIN_OK를 받은 뒤 MainFrame에 승인된 닉네임을 알려 로비를 열게 한다. */
        void loggedIn(String nickname);
        /** 서버가 새로 보낸 로비 접속자와 방 목록을 한 화면에 함께 반영한다. */
        void lobbyUpdated(List<String> nicknames, List<RoomSummary> rooms);
        /** 명시적인 방 목록 조회나 퇴장에 대한 응답을 처리한다. */
        void roomsReceived(List<RoomSummary> rooms);
        /** ROOM_INFO에 담긴 참가자 목록과 내 번호를 보내 대기실이 내 카드와 방장을 구별하게 한다. */
        void roomReceived(RoomInfo room, int myId);
        /** GAME_INIT을 읽으면 초기 게임판과 내 번호를 MainFrame에 전달한다. */
        void gameStarted(int roomId, GameBoard board, int myId);
        /** 현재 방의 지형 변화를 게임 화면에 전달한다. */
        void mapEvent(int roomId, String body);
        /** 같은 틱에 받은 참가자와 물풍선 목록을 방 번호와 함께 전달한다. 현재 경기인지 확인하는 곳은 MainFrame이다. */
        void stateReceived(int roomId, GameBoard.State state);
        void itemStateReceived(int roomId, ItemState state);
        /** 승자 번호 또는 무승부 명단을 현재 경기 화면에 전달한다. */
        void gameFinished(int roomId, String body);
        /** 수신한 CHAT을 화면에 표시할 한 줄로 정리해 해당 방에 전달한다. */
        void chatReceived(int roomId, String line);
        /** EMOJI 본문에서 읽은 발신자와 그림 번호를 화면으로 전달한다. */
        void emojiReceived(int roomId, int senderId, int number, String lobbyNickname);
        /** 요청을 처리할 수 없는 이유를 화면에 전달해 안내창을 띄우고 다시 입력할 수 있게 한다. */
        void requestRejected(String reason);
        /** 서버가 실제 퇴장을 알린 참가자의 번호다. */
        void playerLeft(int roomId, int playerId);
        /** 접속 시도가 실패하거나 연결이 끝났을 때 로그인 화면으로 돌아갈 이유를 전달한다. */
        void sessionEnded(String reason);
    }

    private final String nickname;
    private final Listener listener;
    private Connection connection;
    private int userId;
    // 한 번의 접속을 담당하는 통신 스레드에서 로그인 화면에 전달할 안내를 정한다.
    private String endNotice = "Connection to the server closed.";
    // 이동과 STOP이 뒤바뀌지 않도록 접속마다 송신 작업을 한 줄로 처리한다.
    private final ExecutorService sender = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "client-sender");
        thread.setDaemon(true);
        return thread;
    });

    /** MainFrame이 로그인 요청마다 만든다. 접속에 사용할 닉네임과 결과를 받을 화면을 보관한다. */
    LobbySession(String nickname, Listener listener) {
        this.nickname = nickname;
        this.listener = listener;
    }

    /** MainFrame이 시작한 통신 스레드에서 로그인하고 첫 승인을 확인한다. 그 뒤에는 같은 소켓에서 서버 소식을 계속 받는다. */
    @Override
    public void run() {
        try (Connection opened = new Connection(Constants.DEFAULT_HOST, Constants.PORT)) {
            opened.send(new Message(MsgType.LOGIN, Constants.LOBBY_ROOM_ID, 0, nickname));
            // 첫 응답에서 사용자 번호를 받아야 이후 방 요청에 내 번호를 넣을 수 있다.
            Message reply = opened.receive();
            if (reply == null || reply.type() != MsgType.LOGIN_OK) {
                throw new IOException(reply != null && reply.type() == MsgType.ERROR
                        ? reply.body() : "No login response received.");
            }

            userId = Integer.parseInt(reply.body());
            connection = opened;
            opened.send(new Message(MsgType.LOBBY_WATCH, Constants.LOBBY_ROOM_ID, userId, ""));
            SwingUtilities.invokeLater(() -> listener.loggedIn(nickname));

            // 이미 통신 스레드에서 실행 중이므로 run()을 직접 호출한다. 이후 소켓 읽기는 이 루프가 맡는다.
            // 수신이 끝나면 try 블록을 빠져나가 소켓을 닫고, 아래에서 송신 작업과 화면 상태를 정리한다.
            new ReceiverThread(opened, this::handleMessage, this::receiveFailed).run();
        } catch (IOException | IllegalArgumentException error) {
            endNotice = error.toString();
        }
        SwingUtilities.invokeLater(() -> {
            sender.shutdownNow();
            listener.sessionEnded(endNotice);
        });
    }

    /** 수신 루프에서 읽기 오류가 났을 때 호출한다. 콘솔에 원인을 남기고 연결 종료 시 사용자에게 보여 줄 문구를 정한다. */
    private void receiveFailed(IOException error) {
        // 통신 예외의 종류와 원문을 화면에서도 확인할 수 있게 한다.
        System.err.println("수신 오류: " + error);
        endNotice = error.toString();
    }

    /** 로비에서 입력한 제목을 ROOM_CREATE 본문에 넣는다. 실제 대기실 전환은 방 정보가 도착한 뒤에 일어난다. */
    void createRoom(String title) {
        sendRoomRequest(MsgType.ROOM_CREATE, Constants.LOBBY_ROOM_ID, title);
    }

    /** 로비 목록에서 선택한 방 번호를 ROOM_JOIN 본문에 담아 서버에 입장을 요청한다. */
    void joinRoom(int roomId) {
        sendRoomRequest(MsgType.ROOM_JOIN, Constants.LOBBY_ROOM_ID, Integer.toString(roomId));
    }

    /** 대기실의 나가기 버튼에서 호출한다. 로그인 소켓을 유지한 채 현재 방 번호와 빈 본문으로 퇴장을 요청한다. */
    void leaveRoom(int roomId) {
        sendRoomRequest(MsgType.ROOM_LEAVE, roomId, "");
    }

    /** 대기실에서 전송을 누르면 방 번호와 채팅 원문을 CHAT 메시지로 보낸다. */
    void sendChat(int roomId, String text) {
        sendRoomRequest(roomId == Constants.LOBBY_ROOM_ID ? MsgType.LOBBY_CHAT : MsgType.CHAT, roomId, text);
    }

    /** 그림 파일은 각 클라이언트에 있으므로 방 번호와 선택한 이모티콘 번호만 보낸다. */
    void sendEmoji(int roomId, RoomEmoji emoji) {
        sendRoomRequest(roomId == Constants.LOBBY_ROOM_ID ? MsgType.LOBBY_EMOJI : MsgType.EMOJI, roomId, Integer.toString(emoji.number));
    }

    /** 준비 버튼이 요청한 다음 상태를 보낸다. true는 준비, false는 준비 취소다. */
    void setReady(int roomId, boolean ready) {
        sendRoomRequest(MsgType.READY, roomId, Boolean.toString(ready));
    }

    void selectCharacter(int roomId, Avatar avatar) {
        sendRoomRequest(MsgType.CHARACTER_SELECT, roomId, avatar.name());
    }

    void selectMap(int roomId, ArenaMap map) {
        sendRoomRequest(MsgType.MAP_SELECT, roomId, ArenaMap.selectionCode(map));
    }

    /** 방장의 시작 버튼에서 호출한다. START를 보낸 뒤 게임 화면은 GAME_INIT을 받을 때까지 기다린다. */
    void startGame(int roomId) {
        sendRoomRequest(MsgType.START, roomId, "");
    }

    /** 스페이스바 입력을 서버에 보낸다. 설치 좌표와 허용 여부는 서버가 사용자 상태에서 판단한다. */
    void placeBomb(int roomId) {
        sendRoomRequest(MsgType.BOMB, roomId, "");
    }

    void useItem(int roomId, ItemType item) {
        sendRoomRequest(MsgType.ITEM_USE, roomId, item.name());
    }

    /** 게임 화면에서 방향이 바뀌면 INPUT을 보낸다. 모든 방향키를 뗐을 때는 STOP도 같은 경로를 사용한다. */
    void move(int roomId, Direction direction) {
        sendRoomRequest(MsgType.INPUT, roomId, direction.name());
    }

    /** 방 요청을 하나의 송신 스레드에 맡겨 화면이 소켓 쓰기를 기다리지 않고, 입력 순서대로 메시지를 보내게 한다. */
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

    /** 수신 루프가 읽은 메시지를 종류별로 해석한다. Swing 화면 변경은 invokeLater로 넘겨 화면 전용 스레드가 처리한다. */
    private void handleMessage(Message message) {
        switch (message.type()) {
            case LOBBY_UPDATE -> {
                String[] lists = message.body().split("[|]", 2);
                if (lists.length == 2) {
                    List<String> names = lists[0].isEmpty() ? List.of() : List.of(lists[0].split(","));
                    var rooms = RoomSummary.parseList(lists[1]);
                    SwingUtilities.invokeLater(() -> listener.lobbyUpdated(names, rooms));
                }
            }
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
            case PLAYER_LEFT -> {
                try {
                    int id = Integer.parseInt(message.body());
                    SwingUtilities.invokeLater(() -> listener.playerLeft(message.roomId(), id));
                } catch (NumberFormatException invalid) {
                    System.err.println("Invalid PLAYER_LEFT id: " + message.body());
                }
            }
            case RESULT -> SwingUtilities.invokeLater(() -> listener.gameFinished(message.roomId(), message.body()));
            case EVENT -> SwingUtilities.invokeLater(() -> listener.mapEvent(message.roomId(), message.body()));
            case STATE -> {
                GameBoard.State state = GameBoard.parseState(message.body());
                SwingUtilities.invokeLater(() -> listener.stateReceived(message.roomId(), state));
            }
            case ITEM_STATE -> {
                if (message.userId() == userId) {
                    ItemState state = ItemState.parse(message.body());
                    SwingUtilities.invokeLater(() -> listener.itemStateReceived(message.roomId(), state));
                }
            }
            case EMOJI, LOBBY_EMOJI -> {
                String[] fields = message.body().split(":", -1);
                boolean lobby = message.type() == MsgType.LOBBY_EMOJI;
                if (fields.length == (lobby ? 3 : 2)) {
                    try {
                        int senderId = Integer.parseInt(fields[0]);
                        int number = Integer.parseInt(fields[1]);
                        SwingUtilities.invokeLater(() -> listener.emojiReceived(message.roomId(), senderId, number, lobby ? fields[2] : null));
                    } catch (NumberFormatException invalid) {
                        // 잘못된 이모티콘 한 건 때문에 정상 채팅 수신까지 중단하지 않는다.
                        System.err.println("이모티콘 메시지의 번호 형식이 잘못되었습니다.");
                    }
                }
            }
            case CHAT, LOBBY_CHAT -> {
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
            default -> { } // 이 화면에서 처리할 동작이 없는 메시지는 건너뛴다.
        }
    }
}
