package server.game;

import common.Constants;
import common.model.TileType;

/** 맵은 타일 단위로 저장한다. col은 가로 칸, row는 세로 칸이다. */
public final class GameMap {
    public record Cell(int col, int row) { }

    private final char[][] tiles;

    private GameMap(char[][] tiles) {
        this.tiles = tiles;
    }

    /** 한 줄을 맵의 한 행으로 읽는다. 점은 빈칸, #은 벽, B는 상자다. */
    public static GameMap fromRows(String... rows) {
        if (rows.length != Constants.MAP_ROWS) {
            throw new IllegalArgumentException("Expected 13 map rows");
        }
        char[][] tiles = new char[Constants.MAP_ROWS][Constants.MAP_COLUMNS];
        for (int row = 0; row < rows.length; row++) {
            if (rows[row].length() != Constants.MAP_COLUMNS) {
                throw new IllegalArgumentException("Expected 15 map columns");
            }
            for (int col = 0; col < rows[row].length(); col++) {
                char tile = rows[row].charAt(col);
                if (tile != '.' && tile != '#' && tile != 'B') {
                    throw new IllegalArgumentException("Unknown map tile: " + tile);
                }
                tiles[row][col] = tile;
            }
        }
        return new GameMap(tiles);
    }

    public static GameMap arena() {
        String[] rows = new String[Constants.MAP_ROWS];
        for (int row = 0; row < rows.length; row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < Constants.MAP_COLUMNS; col++) {
                boolean border = col == 0 || row == 0
                        || col == Constants.MAP_COLUMNS - 1 || row == rows.length - 1;
                boolean pillar = col % 2 == 0 && row % 2 == 0;
                // 시작 위치 주변은 바로 움직일 수 있도록 상자를 놓지 않는다.
                boolean spawnLane = (row <= 2 || row >= rows.length - 3)
                        && (col <= 2 || col >= Constants.MAP_COLUMNS - 3);
                line.append(border || pillar ? '#' : spawnLane ? '.' : 'B');
            }
            rows[row] = line.toString();
        }
        return fromRows(rows);
    }

    public char tile(int col, int row) {
        // 맵 바깥을 벽으로 보면 폭발 범위도 경계에서 멈춘다.
        if (col < 0 || row < 0 || col >= Constants.MAP_COLUMNS || row >= Constants.MAP_ROWS) {
            return TileType.WALL.symbol();
        }
        return tiles[row][col];
    }

    /** 이동할 위치에서 캐릭터 사각형이 걸치는 칸을 모두 확인한다. */
    public boolean canOccupy(int x, int y) {
        int size = Constants.TILE_SIZE;
        if (x < 0 || y < 0 || x + size > Constants.MAP_COLUMNS * size
                || y + size > Constants.MAP_ROWS * size) {
            return false;
        }
        // 오른쪽과 아래쪽은 마지막 픽셀까지만 센다. 경계 너머 칸은 확인할 필요가 없다.
        for (int row = y / size; row <= (y + size - 1) / size; row++) {
            for (int col = x / size; col <= (x + size - 1) / size; col++) {
                if (tile(col, row) != '.') {
                    return false;
                }
            }
        }
        return true;
    }

    boolean breakBox(Cell cell) {
        if (tile(cell.col(), cell.row()) != 'B') {
            return false;
        }
        tiles[cell.row()][cell.col()] = '.';
        return true;
    }

    public GameMap copy() {
        char[][] clone = new char[tiles.length][];
        for (int row = 0; row < tiles.length; row++) {
            clone[row] = tiles[row].clone();
        }
        return new GameMap(clone);
    }

    /** 전송할 때는 위쪽 행부터 줄바꿈 없이 붙여 보낸다. */
    public String toWire() {
        StringBuilder result = new StringBuilder(Constants.MAP_COLUMNS * Constants.MAP_ROWS);
        for (char[] row : tiles) {
            result.append(row);
        }
        return result.toString();
    }
}
