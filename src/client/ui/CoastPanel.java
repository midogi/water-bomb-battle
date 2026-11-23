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

    /** 로그인과 로비 패널이 기본 생성자를 사용하면 공통 해안 배경이 선택된다. */
    CoastPanel() {
        this(COAST);
    }

    /** 대기실처럼 다른 배경을 쓰는 패널은 생성할 때 그림을 넘기고 같은 확대·그리기 코드를 사용한다. */
    CoastPanel(BufferedImage background) {
        this.background = background;
    }

    /** Swing이 패널을 그릴 때 배경을 창 비율에 맞춰 채운다. 원본 비율을 유지하므로 가장자리가 일부 잘릴 수 있다. */
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
