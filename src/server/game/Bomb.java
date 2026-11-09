package server.game;

public record Bomb(int ownerId, int col, int row, int range, long explodeAtTick) {
    public GameMap.Cell cell() {
        return new GameMap.Cell(col, row);
    }
}
