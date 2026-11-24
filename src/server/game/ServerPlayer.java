package server.game;

import common.Constants;
import common.model.Direction;
import common.model.ItemType;
import common.model.PlayerState;
import java.util.EnumMap;

/** x와 y는 캐릭터 판정 사각형의 왼쪽 위 픽셀 좌표다. */
public final class ServerPlayer {
    private final int userId;
    int x;
    int y;
    Direction direction = Direction.STOP;
    Direction facing = Direction.DOWN;
    PlayerState state = PlayerState.ALIVE;
    long trappedUntil;
    long invisibleUntil;
    long lensUntil;
    final EnumMap<ItemType, Integer> items = new EnumMap<>(ItemType.class);
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
        // 폭탄과 폭발 범위는 타일 좌표라서 픽셀로 바꿔 비교한다.
        return overlaps(candidateX, candidateY, cell.col() * Constants.TILE_SIZE, cell.row() * Constants.TILE_SIZE);
    }

    boolean overlaps(ServerPlayer other) {
        return overlaps(x, y, other.x, other.y);
    }

    private static boolean overlaps(int x, int y, int left, int top) {
        // 테두리만 맞닿으면 겹친 면적이 없다. 두 축에서 모두 안쪽으로 들어와야 한다.
        int size = Constants.TILE_SIZE;
        return x < left + size && x + size > left && y < top + size && y + size > top;
    }
}
