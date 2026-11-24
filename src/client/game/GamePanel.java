package client.game;

import common.Constants;
import client.resource.Avatar;
import common.model.Direction;
import common.model.PlayerState;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.Map;
import java.awt.RenderingHints;
import javax.swing.JPanel;

public final class GamePanel extends JPanel {
    private static final int TILE = Constants.TILE_SIZE;
    private static final int WIDTH = Constants.MAP_COLUMNS * TILE;
    private static final int HEIGHT = Constants.MAP_ROWS * TILE;
    private static final java.awt.image.BufferedImage BALLOON = client.resource.GameImages.load("water-balloon.png");
    private static final java.awt.image.BufferedImage BUBBLE = client.resource.GameImages.load("trapped-bubble.png");
    private List<GameBoard.Balloon> balloons = List.of();
    private final List<WaterBlast> blasts = new ArrayList<>();
    private final Map<Integer, PlayerPose> poses = new java.util.HashMap<>();
    // 32ms마다 다시 그려 걸음·물막·폭발의 중간 모습을 표시한다. 위치 갱신은 STATE가 담당하며 숨겨진 패널의 타이머는 멈춘다.
    private final javax.swing.Timer animation = new javax.swing.Timer(32, e -> {
        long now = System.nanoTime();
        blasts.removeIf(blast -> blast.expired(now));
        repaint();
    });
    private boolean spaceHeld;
    private MapTiles terrain;
    public record Appearance(String nickname, Avatar avatar) { }
    private static final Color NAME_BACKGROUND = new Color(20, 40, 50, 225);
    private static final Font NAME_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    private Map<Integer, Appearance> appearances = Map.of();
    private GameBoard board;
    private int myId;
    // 화살표의 끝이 (0, 0)에 오도록 그린다.
    private static final Polygon MY_ARROW = new Polygon(
            new int[] {-3, 3, 3, 8, 0, -8, -3},
            new int[] {-16, -16, -8, -8, 0, -8, -8}, 7);
    private static final Color ARROW_FILL = new Color(65, 189, 255);
    private static final Color ARROW_EDGE = new Color(16, 76, 134);

    private final List<Direction> held = new ArrayList<>();
    private final Consumer<Direction> onDirection;

    /** 방향키는 onDirection, 스페이스바는 onBomb에 연결한다. 실제 창과 프리뷰가 각자 입력을 처리할 함수를 넘겨 같은 게임판을 사용한다. */
    public GamePanel(Consumer<Direction> onDirection, Runnable onBomb) {
        this.onDirection = onDirection;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) animation.start();
                else animation.stop();
            }
        });
        for (Direction direction : List.of(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)) {
            bindKey(direction, true);
            bindKey(direction, false);
        }
        bindSpace(true, onBomb);
        bindSpace(false, onBomb);
        addFocusListener(new FocusAdapter() {
            /** 다른 창을 클릭하면 Swing이 호출한다. 이동 중이었다면 STOP을 전달해 키를 뗀 신호가 누락돼도 멈출 수 있게 한다. */
            @Override
            public void focusLost(FocusEvent event) {
                spaceHeld = false;
                if (!held.isEmpty()) {
                    held.clear();
                    onDirection.accept(Direction.STOP);
                }
            }
        });
    }

    /** 생성자가 방향별로 호출한다. 예를 들어 오른쪽 키를 누르는 동작과 떼는 동작을 서로 다른 키 입력으로 등록한다. */
    private void bindKey(Direction direction, boolean pressed) {
        String key = (pressed ? "pressed " : "released ") + direction.name();
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            /** 등록한 키 입력이 발생하면 Swing이 호출한다. 누른 키 목록을 고치고 이동 방향이 바뀐 경우에만 onDirection에 전달한다. */
            @Override
            public void actionPerformed(ActionEvent event) {
                Direction before = currentDirection();
                if (pressed && !held.contains(direction)) {
                    held.add(direction);
                } else if (!pressed) {
                    held.remove(direction);
                }
                // 가장 최근에 누른 방향을 사용하고, 같은 키의 자동 반복은 전송하지 않는다.
                Direction after = currentDirection();
                if (after != before) {
                    onDirection.accept(after);
                }
            }
        });
    }

    /** 길게 누를 때 생기는 키 반복을 걸러내고, 손을 뗐다 다시 누를 때만 새 설치 요청을 보낸다. */
    private void bindSpace(boolean pressed, Runnable onBomb) {
        String key = pressed ? "pressed SPACE" : "released SPACE";
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (pressed && !spaceHeld) onBomb.run();
                spaceHeld = pressed;
            }
        });
    }

    /** 아직 누르고 있는 키 중 마지막 방향을 반환한다. 모두 뗐으면 멈춤(STOP)을 반환한다. */
    private Direction currentDirection() {
        return held.isEmpty() ? Direction.STOP : held.get(held.size() - 1);
    }

    /** STATE를 받은 쪽에서 호출한다. 맵은 유지하고 참가자와 설치된 물풍선을 함께 교체한 뒤 Swing에 다시 그리기를 요청한다. */
    public void updateState(GameBoard.State state) {
        if (board != null) {
            long now = System.nanoTime();
            for (GameBoard.Player player : state.players()) {
                poses.computeIfAbsent(player.id(), id -> new PlayerPose(player)).update(player, now);
            }
            board = new GameBoard(board.tiles(), state.players());
            balloons = state.balloons();
            repaint();
        }
    }

    /** EXPLODE는 잠깐 그릴 물줄기를 등록하고, BOX_BREAK는 이후에도 바닥으로 표시되도록 게임판의 타일을 바꾼다. */
    public void applyMapEvent(String body) {
        if (board != null) {
            if (body.startsWith("EXPLODE:")) {
                WaterBlast blast = WaterBlast.parse(body);
                if (blast != null) {
                    blasts.add(blast);
                    repaint();

                }
                return;
            }
            GameBoard next = board.afterEvent(body);
            if (next != board) {
                board = next;
                repaint();
            }
        }
    }

    /** 게임이 시작될 때 초기 맵, 내 번호, 대기실의 닉네임·캐릭터 정보를 받아 첫 화면을 준비한다. */
    public void showBoard(GameBoard board, int myId, Map<Integer, Appearance> roster) {
        this.board = board;
        poses.clear();
        for (GameBoard.Player player : board.players()) poses.put(player.id(), new PlayerPose(player));
        balloons = List.of();
        blasts.clear();
        spaceHeld = false;
        // 상자가 부서져도 같은 테마를 유지하도록 초기 배치에서 한 번만 그림을 고른다.
        terrain = MapTiles.forMap(CoastMap.matching(board.tiles()));
        this.myId = myId;
        // 이동할 때도 대기실에서 받은 닉네임과 캐릭터를 그대로 사용한다.
        appearances = Map.copyOf(roster);
        repaint();
    }

    /** 창이 보이거나 repaint()를 요청하면 Swing이 호출한다. 맵, 캐릭터, 이름과 화살표 순으로 겹쳐 그린다. */
    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (board == null) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            double scale = Math.min((double) getWidth() / WIDTH, (double) getHeight() / HEIGHT);
            g.translate((getWidth() - WIDTH * scale) / 2, (getHeight() - HEIGHT * scale) / 2);
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // 맵 문자는 왼쪽 위부터 한 줄씩 들어온다.
            for (int i = 0; i < board.tiles().length(); i++) {
                int x = (i % Constants.MAP_COLUMNS) * TILE;
                int y = (i / Constants.MAP_COLUMNS) * TILE;
                char tile = board.tiles().charAt(i);
                g.drawImage(terrain.tile(tile, i % Constants.MAP_COLUMNS, i / Constants.MAP_COLUMNS),
                        x, y, TILE, TILE, null);
            }
            // STATE에 들어온 물풍선만 표시한다. 남은 틱으로 크기를 조금 바꿔 터지기 전 박동을 표현한다.
            for (GameBoard.Balloon balloon : balloons) {
                int inset = (balloon.ticksLeft() / 4 % 2) * 2;
                g.drawImage(BALLOON, balloon.col() * TILE + inset, balloon.row() * TILE + inset,
                        TILE - 2 * inset, TILE - 2 * inset, null);
            }
            long now = System.nanoTime();
            for (WaterBlast blast : blasts) blast.draw(g, TILE, now);
            // 플레이어 좌표는 이미 픽셀 단위이므로 타일 크기를 다시 곱하지 않는다.
            for (GameBoard.Player player : board.players()) {
                if (player.state() == PlayerState.DEAD) {
                    continue;
                }
                Appearance appearance = appearances.get(player.id());
                Avatar avatar = appearance == null ? Avatar.NONE : appearance.avatar();
                PlayerPose pose = poses.get(player.id());
                pose.draw(g, avatar, player, TILE, now, isEnabled());
                if (player.state() == PlayerState.TRAPPED) {
                    // 중심은 그대로 두고 가로와 세로를 반대로 늘려 물막만 일렁이게 한다.
                    int wave = (int) Math.round(2 * Math.sin((now % 850_000_000L) * 2 * Math.PI / 850_000_000L));
                    g.drawImage(BUBBLE, player.x() - 2 - wave, player.y() - 2 + wave,
                            TILE + 4 + 2 * wave, TILE + 4 - 2 * wave, null);
                }
            }
            // 모든 캐릭터를 먼저 그려서 이웃한 캐릭터가 닉네임을 덮지 않게 한다.
            g.setFont(NAME_FONT);
            FontMetrics metrics = g.getFontMetrics();
            for (GameBoard.Player player : board.players()) {
                if (player.state() == PlayerState.DEAD) continue;
                if (player.id() == myId) {
                    drawMyArrow(g, player.x() + TILE / 2, player.y() - 2);
                    continue;
                }
                Appearance appearance = appearances.get(player.id());
                if (appearance == null) continue;
                int width = metrics.stringWidth(appearance.nickname()) + 8;
                int height = metrics.getHeight() + 2;
                int x = Math.max(0, Math.min(WIDTH - width, player.x() + (TILE - width) / 2));
                int y = Math.max(0, player.y() - height - 2);
                g.setColor(NAME_BACKGROUND);
                g.fillRoundRect(x, y, width, height, 8, 8);
                g.setColor(appearance.avatar().nicknameColor);
                g.drawString(appearance.nickname(), x + 4, y + metrics.getAscent() + 1);
            }
        } finally {
            g.dispose();
        }
    }

    /** paintComponent가 내 캐릭터를 찾았을 때 호출한다. 전달받은 가로 중심과 높이를 기준으로 파란 화살표를 그린다. */
    private void drawMyArrow(Graphics2D graphics, int centerX, int tipY) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(centerX, Math.max(17, tipY));
            g.setColor(ARROW_FILL);
            g.fillPolygon(MY_ARROW);
            g.setColor(ARROW_EDGE);
            g.drawPolygon(MY_ARROW);
            g.setColor(Color.WHITE);
            g.drawLine(-1, -14, -1, -6);
        } finally {
            g.dispose();
        }
    }
}
