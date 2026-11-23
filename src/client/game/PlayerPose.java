package client.game;

import client.resource.Avatar;
import common.model.Direction;
import common.model.PlayerState;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** 참가자 한 명의 그림 방향과 걸음 박자를 기억한다. 충돌 판정에 쓰는 좌표는 변경하지 않는다. */
final class PlayerPose {
    private int lastX, lastY;
    private Direction facing = Direction.DOWN;
    private boolean walking;
    private long walkStarted;
    private long lastMovement;

    /** 첫 좌표를 기준점으로 저장한다. 첫 STATE에서 출발 위치까지의 거리를 걸음으로 잘못 계산하는 일을 막는다. */
    PlayerPose(GameBoard.Player player) {
        lastX = player.x();
        lastY = player.y();
    }

    /** 내 캐릭터와 상대 모두 실제 좌표 차이로 방향을 정한다. 멈추면 마지막 방향만 남긴다. */
    void update(GameBoard.Player player, long now) {
        int dx = player.x() - lastX;
        int dy = player.y() - lastY;
        boolean moved = dx != 0 || dy != 0;
        if (moved) {
            if (Math.abs(dx) > Math.abs(dy)) facing = dx > 0 ? Direction.RIGHT : Direction.LEFT;
            else facing = dy > 0 ? Direction.DOWN : Direction.UP;
            if (!walking) walkStarted = now;
            lastMovement = now;
        }
        walking = moved && player.state() == PlayerState.ALIVE;
        lastX = player.x();
        lastY = player.y();
    }

    /** 그림자는 발밑에 고정한다. 몸만 들어 올리고 기울인 뒤 그래픽 변환을 해제한다. */
    void draw(Graphics2D graphics, Avatar avatar, GameBoard.Player player, int size, long now, boolean playing) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(player.x(), player.y());
            g.setColor(new Color(25, 48, 53, 64));
            g.fillOval(size / 5, size - 6, size * 3 / 5, 6);
            if (playing && walking && now - lastMovement < 180_000_000L) {
                double step = Math.sin((now - walkStarted) * 2 * Math.PI / 320_000_000L);
                g.translate(0, -2 * Math.abs(step));
                g.rotate(step * 0.045, size / 2.0, size);
            }
            BufferedImage sprite = avatar.facing(facing);
            if (sprite == null) {
                g.setColor(Color.GRAY);
                g.fillOval(4, 4, size - 8, size - 8);
            } else if (facing == Direction.LEFT) {
                g.drawImage(sprite, size, 0, -size, size, null);
            } else {
                g.drawImage(sprite, 0, 0, size, size, null);
            }
        } finally {
            g.dispose();
        }
    }
}
