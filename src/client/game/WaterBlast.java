package client.game;

import client.resource.GameImages;
import common.Constants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** EXPLODE 한 건의 표시 범위. 방향별 길이는 게임 루프에서 이미 벽과 상자를 고려한 결과다. */
record WaterBlast(int col, int row, int up, int down, int left, int right, long started) {
    private static final long DURATION = 360_000_000L;
    private static final BufferedImage SPLASH = GameImages.load("water-splash.png");

    /** 잘못된 효과 메시지는 건너뛰고 정상적인 방 상태와 화면은 계속 유지한다. */
    static WaterBlast parse(String body) {
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
            return new WaterBlast(x, y, u, d, l, r, System.nanoTime());
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    WaterBlast startAt(long time) {
        return new WaterBlast(col, row, up, down, left, right, time);
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
            int x = col * tile + tile / 2;
            int y = row * tile + tile / 2;
            // 앞부분에서 물줄기가 빠르게 뻗고, 끝에서는 흰 거품이 먼저 사라진다.
            float phase = Math.min(1f, (float) (now - started) / DURATION);
            float reach = Math.min(1f, phase / 0.32f);
            int length = Math.round(tile * reach);
            float opacity = Math.min(1f, (1f - phase) / 0.48f);
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive(opacity));
            g.setColor(new Color(19, 178, 203, 220));
            drawStreams(g, x, y, length, tile * 0.55f);
            g.setComposite(java.awt.AlphaComposite.SrcOver.derive(opacity * (1f - phase)));
            g.setColor(new Color(188, 253, 255, 230));
            drawStreams(g, x, y, length, tile * 0.18f);
            g.drawImage(SPLASH, x - tile / 2, y - tile / 2, tile, tile, null);
        } finally {
            g.dispose();
        }
    }

    /** 중심에서 네 방향으로 선을 그린다. 방향별 칸 수에 현재 한 칸당 표시 길이를 곱해 물줄기가 뻗는 모습을 만든다. */
    private void drawStreams(Graphics2D g, int x, int y, int tile, float width) {
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(x - left * tile, y, x + right * tile, y);
        g.drawLine(x, y - up * tile, x, y + down * tile);
    }
}
