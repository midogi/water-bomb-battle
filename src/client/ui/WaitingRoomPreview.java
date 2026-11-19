package client.ui;

import client.game.GameBoard;
import client.game.GamePanel;
import client.resource.Avatar;
import common.Constants;
import common.protocol.Message;
import common.protocol.MsgType;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import server.game.GameLoop;
import server.game.GameMap;
import server.game.GameState;
import server.game.ServerPlayer;

// 두 창이 한 방을 공유한다. 모든 예시 요청은 Swing 이벤트 스레드에서 순서대로 처리한다.
public final class WaitingRoomPreview {
    private static final int ROOM_ID = 3;
    private final Map<Integer, RoomInfo.Member> members = new LinkedHashMap<>();
    private final Map<Integer, View> views = new LinkedHashMap<>();
    private int hostId = 2;
    private GameLoop loop;
    private Timer timer;

    private final class View {
        final JFrame frame;
        final WaitingRoomPanel waiting;
        GamePanel game;

        View(int id, String nickname) {
            frame = new JFrame(nickname + " · 대기실 미리보기");
            waiting = new WaitingRoomPanel((room, ready) -> setReady(id, ready),
                    room -> start(id), (room, avatar) -> select(id, avatar),
                    (room, text) -> views.values().forEach(v -> v.waiting.appendChat(nickname + "  ·  " + text)));
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            waiting.setPreferredSize(new Dimension(600, 520));
            frame.setContentPane(waiting);
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    leave(id);
                }
            });
        }
    }

    private RoomInfo snapshot() {
        return new RoomInfo(ROOM_ID, hostId, List.copyOf(members.values()));
    }

    private void publish() {
        RoomInfo room = snapshot();
        views.forEach((id, view) -> view.waiting.showRoom(room, id));
    }

    private void reject(int id, String reason) {
        View view = views.get(id);
        view.waiting.requestFinished();
        JOptionPane.showMessageDialog(view.frame, reason);
    }

    private void select(int id, Avatar avatar) {
        RoomInfo.Member me = members.get(id);
        RoomInfo.Member owner = snapshot().owner(avatar);
        if (loop != null || (me.id() != hostId && me.ready())) {
            reject(id, "준비를 취소한 뒤 선택해 주세요.");
        } else if (owner != null && owner.id() != id) {
            reject(id, "다른 참가자가 먼저 선택했습니다.");
        } else {
            // 새로운 선택을 확인한 뒤에만 기존 선택을 교체한다. NONE은 누구나 선택 가능하다.
            members.put(id, new RoomInfo.Member(id, me.nickname(), false, avatar));
            publish();
        }
    }

    private void setReady(int id, boolean ready) {
        RoomInfo.Member me = members.get(id);
        if (loop != null || (ready && me.avatar() == Avatar.NONE)) {
            reject(id, "캐릭터를 먼저 선택해 주세요.");
            return;
        }
        members.put(id, new RoomInfo.Member(id, me.nickname(), ready, me.avatar()));
        publish();
    }

    private void join(int id, String nickname) {
        members.put(id, new RoomInfo.Member(id, nickname, false, Avatar.NONE));
        View view = new View(id, nickname);
        views.put(id, view);
        publish();
        view.frame.setVisible(true);
    }

    private void leave(int id) {
        views.remove(id);
        members.remove(id);
        if (views.isEmpty()) {
            if (timer != null) timer.stop();
            if (loop != null) loop.close();
        } else if (loop == null) {
            if (id == hostId) hostId = members.keySet().iterator().next();
            publish();
        } else {
            loop.playerLeft(id);
        }
    }

    private void start(int id) {
        RoomInfo room = snapshot();
        if (id != hostId || loop != null || !room.canStart()) {
            reject(id, "모두 캐릭터를 선택하고 준비한 뒤 방장이 시작할 수 있습니다.");
            return;
        }
        GameMap map = GameMap.fromRows(RoomMapPreview.rows());
        GameState state = new GameState(ROOM_ID, map,
                List.of(new ServerPlayer(1, 40, 40), new ServerPlayer(2, 520, 440)), 4);
        loop = new GameLoop(state, message -> { });
        GameBoard board = GameBoard.parse(state.initialMessage().body());
        views.forEach((playerId, view) -> {
            view.game = new GamePanel(direction -> loop.submit(playerId,
                    new Message(MsgType.INPUT, ROOM_ID, playerId, direction.name())));
            view.game.showBoard(board, playerId, room.appearances());
            view.frame.setContentPane(view.game);
            view.frame.pack();
            view.game.requestFocusInWindow();
        });
        timer = new Timer(Constants.TICK_MILLIS, e -> {
            for (Message message : loop.advanceOneTick()) {
                if (message.type() == MsgType.STATE) {
                    var players = GameBoard.playersFromState(message.body());
                    views.values().forEach(view -> view.game.updatePlayers(players));
                } else if (message.type() == MsgType.RESULT) {
                    timer.stop();
                    loop.close();
                }
            }
        });
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WaitingRoomPreview preview = new WaitingRoomPreview();
            preview.join(1, "물방울");
            preview.join(2, "파랑");
        });
    }
}
