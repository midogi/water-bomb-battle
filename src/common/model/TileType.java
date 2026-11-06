package common.model;

public enum TileType {
    EMPTY('.'), WALL('#'), BOX('B');

    private final char symbol;

    TileType(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }
}
