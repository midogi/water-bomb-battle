package client.ui;

import client.resource.GameImages;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

// 로그인과 로비가 하나의 배경 이미지를 공유한다.
class CoastPanel extends JPanel {
    private static final BufferedImage COAST = GameImages.load("coast.jpg");
    private final BufferedImage background;

    CoastPanel() {
        this(COAST);
    }

    CoastPanel(BufferedImage background) {
        this.background = background;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        // 창 비율이 달라지면 그림의 가운데를 기준으로 가장자리만 잘라낸다.
        double scale = Math.max((double) getWidth() / background.getWidth(),
                (double) getHeight() / background.getHeight());
        int width = (int) Math.ceil(background.getWidth() * scale);
        int height = (int) Math.ceil(background.getHeight() * scale);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(background, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
        } finally {
            g.dispose();
        }
    }
}
