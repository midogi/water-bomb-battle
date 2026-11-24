package common.model;

import java.util.List;

/** 서버와 화면이 같이 읽는 해안 맵 배치다. #은 벽, B는 상자, 점은 빈칸이다. */
public enum ArenaMap {
    TIDE(
            "###############",
            "#..BB..B..BB..#",
            "#.B#.#..#.#.B.#",
            "#B..B.B..B.#..#",
            "#.#.#..#.#.##B#",
            "#B.B..B.B.....#",
            "#B#.#.#..#..#B#",
            "#..B.B..B.B.B.#",
            "#B#.##.#..#.#.#",
            "#..B..B..B..B.#",
            "#.B#..#.##..B.#",
            "#..B.B..B.BB..#",
            "###############"),
    FROST(
            "###############",
            "#..BB...BB.B..#",
            "#..###...###..#",
            "#B.#.B.B.B.#.B#",
            "#..#..#.#..#..#",
            "#B.B.B#.#B...B#",
            "#...B.#.#.B...#",
            "#B...B#.#B.B.B#",
            "#..#..#.#..#..#",
            "#B.#.B.B.B.#.B#",
            "#..###...###..#",
            "#..B.BB...BB..#",
            "###############"),
    MARKET(
            "###############",
            "#..BBBB.BBBB..#",
            "#.B#BB...BB#B.#",
            "#BB#..B.B..#BB#",
            "#..B#.....#B..#",
            "#BB.........BB#",
            "#B.B.......B.B#",
            "#BB.........BB#",
            "#..B#.....#B..#",
            "#BB#..B.B..#BB#",
            "#.B#BB...BB#B.#",
            "#..BBB..BBBB..#",
            "###############"),
    QUAY(
            "###############",
            "#..B#B...B#B..#",
            "#.B.BB.#.BBB..#",
            "#B#B#....B#B#B#",
            "#..BB.B.B..BB.#",
            "#B##B..#..B##B#",
            "#.....B.......#",
            "#B##B..#..B##B#",
            "#.BB..B.B.BB..#",
            "#B#B#....B#B#B#",
            "#..BBB.#.BB.B.#",
            "#..B#B...B#B..#",
            "###############"),
    GROVE(
            "###############",
            "#..B..BB#.B...#",
            "#..#B.#...#B..#",
            "#BB#..B.##..B.#",
            "#.#..##.B.B.#B#",
            "#..BB...B.#...#",
            "#B.#.B#..B..B##",
            "#.B..#..B.##..#",
            "#B##....B..B#.#",
            "#..B.B#..##.BB#",
            "#.B.#...B#.B..#",
            "#...BB.B..BB..#",
            "###############"),
    DUNE(
            "###############",
            "#..B...BB..B..#",
            "#.B##..B..##..#",
            "#...BB...B.BB.#",
            "#B#...#.#...#B#",
            "#.BB..B.B..BB.#",
            "#...##...##...#",
            "#BB...B.B...BB#",
            "#.##..#.#..##.#",
            "#B..B.....BB.B#",
            "#..##..B..##..#",
            "#..BB...B.BB..#",
            "###############"),
    DOCK(
            "###############",
            "#..BB...BBB...#",
            "#..##.B.##.BB.#",
            "#B.BB...##..BB#",
            "#B..B.##..B...#",
            "#.##..##.BB..B#",
            "#..BB.....##..#",
            "#B..BB.##..B..#",
            "#..B...##.BB.B#",
            "#BB.##....B.BB#",
            "#..B##.BB.##..#",
            "#..BB...B..B..#",
            "###############"),
    HEART(
            "###############",
            "#..BB..B..BB..#",
            "#..##B...B##..#",
            "#B#..B#.#B..#B#",
            "#.#...B.B...#.#",
            "#B.#...B...#.B#",
            "#...#..B..#...#",
            "#.B..#.B.#..B.#",
            "#B.B..#.#..B.B#",
            "#..BB..#..BB..#",
            "#..#B.....B#..#",
            "#..BB..B..BB..#",
            "###############"),
    CANAL(
            "###############",
            "#..B..B.B..B..#",
            "#..#.#...#.#..#",
            "#B.#B#.B.#B#.B#",
            "#..#.#.#.#.#..#",
            "#B.B.#.#.#.B.B#",
            "#..#...B...#..#",
            "#B.#.#.#.#.#.B#",
            "#..#.#.#.#.#..#",
            "#B.#B#.B.#B#.B#",
            "#..#.#...#.#..#",
            "#..B..B.B..B..#",
            "###############");

    private final List<String> rows;
    private final String tiles;

    ArenaMap(String... rows) {
        this.rows = List.of(rows);
        this.tiles = String.join("", rows);
    }

    /** 대기실의 RANDOM은 아직 실제 배치가 정해지지 않았다는 뜻이다. */
    public static ArenaMap fromSelection(String code) {
        return "RANDOM".equals(code) ? null : valueOf(code);
    }

    public static String selectionCode(ArenaMap map) {
        return map == null ? "RANDOM" : map.name();
    }

    /** 한 경기의 GameState를 만들 때 한 번만 뽑아서 모든 참가자에게 같은 배치를 보낸다. */
    public static ArenaMap forMatch(ArenaMap selection) {
        if (selection != null) return selection;
        ArenaMap[] maps = values();
        return maps[java.util.concurrent.ThreadLocalRandom.current().nextInt(maps.length)];
    }

    public String[] rows() {
        return rows.toArray(String[]::new);
    }

    public boolean matches(String tiles) {
        return this.tiles.equals(tiles);
    }
}
