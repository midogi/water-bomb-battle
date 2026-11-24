package client.ui;

import java.awt.CardLayout;
import client.game.GameBoard;
import client.game.GamePanel;
import client.resource.Avatar;
import common.Constants;
import common.model.Direction;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class MainFrame extends JFrame implements LoginPanel.LoginListener,
        LobbyPanel.RoomListener, LobbySession.Listener {

    // CardLayout에서 각 화면을 구분하는 이름이다.
    private static final String LOGIN = "login";
    private static final String LOBBY = "lobby";
    private static final String WAITING_ROOM = "waiting-room";
    private static final String GAME = "game";

    private final CardLayout layout = new CardLayout();
    private final JPanel screens = new JPanel(layout);

    private final LoginPanel loginPanel = new LoginPanel(this);
    private final LobbyPanel lobbyPanel = new LobbyPanel(this);
    private final WaitingRoomPanel waitingRoom = new WaitingRoomPanel(this::readyRequested,
            this::startRequested, this::selectRequested, (room, text) -> sendChat(room, text));
    private RoomInfo currentRoom;
    private final GamePanel gamePanel = new GamePanel(this::moveRequested);
    private int gameRoomId;
    private LobbySession session;
    private boolean sessionActive;

    public MainFrame() {
        super("연결고리 | 물폭탄 대전");

        screens.add(loginPanel, LOGIN);
        screens.add(lobbyPanel, LOBBY); // [변경] 로비 화면을 카드로 등록한다.
        screens.add(waitingRoom, WAITING_ROOM);
        screens.add(gamePanel, GAME);

        screens.setPreferredSize(new Dimension(600, 520));
        setContentPane(screens);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        // 프로그램을 처음 열면 로그인 화면부터 보여 준다.
        layout.show(screens, LOGIN);
    }

    @Override
    public void loginRequested(String nickname) {
        if (sessionActive) {
            return;
        }
        sessionActive = true;
        //위 코드는 한 번 접속을 시작한 뒤 두 번째 접속 작업이 또 생기는 것을 막음

        loginPanel.setConnecting(true);

        // 소켓 응답을 기다려도 화면이 멈추지 않게 별도 스레드에서 실행한다.
        session = new LobbySession(nickname, this);
        Thread worker = new Thread(session, "lobby-session");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void sessionEnded(String reason) {
        currentRoom = null;
        waitingRoom.clearChat();
        gameRoomId = 0;
        waitingRoom.requestFinished();
        session = null;
        sessionActive = false;
        loginPanel.setConnecting(false);
        layout.show(screens, LOGIN);
        JOptionPane.showMessageDialog(this, reason);
    }

    @Override
    public void loggedIn(String nickname) {
        // [변경] 로비 안의 닉네임 표시는 LobbyPanel이 담당한다.
        lobbyPanel.setNickname(nickname);
        lobbyPanel.showLoading();
        lobbyPanel.setRequestPending(false);

        // MainFrame은 보여 줄 화면을 선택한다.
        layout.show(screens, LOBBY);
    }

    @Override
    public void roomsReceived(List<RoomSummary> rooms) {
        lobbyPanel.setRooms(rooms);
    }

    @Override
    public void createRequested(String title) {
        lobbyPanel.setRequestPending(true);
        session.createRoom(title);
    }

    @Override
    public void joinRequested(int roomId) {
        lobbyPanel.setRequestPending(true);
        session.joinRoom(roomId);
    }

    @Override
    public void requestRejected(String reason) {
        // 방 요청이 거절되어도 로그인 연결은 유지한다.
        lobbyPanel.setRequestPending(false);
        waitingRoom.requestFinished();
        JOptionPane.showMessageDialog(this, reason);
    }

    @Override
    public void roomReceived(RoomInfo room, int myId) {
        currentRoom = room;
        gameRoomId = 0;
        waitingRoom.showRoom(room, myId);
        layout.show(screens, WAITING_ROOM);
    }

    private void sendChat(int roomId, String text) {
        session.sendChat(roomId, text);
    }

    @Override
    public void chatReceived(int roomId, String line) {
        if (roomId == Constatns.LOBBY_ROOM_ID) {
            lobbyPanel.appendChat(line);
            return;
        }
        if(currentRoom != null && currentRoom.roomId() == roomId && gameRoomId == 0) {
            waitingRoom.appendChat(line);
        }
    }

    private void readyRequested(int roomId, boolean ready) {
        session.setReady(roomId, ready);
    }

    private void startRequested(int roomId) {
        session.startGame(roomId);
    }

    private void selectRequested(int roomId, Avatar avatar) {
        session.selectCharacter(roomId, avatar);
    }

    @Override
    public void gameStarted(int roomId, GameBoard board, int myId) {
        // 방장과 참가자 모두 서버의 GAME_INIT이 도착했을 때 게임 화면으로 이동한다.
        waitingRoom.requestFinished();
        gameRoomId = roomId;
        gamePanel.showBoard(board, myId, currentRoom != null && currentRoom.roomId() == roomId
                ? currentRoom.appearances() : java.util.Map.of());
        layout.show(screens, GAME);
        gamePanel.requestFocusInWindow();
    }

    private void moveRequested(Direction direction) {
        if (session != null && gameRoomId != 0) {
            session.move(gameRoomId, direction);
        }
    }

    @Override
    public void playersReceived(int roomId, List<GameBoard.Player> players) {
        if (gameRoomId != 0 && roomId == gameRoomId) {
            gamePanel.updatePlayers(players);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }

    @Override
    public void lobbyChatRequested(String text) {
        session.sendChat(Constants.LOBBY_ROOM_ID, text);
    }
}
