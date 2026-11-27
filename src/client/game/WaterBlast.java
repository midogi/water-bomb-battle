package client.game;

import client.resource.GameImages;
import common.Constants;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** EXPLODE 한 건의 표시 범위. 방향별 길이는 게임 루프에서 이미 벽과 상자를 고려한 결과다. */
record WaterBlast(int col, int row, int up, int down, int left, int right, long started) {
    private static final long DURATION = 360_000_000L;
    private static final long FLASH_DURATION = Constants.TICK_MILLIS * 1_000_000L;
    private static final BufferedImage SPLASH = GameImages.load("water-splash.png");

    /** 잘못된 효과 메시지는 건너뛰고 정상적인 방 상태와 화면은 계속 유지한다. */
    static WaterBlast parse(String body, long now) {
        String[] fields = body.split(":", -1);
        if (fields.length != 7 || !fields[0].equals("EXPLODE")) return null;
        try {
            int x = Integer.parseInt(fields[1]);
            int y = Integer.parseInt(fields[2]);
            int u = Integer.parseInt(fields[3]);
            int d = Integer.parseInt(fields[4]);
            int l = Integer.parseInt(fields[5]);
            int r = Integer.parseInt(fields[6]);
            if (x < 0 || x >= Constants.MAP_COLUMNS || y < 0 || y >= Constants.MAP_ROWS
                    || u < 0 || u > y || d < 0 || d >= Constants.MAP_ROWS - y
                    || l < 0 || l > x || r < 0 || r >= Constants.MAP_COLUMNS - x) return null;
            return new WaterBlast(x, y, u, d, l, r, now);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** GamePanel의 애니메이션 타이머가 호출한다. 효과를 등록한 뒤 360ms가 지나면 화면에서 제거한다. */
    boolean expired(long now) {
        return now - started >= DURATION;
    }

    /** 물줄기 끝을 마지막으로 맞은 칸의 가운데에 두면 둥근 끝부분도 벽 너머로 넘어가지 않는다. */
    void draw(Graphics2D graphics, int tile, long now) {
        if (now < started || expired(now)) return;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            int leftX = col * tile;
            int topY = row * tile;
            int x = leftX + tile / 2;
            int y = topY + tile / 2;
            long elapsed = now - started;
            // 맞은 칸은 첫 순간에 전부 표시하고, 이후에는 옅은 물줄기만 남긴다.
            boolean flash = elapsed < FLASH_DURATION;
            if (flash) {
                g.setColor(new Color(105, 241, 255, 148));
                g.fillRect(leftX, topY - up * tile, tile, (1 + up + down) * tile);
                g.fillRect(leftX - left * tile, topY, left * tile, tile);
                g.fillRect(leftX + tile, topY, right * tile, tile);
            }
            float opacity = flash ? 0.9f : 0.36f * (DURATION - elapsed) / (DURATION - FLASH_DURATION);
            g.setComposite(AlphaComposite.SrcOver.derive(opacity));
            g.setColor(new Color(19, 178, 203, 220));
            drawStreams(g, x, y, tile, tile * 0.55f);
            g.setComposite(AlphaComposite.SrcOver.derive(opacity * 0.75f));
            g.setColor(new Color(188, 253, 255, 230));
            drawStreams(g, x, y, tile, tile * 0.18f);
            g.drawImage(SPLASH, x - tile / 2, y - tile / 2, tile, tile, null);
        } finally {
            g.dispose();
        }
    }

    /** 중심에서 피격된 마지막 칸의 가운데까지 물줄기를 그린다. */
    private void drawStreams(Graphics2D g, int x, int y, int tile, float width) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x - left * tile, y, x + right * tile, y);
        g.drawLine(x, y - up * tile, x, y + down * tile);
    }
}
