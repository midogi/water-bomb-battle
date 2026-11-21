package server.game;

/** 폭탄 위치와 위력은 칸 단위다. explodeAtTick은 타이머가 끝나는 틱 번호다. */
public record Bomb(int ownerId, int col, int row, int range, long explodeAtTick) {
    public GameMap.Cell cell() {
        return new GameMap.Cell(col, row);
    }
}
