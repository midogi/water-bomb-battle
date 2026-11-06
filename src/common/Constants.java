package common;

/** 개발 규약의 수치 초안. 변경 전 팀 합의가 필요합니다. */
public final class Constants {
    public static final String DEFAULT_HOST = "localhost";
    public static final int PORT = 5000;
    public static final int LOBBY_ROOM_ID = 0;
    public static final int MAP_COLUMNS = 15;
    public static final int MAP_ROWS = 13;
    public static final int TILE_SIZE = 40;
    public static final int TICK_MILLIS = 50;
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;
    public static final int BOMB_FUSE_TICKS = 60;
    public static final int DEFAULT_BOMB_RANGE = 1;
    public static final int DEFAULT_BOMB_LIMIT = 1;
    public static final int TRAPPED_TIMEOUT_TICKS = 100;
    public static final int MAX_FRAME_BYTES = 64 * 1024;

    private Constants() {
    }
}
