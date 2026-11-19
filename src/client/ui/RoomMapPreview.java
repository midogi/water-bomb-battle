package client.ui;

import client.resource.GameImages;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

// 대기실 썸네일과 두 창 미리보기가 같은 타일 배치를 사용한다.
final class RoomMapPreview {
    private static final int TILE = 8;
    private static final ImageIcon ICON = draw();

    static String[] rows() {
        String wall = "###############";
        String lane = "#.............#";
        String boxes = "#.B.B.B.B.B.B.#";
        return new String[] {wall, lane, boxes, lane, boxes, lane, boxes,
                lane, boxes, lane, boxes, lane, wall};
    }

    static ImageIcon icon() {
        return ICON;
    }

    private static ImageIcon draw() {
        String[] rows = rows();
        BufferedImage floor = GameImages.load("floor.png");
        BufferedImage wall = GameImages.load("wall.png");
        BufferedImage crate = GameImages.load("crate.png");
        BufferedImage result = new BufferedImage(rows[0].length() * TILE, rows.length * TILE,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int row = 0; row < rows.length; row++) {
                for (int col = 0; col < rows[row].length(); col++) {
                    BufferedImage tile = switch (rows[row].charAt(col)) {
                        case '#' -> wall;
                        case 'B' -> crate;
                        default -> floor;
                    };
                    g.drawImage(tile, col * TILE, row * TILE, TILE, TILE, null);
                }
            }
        } finally {
            g.dispose();
        }
        return new ImageIcon(result);
    }
}
