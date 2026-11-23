package client.ui;

import client.game.CoastMap;
import client.game.GameBoard;
import client.game.GamePanel;
import client.game.MatchScreen;
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
    private CoastMap selectedMap = CoastMap.TIDE;
    private GameLoop loop;
    private Timer timer;

    private final class View {
        final JFrame frame;
        final WaitingRoomPanel waiting;
        GamePanel game;
        MatchScreen match;

        /** 미리보기 참가자 한 명의 창을 만든다. 이 창에서 누른 버튼과 채팅에는 이 사람의 번호와 닉네임을 사용한다. */
        View(int id, String nickname) {
            frame = new JFrame(nickname + " · 대기실 미리보기");
            waiting = new WaitingRoomPanel((room, ready) -> setReady(id, ready),
                    room -> start(id), (room, avatar) -> select(id, avatar),
                    (room, text) -> views.values().forEach(v -> v.waiting.appendChat(nickname + "  ·  " + text)),
                    // 실제 서버의 같은 방 중계를 대신해 두 프리뷰 창의 기록에 한 번씩 추가한다.
                    (room, emoji) -> views.values().forEach(v -> v.waiting.appendEmoji(nickname, emoji)),
                    // 미리보기에는 로비가 없으므로 창을 닫는다. 아래 windowClosed에서 참가자를 정리한다.
                    room -> frame.dispose());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            waiting.setPreferredSize(new Dimension(600, 520));
            frame.setContentPane(waiting);
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.addWindowListener(new WindowAdapter() {
                /** 참가자의 창을 닫으면 Swing이 호출한다. 해당 번호를 leave()에 알려 남은 창의 상태를 정리한다. */
                @Override
                public void windowClosed(WindowEvent e) {
                    leave(id);
                }
            });
        }
    }

    /** 현재 방장과 참가자 목록을 한 시점의 방 정보로 묶는다. publish()와 선택·시작 조건 검사에서 사용한다. */
    private RoomInfo snapshot() {
        return new RoomInfo(ROOM_ID, hostId, List.copyOf(members.values()));
    }

    /** 참가자의 상태가 바뀌면 같은 방 정보를 모든 창의 showRoom()에 보내되 각 창에는 그 사람의 번호를 넘긴다. */
    private void publish() {
        RoomInfo room = snapshot();
        views.forEach((id, view) -> {
            view.waiting.showRoom(room, id, "해안 연습방");
            view.waiting.showPreviewMap(selectedMap, id == hostId ? this::selectMap : null);
        });
    }

    /** 방장이 고른 배치를 두 창에 다시 표시한다. 시작할 때도 같은 배치를 GameMap에 넘긴다. */
    private void selectMap(CoastMap map) {
        if (loop == null) {
            selectedMap = map;
            publish();
        }
    }

    /** 선택이나 준비 요청을 받아들일 수 없으면 요청한 창의 버튼 잠금을 풀고 거절 이유를 보여 준다. */
    private void reject(int id, String reason) {
        View view = views.get(id);
        view.waiting.requestFinished();
        JOptionPane.showMessageDialog(view.frame, reason);
    }

    /** 선택 버튼에서 받은 캐릭터를 다른 사람이 사용 중인지 확인한다. 선택할 수 있으면 참가자 정보를 바꾸고 두 창에 알린다. */
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

    /** 준비 또는 취소 버튼의 요청을 검사한 뒤 참가자의 준비 상태를 갱신하고 모든 창에 반영한다. */
    private void setReady(int id, boolean ready) {
        RoomInfo.Member me = members.get(id);
        if (loop != null || (ready && me.avatar() == Avatar.NONE)) {
            reject(id, "캐릭터를 먼저 선택해 주세요.");
            return;
        }
        members.put(id, new RoomInfo.Member(id, me.nickname(), ready, me.avatar()));
        publish();
    }

    /** 미리보기 시작 때 번호와 닉네임으로 참가자를 등록하고, 준비 전·캐릭터 미선택 상태로 대기실 창을 연다. */
    private void join(int id, String nickname) {
        members.put(id, new RoomInfo.Member(id, nickname, false, Avatar.NONE));
        View view = new View(id, nickname);
        views.put(id, view);
        publish();
        view.frame.setVisible(true);
    }

    /** 창이 닫히면 참가자를 제거한다. 대기 중에는 필요하면 방장을 바꾸고 마지막 창이 닫히면 게임 자원을 정리한다. */
    private void leave(int id) {
        views.remove(id);
        members.remove(id);
        if (!members.isEmpty() && id == hostId) hostId = members.keySet().iterator().next();
        if (views.isEmpty()) {
            if (timer != null) timer.stop();
            if (loop != null) loop.close();
        } else if (loop == null) {
            publish();
        } else {
            loop.playerLeft(id);
        }
    }

    /** 방장이 시작하면 한 게임 루프가 두 창의 입력을 처리한다. 매 틱의 상태·효과·최종 결과를 두 창에 전달해 같은 경기를 보여 준다. */
    private void start(int id) {
        RoomInfo room = snapshot();
        if (id != hostId || loop != null || !room.canStart()) {
            reject(id, "모두 캐릭터를 선택하고 준비한 뒤 방장이 시작할 수 있습니다.");
            return;
        }
        GameMap map = GameMap.fromRows(selectedMap.rows());
        GameState state = new GameState(ROOM_ID, map,
                List.of(new ServerPlayer(1, 40, 40), new ServerPlayer(2, 520, 440)), 4);
        loop = new GameLoop(state, message -> { });
        GameLoop round = loop;
        GameBoard board = GameBoard.parse(state.initialMessage().body());
        views.forEach((playerId, view) -> {
            view.game = new GamePanel(direction -> round.submit(playerId,
                    new Message(MsgType.INPUT, ROOM_ID, playerId, direction.name())),
                    () -> round.submit(playerId, new Message(MsgType.BOMB, ROOM_ID, playerId, "")));
            view.match = new MatchScreen(view.game, () -> returnToWaitingRoom(playerId));
            view.match.start(board, playerId, room.appearances());
            view.frame.setContentPane(view.match);
            view.frame.pack();
            view.game.requestFocusInWindow();
        });
        // 미리보기에서는 한 타이머가 게임을 진행하고 같은 위치 정보를 두 창에 전달한다.
        timer = new Timer(Constants.TICK_MILLIS, e -> {
            for (Message message : loop.advanceOneTick()) {
                if (message.type() == MsgType.STATE) {
                    var frameState = GameBoard.parseState(message.body());
                    views.values().forEach(view -> view.match.updateState(frameState));
                } else if (message.type() == MsgType.EVENT) {
                    views.values().forEach(view -> view.game.applyMapEvent(message.body()));
                } else if (message.type() == MsgType.RESULT) {
                    views.values().forEach(view -> view.match.finish(message.body()));
                    timer.stop();
                    loop.close();
                    loop = null;
                    // 다음 판은 다시 준비하되, 선택했던 캐릭터와 방장은 유지한다.
                    members.replaceAll((idKey, member) -> new RoomInfo.Member(idKey, member.nickname(), false, member.avatar()));
                    publish();
                }
            }
        });
        timer.start();
    }

    /** 창과 방 참가 정보는 그대로 두고 이 참가자만 대기실로 이동한다. 도중 복귀는 이번 경기에서만 기권한다. */
    private void returnToWaitingRoom(int id) {
        View view = views.get(id);
        if (loop != null) loop.playerLeft(id);
        view.waiting.requestFinished();
        view.frame.setContentPane(view.waiting);
        view.frame.pack();
        publish();
    }

    /** 서버 없이 대기실을 확인하는 시작점이다. 물방울과 파랑의 두 창을 열어 선택·준비 결과를 비교할 수 있게 한다. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            WaitingRoomPreview preview = new WaitingRoomPreview();
            preview.join(1, "물방울");
            preview.join(2, "파랑");
        });
    }
}
