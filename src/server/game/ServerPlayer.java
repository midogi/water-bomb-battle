package server.game;

import common.Constants;
import common.model.Direction;
import common.model.PlayerState;

/** x,y는 타일 크기인 캐릭터 사각형의 좌상단 픽셀 좌표입니다. */
public final class ServerPlayer {
    private final int userId;
    int x;
    int y;
    Direction direction = Direction.STOP;
    PlayerState state = PlayerState.ALIVE;
    long trappedUntil;
    boolean connected = true;

    public ServerPlayer(int userId, int x, int y) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        this.userId = userId;
        this.x = x;
        this.y = y;
    }

    public int userId() {
        return userId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public PlayerState state() {
        return state;
    }

    boolean overlaps(GameMap.Cell cell, int candidateX, int candidateY) {
        int size = Constants.TILE_SIZE;
        int left = cell.col() * size;
        int top = cell.row() * size;
        return candidateX < left + size && candidateX + size > left
                && candidateY < top + size && candidateY + size > top;
    }
}
