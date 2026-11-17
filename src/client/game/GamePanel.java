package client.game;

import common.Constants;
import client.resource.GameImages;
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
import java.awt.image.BufferedImage;
import java.util.Map;
import java.awt.RenderingHints;
import javax.swing.JPanel;

public final class GamePanel extends JPanel {
    private static final int TILE = Constants.TILE_SIZE;
    private static final int WIDTH = Constants.MAP_COLUMNS * TILE;
    private static final int HEIGHT = Constants.MAP_ROWS * TILE;
    private static final BufferedImage FLOOR = GameImages.load("floor.png");
    private static final BufferedImage WALL = GameImages.load("wall.png");
    private static final BufferedImage BOX = GameImages.load("crate.png");
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

    public GamePanel(Consumer<Direction> onDirection) {
        this.onDirection = onDirection;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        for (Direction direction : List.of(Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT)) {
            bindKey(direction, true);
            bindKey(direction, false);
        }
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent event) {
                if (!held.isEmpty()) {
                    held.clear();
                    onDirection.accept(Direction.STOP);
                }
            }
        });
    }

    private void bindKey(Direction direction, boolean pressed) {
        String key = (pressed ? "pressed " : "released ") + direction.name();
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() {
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

    private Direction currentDirection() {
        return held.isEmpty() ? Direction.STOP : held.get(held.size() - 1);
    }

    public void updatePlayers(List<GameBoard.Player> players) {
        if (board != null) {
            board = new GameBoard(board.tiles(), players);
            repaint();
        }
    }

    public void showBoard(GameBoard board, int myId, Map<Integer, Appearance> roster) {
        this.board = board;
        this.myId = myId;
        // 이동할 때도 대기실에서 받은 닉네임과 캐릭터를 그대로 사용한다.
        appearances = Map.copyOf(roster);
        repaint();
    }

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
                BufferedImage image = switch (tile) {
                    case '#' -> WALL;
                    case 'B' -> BOX;
                    default -> FLOOR;
                };
                g.drawImage(image, x, y, TILE, TILE, null);
            }
            // 플레이어 좌표는 이미 픽셀 단위이므로 타일 크기를 다시 곱하지 않는다.
            for (GameBoard.Player player : board.players()) {
                if (player.state() == PlayerState.DEAD) {
                    continue;
                }
                Appearance appearance = appearances.get(player.id());
                Avatar avatar = appearance == null ? Avatar.NONE : appearance.avatar();
                if (avatar.image != null) {
                    g.drawImage(avatar.image, player.x(), player.y(), TILE, TILE, null);
                } else {
                    g.setColor(Color.GRAY);
                    g.fillOval(player.x() + 4, player.y() + 4, TILE - 8, TILE - 8);
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
