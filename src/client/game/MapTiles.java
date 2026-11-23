package client.game;

import client.resource.GameImages;
import common.Constants;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.EnumMap;

/** 대기실과 게임 화면이 같은 타일 그림을 사용한다. 테마별 그림은 처음 한 번만 그린다. */
public final class MapTiles {
    private static final int SIZE = Constants.TILE_SIZE;
    private static final EnumMap<CoastMap, MapTiles> STYLES = new EnumMap<>(CoastMap.class);
    private static final MapTiles DEFAULT = new MapTiles(null);
    private final BufferedImage[] floors = new BufferedImage[2];
    private final BufferedImage wall;
    private final BufferedImage crate;

    private MapTiles(CoastMap map) {
        if (map == null) {
            floors[0] = floors[1] = GameImages.load("floor.png");
            wall = GameImages.load("wall.png");
            crate = GameImages.load("crate.png");
        } else {
            floors[0] = draw(map, '.', false);
            floors[1] = draw(map, '.', true);
            wall = draw(map, '#', false);
            crate = draw(map, 'B', false);
        }
    }

    /** 서버가 다른 맵을 보낸 경우에도 원래 프로젝트 타일로 정확한 지형을 표시한다. */
    public static MapTiles forMap(CoastMap map) {
        return map == null ? DEFAULT : STYLES.computeIfAbsent(map, MapTiles::new);
    }

    public BufferedImage tile(char cell, int col, int row) {
        return switch (cell) {
            case '#' -> wall;
            case 'B' -> crate;
            default -> floors[(col + row) & 1];
        };
    }

    /** 타일 한 장을 40×40 기준 좌표로 그린 뒤 실제 타일 크기에 맞춰 외곽선과 그림자를 함께 확대·축소한다. */
    private static BufferedImage draw(CoastMap map, char cell, boolean alternate) {
        BufferedImage tile = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = tile.createGraphics();
        try {
            g.scale(SIZE / 40.0, SIZE / 40.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(map.ground);
            g.fillRect(0, 0, 40, 40);
            g.setColor(new Color(255, 255, 245, alternate ? 42 : 22));
            g.fillRect(0, 0, 40, 40);
            g.drawLine(3, 6, 9, 5);
            g.drawLine(25, 29, 32, 28);
            if (cell == '.') return tile;
            g.setColor(new Color(26, 43, 49, 85));
            g.fillOval(2, 25, 36, 13);
            if (cell == 'B') {
                // 모든 테마에서 X자 판재가 보이면 물줄기로 부술 수 있는 상자다.
                g.setColor(map.crate.darker());
                g.fillRoundRect(4, 5, 32, 30, 5, 5);
                g.setColor(map.crate);
                g.fillRoundRect(5, 3, 30, 26, 4, 4);
                g.setColor(map.crate.darker());
                g.drawRect(8, 6, 24, 20);
                g.drawLine(8, 16, 32, 16);
                g.setColor(map.crate.brighter());
                g.setStroke(new BasicStroke(3));
                g.drawLine(8, 7, 31, 25);
                g.drawLine(31, 7, 8, 25);
            } else {
                drawLandmark(g, map);
            }
        } finally {
            g.dispose();
        }
        return tile;
    }

    /** 맵마다 고정 벽의 실루엣을 다르게 그린다. 그림이 달라도 충돌 기준은 모두 # 한 칸이다. */
    private static void drawLandmark(Graphics2D g, CoastMap map) {
        Color shade = map.wall.darker();
        g.setColor(shade);
        switch (map) {
            case TIDE, FROST -> {
                int[] xs = {2, 7, 24, 36, 38, 26, 8};
                int[] ys = {24, 9, 4, 12, 27, 34, 32};
                g.fillPolygon(xs, ys, xs.length);
                g.setColor(map.wall.brighter());
                g.fillPolygon(new int[]{7, 24, 35, 21}, new int[]{10, 5, 12, 18}, 4);
                g.setColor(map.wall);
                g.fillPolygon(new int[]{3, 7, 21, 26, 8}, new int[]{24, 10, 18, 33, 31}, 5);
                if (map == CoastMap.FROST) {
                    g.setColor(Color.WHITE);
                    g.drawPolyline(new int[]{12, 18, 23, 28}, new int[]{10, 12, 8, 10}, 4);
                }
            }
            case MARKET, QUAY -> {
                g.fillRoundRect(5, 12, 30, 22, 4, 4);
                g.setColor(map.wall.brighter());
                g.fillRoundRect(8, 13, 24, 17, 4, 4);
                g.setColor(map.wall);
                g.fillPolygon(new int[]{2, 10, 30, 38}, new int[]{18, 4, 4, 18}, 4);
                if (map == CoastMap.MARKET) {
                    g.setColor(new Color(255, 228, 158));
                    for (int x = 8; x < 30; x += 9) g.fillRect(x, 10, 4, 9);
                }
                g.setColor(shade);
                g.fillRect(17, 22, 6, 10);
            }
            case GROVE -> {
                g.fillRoundRect(17, 22, 7, 14, 3, 3);
                for (int tier = 0; tier < 3; tier++) {
                    int y = 28 - tier * 7;
                    g.setColor(tier == 0 ? shade : tier == 1 ? map.wall : map.wall.brighter());
                    g.fillPolygon(new int[]{3 + tier * 3, 20, 37 - tier * 3},
                            new int[]{y, y - 16, y}, 3);
                }
            }
            case DUNE -> {
                for (int x : new int[]{5, 21}) {
                    g.setColor(shade);
                    g.fillRoundRect(x, 9, 14, 24, 5, 6);
                    g.setColor(map.wall.brighter());
                    g.fillOval(x, 6, 14, 9);
                    g.setColor(map.wall);
                    g.drawOval(x + 3, 8, 7, 4);
                }
                g.setColor(map.crate.brighter());
                g.fillRoundRect(4, 18, 32, 4, 3, 3);
            }
            case DOCK, CANAL -> {
                g.fillRoundRect(2, 5, 36, 30, 5, 5);
                g.setColor(map.wall);
                g.fillRoundRect(2, 3, 35, 25, 5, 5);
                g.setColor(map.wall.brighter());
                if (map == CoastMap.DOCK) {
                    for (int x = 7; x < 34; x += 6) g.fillRoundRect(x, 6, 2, 19, 2, 2);
                } else {
                    g.drawLine(4, 15, 35, 15);
                    g.drawLine(14, 4, 14, 15);
                    g.drawLine(25, 15, 25, 27);
                }
            }
            case HEART -> {
                g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(19, 32, 20, 8);
                g.setColor(map.wall);
                g.drawPolyline(new int[]{8, 9, 19, 29, 32}, new int[]{11, 19, 24, 18, 9}, 5);
                g.setColor(map.wall.brighter());
                g.drawLine(19, 19, 20, 5);
                g.fillOval(6, 8, 6, 7);
            }
        }
    }
}
