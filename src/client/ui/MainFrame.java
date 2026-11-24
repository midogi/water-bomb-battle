package client.ui;

import java.awt.CardLayout;
import client.game.CoastMap;
import client.game.GameBoard;
import client.game.GamePanel;
import client.game.MatchScreen;
import client.resource.Avatar;
import common.model.Direction;
import common.model.ItemType;
import common.protocol.ItemState;
import java.awt.Dimension;
import java.util.List;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

// 로그인, 로비, 대기실, 게임 패널을 같은 창에 두고 서버 응답에 맞춰 전환한다.
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
    private final LobbyPanel lobbyPanel = new LobbyPanel(this,
            text -> sendChat(common.Constants.LOBBY_ROOM_ID, text),
            emoji -> sendEmoji(common.Constants.LOBBY_ROOM_ID, emoji));
    // 패널은 버튼 입력을 알리고, 이 창은 현재 세션에 맞는 요청을 보낸다.
    private final WaitingRoomPanel waitingRoom = new WaitingRoomPanel(this::readyRequested,
            this::startRequested, this::selectRequested, this::sendChat, this::sendEmoji, this::leaveRequested);
    private RoomInfo currentRoom;
    // ROOM_INFO에는 제목이 없다. 방 생성 때 입력했거나 입장할 때 선택한 제목을 퇴장까지 기억한다.
    private String roomTitle;
    private final GamePanel gamePanel = new GamePanel(this::moveRequested, this::bombRequested);
    private int gameRoomId;
    private final MatchScreen match = new MatchScreen(gamePanel, this::returnToWaitingRoom, this::itemRequested);
    private LobbySession session;
    private boolean sessionActive;

    /** 프로그램을 시작할 때 네 화면을 한 창의 CardLayout에 등록하고 첫 화면으로 로그인을 보여 준다. */
    public MainFrame() {
        super("연결고리 | 물폭탄 대전");

        screens.add(loginPanel, LOGIN);
        screens.add(lobbyPanel, LOBBY);
        screens.add(waitingRoom, WAITING_ROOM);
        screens.add(match, GAME);

        screens.setPreferredSize(new Dimension(600, 520));
        setContentPane(screens);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        // 프로그램을 처음 열면 로그인 화면부터 보여 준다.
        layout.show(screens, LOGIN);
    }

    /** LoginPanel에서 유효한 닉네임을 받으면 중복 접속을 막고 LobbySession을 별도 스레드에서 시작한다. */
    @Override
    public void loginRequested(String nickname) {
        if (sessionActive) {
            return;
        }
        sessionActive = true;
        // 응답 대기 중에는 새 로그인 요청을 받지 않는다.

        loginPanel.setConnecting(true);

        // 소켓 응답을 기다려도 화면이 멈추지 않게 별도 스레드에서 실행한다.
        session = new LobbySession(nickname, this);
        Thread worker = new Thread(session, "lobby-session");
        worker.setDaemon(true);
        worker.start();
    }

    /** LobbySession이 연결 종료를 알리면 이전 방과 게임 정보를 정리하고 로그인 화면에서 다시 입력할 수 있게 한다. */
    @Override
    public void sessionEnded(String reason) {
        currentRoom = null;
        roomTitle = null;
        waitingRoom.resetRoom();
        if (gameRoomId != 0) restoreLobbySize();
        gameRoomId = 0;
        session = null;
        sessionActive = false;
        loginPanel.setConnecting(false);
        layout.show(screens, LOGIN);
        JOptionPane.showMessageDialog(this, reason);
    }

    /** 로그인 승인을 받으면 닉네임을 표시하고 로비를 연다. 방 목록은 별도 응답이 올 때까지 불러오는 중으로 표시한다. */
    @Override
    public void loggedIn(String nickname) {
        // 승인된 닉네임을 로비에 표시한다.
        lobbyPanel.setNickname(nickname);
        lobbyPanel.showLoading();
        lobbyPanel.setRequestPending(false);

        // MainFrame은 보여 줄 화면을 선택한다.
        layout.show(screens, LOBBY);
    }

    /** 다른 사용자의 활동으로 받은 목록은 표시만 갱신한다. 퇴장 응답과 화면 전환은 별도로 처리한다. */
    @Override
    public void lobbyUpdated(List<String> nicknames, List<RoomSummary> rooms) {
        lobbyPanel.setOnlineUsers(nicknames);
        lobbyPanel.setRooms(rooms);
    }

    /** LobbySession이 방 목록을 해석하면 호출한다. 목록을 보여 주는 작업은 LobbyPanel에 맡긴다. */
    @Override
    public void roomsReceived(List<RoomSummary> rooms) {
        lobbyPanel.setRooms(rooms);
        // 현재 서버는 목록 조회 또는 퇴장 처리 직후에 요청자에게 ROOM_LIST를 보낸다.
        // 방 나가기를 요청한 상태에서 목록이 도착하면 퇴장이 끝난 것으로 보고, 로그인 연결을 유지한 채 로비로 돌아간다.
        if (waitingRoom.isLeavePending()) {
            currentRoom = null;
            roomTitle = null;
            if (gameRoomId != 0) restoreLobbySize();
            gameRoomId = 0;
            waitingRoom.resetRoom();
            lobbyPanel.setRequestPending(false);
            layout.show(screens, LOBBY);
        }
    }

    /** 경기에서 빠져나오면 참가자 명단을 위해 넓혔던 창을 원래 로비 크기로 돌린다. */
    private void restoreLobbySize() {
        screens.setPreferredSize(new Dimension(600, 520));
        pack();
    }

    /** 로비의 방 만들기 버튼에서 제목을 받아 버튼을 잠그고 LobbySession.createRoom()으로 넘긴다. */
    @Override
    public void createRequested(String title) {
        lobbyPanel.setRequestPending(true);
        roomTitle = title;
        session.createRoom(title);
    }

    /** 선택한 방 제목은 대기실 표시에 쓰고, 서버에는 같은 제목의 방도 구별할 수 있는 번호를 보낸다. */
    @Override
    public void joinRequested(RoomSummary room) {
        lobbyPanel.setRequestPending(true);
        roomTitle = room.title();
        session.joinRoom(room.id());
    }

    /** 요청이 거절되면 로비와 대기실의 요청 잠금을 해제하고 이유를 보여 준다. 사용자는 같은 연결에서 다시 시도할 수 있다. */
    @Override
    public void requestRejected(String reason) {
        // 결과보다 늦게 도착한 마지막 경기 입력의 응답은 이미 결과 화면으로 안내한 상태다.
        if (match.isFinished() && reason.startsWith("NOT_PLAYING:")) return;
        // 방 요청이 거절되어도 로그인 연결은 유지한다.
        lobbyPanel.setRequestPending(false);
        waitingRoom.requestFinished();
        JOptionPane.showMessageDialog(this, reason);
    }

    /** ROOM_INFO로 대기실 명단을 갱신한다. 경기 중이면 뒤에 있는 대기실만 갱신해서 결과 화면을 계속 볼 수 있게 한다. */
    @Override
    public void roomReceived(RoomInfo room, int myId) {
        // ROOM_CREATE와 ROOM_JOIN 모두 성공하면 ROOM_INFO가 온다. 두 요청의 화면 전환을 여기서 함께 처리한다.
        // 다른 사람이 경기 중 방을 나가도 남은 사람의 게임·결과 화면을 닫지 않는다.
        if (gameRoomId != 0 && gameRoomId != room.roomId()) return;
        currentRoom = room;
        waitingRoom.showRoom(room, myId, roomTitle);
        waitingRoom.showMap(room.map() == null ? null : CoastMap.valueOf(room.map().name()), myId == room.hostId()
                && room.member(myId) != null ? map -> session.selectMap(room.roomId(), map == null ? null : map.layout()) : null);
        if (gameRoomId != 0) return;
        layout.show(screens, WAITING_ROOM);
    }

    /** 나가기 버튼의 방 번호를 세션에 전달한다. 로비 전환은 퇴장 응답을 받는 roomsReceived()가 맡는다. */
    private void leaveRequested(int roomId) {
        session.leaveRoom(roomId);
    }

    /** 대기실 채팅 입력을 LobbySession에 넘긴다. 화면에 대화를 추가하는 일은 수신 경로인 chatReceived()가 맡는다. */
    private void sendChat(int roomId, String text) {
        session.sendChat(roomId, text);
    }

    /** 선택 즉시 화면에 붙이지 않고, 서버가 같은 방에 중계한 응답을 기다린다. */
    private void sendEmoji(int roomId, RoomEmoji emoji) {
        session.sendEmoji(roomId, emoji);
    }

    /** EMOJI에는 닉네임이 없어 발신자 번호로 현재 방의 명단을 조회한다. 모르는 그림 번호도 텍스트로 남긴다. */
    @Override
    public void emojiReceived(int roomId, int senderId, int number, String lobbyNickname) {
        if (roomId == common.Constants.LOBBY_ROOM_ID && currentRoom == null) {
            RoomEmoji emoji = RoomEmoji.find(number);
            if (emoji == null) lobbyPanel.chat.appendChat(lobbyNickname + " · [이모티콘 " + number + "]");
            else lobbyPanel.chat.appendEmoji(lobbyNickname, emoji);
            return;
        }
        if (currentRoom == null || currentRoom.roomId() != roomId || gameRoomId != 0) return;
        RoomInfo.Member sender = currentRoom.member(senderId);
        String name = sender == null ? "참가자 " + senderId : sender.nickname();
        RoomEmoji emoji = RoomEmoji.find(number);
        if (emoji == null) {
            waitingRoom.appendChat(name + "  ·  [이모티콘 " + number + "]");
        } else {
            waitingRoom.appendEmoji(name, emoji);
        }
    }

    /** 수신한 대화의 방 번호를 확인해 현재 대기실에 해당하는 메시지만 채팅 기록에 붙인다. */
    @Override
    public void chatReceived(int roomId, String line) {
        if (roomId == common.Constants.LOBBY_ROOM_ID && currentRoom == null) lobbyPanel.chat.appendChat(line);
        if (currentRoom != null && currentRoom.roomId() == roomId && gameRoomId == 0) {
            waitingRoom.appendChat(line);
        }
    }

    /** 대기실이 알려 준 방 번호와 준비 여부를 세션에 넘겨 READY 요청을 보낸다. */
    private void readyRequested(int roomId, boolean ready) {
        session.setReady(roomId, ready);
    }

    /** 방장이 누른 시작 버튼의 방 번호를 세션에 전달한다. 화면 전환은 gameStarted()가 맡는다. */
    private void startRequested(int roomId) {
        session.startGame(roomId);
    }

    /** 대기실에서 고른 캐릭터를 세션에 넘긴다. 현재 선택 표시는 roomReceived()가 받은 방 정보로 갱신한다. */
    private void selectRequested(int roomId, Avatar avatar) {
        session.selectCharacter(roomId, avatar);
    }

    /** 게임 시작 응답을 받으면 같은 방의 닉네임과 캐릭터를 게임판에 넘기고 방향키를 받을 수 있게 한다. */
    @Override
    public void gameStarted(int roomId, GameBoard board, int myId) {
        // 방장과 참가자 모두 서버의 GAME_INIT이 도착했을 때 게임 화면으로 이동한다.
        waitingRoom.requestFinished();
        gameRoomId = roomId;
        match.start(board, myId, currentRoom != null && currentRoom.roomId() == roomId
                ? currentRoom.appearances() : java.util.Map.of());
        match.allowReturnAfterResult();
        screens.setPreferredSize(match.getPreferredSize());
        pack();
        layout.show(screens, GAME);
        gamePanel.requestFocusInWindow();
    }

    /** 경기를 닫아도 방 소속은 유지한다. ROOM_LEAVE는 대기실에서 실제로 방을 나갈 때만 보낸다. */
    private void returnToWaitingRoom() {
        gameRoomId = 0;
        restoreLobbySize();
        waitingRoom.requestFinished();
        layout.show(screens, WAITING_ROOM);
    }

    /** GamePanel에서 현재 방향이 달라지면 호출한다. 게임 중이고 세션이 있을 때만 이동 요청을 보낸다. */
    private void moveRequested(Direction direction) {
        if (canSendGameInput()) {
            session.move(gameRoomId, direction);
        }
    }

    /** 현재 플레이 중인 방으로만 물풍선 설치 요청을 보낸다. */
    private void bombRequested() {
        if (canSendGameInput()) session.placeBomb(gameRoomId);
    }

    private void itemRequested(ItemType item) {
        if (canSendGameInput()) session.useItem(gameRoomId, item);
    }

    private boolean canSendGameInput() {
        return session != null && gameRoomId != 0 && !match.isFinished();
    }

    @Override
    public void itemStateReceived(int roomId, ItemState state) {
        if (gameRoomId == 0 || roomId != gameRoomId) return;
        var ground = state.ground().stream()
                .map(item -> new GamePanel.ItemMarker(item.col(), item.row(), item.type())).toList();
        gamePanel.updateItems(ground, Set.of());
        match.updateInventory(state.inventory(), state.invisible(), state.lensActive());
    }

    /** 현재 경기의 이벤트만 게임판에 넘겨 이전 방의 상자가 잘못 사라지지 않게 한다. */
    @Override
    public void mapEvent(int roomId, String body) {
        if (gameRoomId != 0 && roomId == gameRoomId) gamePanel.applyMapEvent(body);
    }

    @Override
    public void playerLeft(int roomId, int playerId) {
        if (gameRoomId != 0 && roomId == gameRoomId) match.playerLeft(playerId);
    }

    /** 현재 경기의 STATE만 MatchScreen에 넘긴다. 같은 응답에서 참가자 카드 상태와 게임판의 좌표·물풍선을 함께 갱신한다. */
    @Override
    public void stateReceived(int roomId, GameBoard.State state) {
        // 게임 시작 전에 오거나 다른 방을 가리키는 좌표는 현재 게임판에 적용하지 않는다.
        if (gameRoomId != 0 && roomId == gameRoomId) {
            match.updateState(state);
        }
    }

    /** 현재 방의 최종 결과가 도착하면 경기판 위에 표시하고 나가기 입력을 기다린다. */
    @Override
    public void gameFinished(int roomId, String body) {
        if (gameRoomId != 0 && gameRoomId == roomId) match.finish(body);
    }

    /** 실제 클라이언트의 시작점이다. Swing 이벤트 스레드에서 창을 만들고 로그인 화면을 띄운다. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            javax.swing.JComponent.setDefaultLocale(java.util.Locale.KOREAN);
            javax.swing.UIManager.put("OptionPane.okButtonText", "확인");
            javax.swing.UIManager.put("OptionPane.cancelButtonText", "취소");
            new MainFrame().setVisible(true);
        });
    }
}
