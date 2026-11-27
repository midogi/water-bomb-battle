package client.game;

import common.Constants;
import common.model.Direction;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;

/** 날아가는 그림만 잠깐 재생한다. 명중 여부와 물폭탄의 폭발 시각은 서버가 정한다. */
record DartFlight(int fromX, int fromY, int targetX, int targetY, End end, long start, long impact) {
    enum End { BOMB, BLOCK, MISS }
    private static final int TILE = Constants.TILE_SIZE;
    private static final long FLASH = 65_000_000L;

    static DartFlight from(GameBoard.Player shooter, int col, int row, End end, Direction direction, long now) {
        int x = shooter.x() + TILE / 2;
        int y = shooter.y() + TILE / 2;
        int targetX = col * TILE + TILE / 2;
        int targetY = row * TILE + TILE / 2;
        // 서버가 발사 방향으로 검사한 타일의 중심선을 따라간다.
        if (direction == Direction.LEFT || direction == Direction.RIGHT) y = targetY;
        else x = targetX;
        // 장애물 한가운데로 들어가지 않고 날아온 쪽 표면에서 부딪힌다.
        if (end == End.BLOCK) {
            switch (direction) {
                case RIGHT -> targetX -= TILE / 2;
                case LEFT -> targetX += TILE / 2;
                case DOWN -> targetY -= TILE / 2;
                case UP -> targetY += TILE / 2;
                default -> { }
            }
        }
        double tiles = Math.hypot(targetX - x, targetY - y) / TILE;
        // 명중한 물폭탄은 이미 터졌으므로 발사 궤적과 명중 표시를 같은 프레임에 그린다.
        long duration = end == End.BOMB ? 0L : (long) ((70 + 20 * Math.min(5, tiles)) * 1_000_000);
        return new DartFlight(x, y, targetX, targetY, end, now, now + duration);
    }

    boolean expired(long now) {
        return now - impact >= FLASH;
    }

    void draw(Graphics2D graphics, long now) {
        if (expired(now)) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (now >= impact) {
                if (end == End.MISS) return;
                double phase = (double) (now - impact) / FLASH;
                int r = (int) (4 + 10 * phase);
                g.setColor(new Color(255, 247, 173, (int) (235 * (1 - phase))));
                g.setStroke(new BasicStroke(2));
                if (end == End.BLOCK) {
                    // 벽에서는 물줄기 대신 짧은 불꽃 세 줄만 흩어진다.
                    for (int i = 0; i < 3; i++) {
                        double angle = Math.atan2(fromY - targetY, fromX - targetX) + (i - 1) * 0.7;
                        double cx = Math.cos(angle), cy = Math.sin(angle);
                        g.drawLine(targetX + (int)(cx * r / 2), targetY + (int)(cy * r / 2),
                                targetX + (int)(cx * r), targetY + (int)(cy * r));
                    }
                } else {
                    g.drawLine(fromX, fromY, targetX, targetY);
                    g.drawOval(targetX - r, targetY - r, 2 * r, 2 * r);
                }
                return;
            }
            double t = (double) (now - start) / (impact - start);
            int dx = targetX - fromX;
            int dy = targetY - fromY;
            g.translate(fromX + dx * t, fromY + dy * t);
            g.rotate(Math.atan2(dy, dx));
            g.setColor(new Color(208, 255, 252, 195));
            g.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(-15, 0, -5, 0);
            // 원본 아이콘의 촉은 왼쪽 아래를 향하므로 이동 방향에 맞춰 돌린다.
            g.rotate(-3 * Math.PI / 4);
            g.drawImage(ItemArt.FIN.image, -13, -13, 26, 26, null);
        } finally {
            g.dispose();
        }
    }
}
