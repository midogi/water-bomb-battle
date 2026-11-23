package client.game;

import java.awt.Color;
import java.util.List;

/** 화면에서 사용하는 해안 맵 목록. 한 줄이 한 행이며 #은 고정 벽, B는 부서지는 상자, .은 바닥이다. */
public enum CoastMap {
    TIDE("갈대 갯벌", "돌무더기 사이로 좁은 길이 이어지는 초지", 0x92bd72, 0x60796e, 0xc79450,
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
    FROST("유빙 순환선", "두 순환로와 세로 레일을 잇는 얼음 항구", 0xb5e5ed, 0x649bc4, 0xee9263,
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
    MARKET("조개 경매장", "가판대 사이를 빠져나오면 트인 중앙 광장", 0xc1a479, 0x9d6854, 0x4ba9a2,
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
    QUAY("등대 큰길", "두 주거 구역 사이로 난 긴 중앙 도로", 0xbcbc9c, 0xbf674d, 0xd3a14a,
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
    GROVE("솔바람 캠프", "솔숲 사이의 작은 빈터들이 굽은 길로 연결", 0x9ac77a, 0x337a61, 0xd07b4d,
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
    DUNE("모래톱 잔교", "엇갈린 짧은 잔교를 돌아 중앙 교차로로 진입", 0xe7c67f, 0xaa8356, 0xb67c46,
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
    DOCK("푸른 컨테이너 부두", "하역장 사이를 비스듬히 가로지르는 화물 통로", 0x91adb3, 0x387795, 0xd69253,
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
    HEART("산호 하트 정원", "두 산호 고리에서 아래쪽 한 길로 모이는 하트", 0xa6bcc9, 0xba7092, 0xeea574,
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
    CANAL("밤바다 직선 수로", "긴 세로 둑과 드문 횡단로가 만나는 수로", 0x7686a4, 0x505c80, 0xd9ad51,
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

    public final String title;
    public final String description;
    final Color ground, wall, crate;
    private final List<String> rows;
    private final String tiles;

    CoastMap(String title, String description, int ground, int wall, int crate, String... rows) {
        this.title = title;
        this.description = description;
        this.ground = new Color(ground);
        this.wall = new Color(wall);
        this.crate = new Color(crate);
        this.rows = List.of(rows);
        this.tiles = String.join("", rows);
    }

    /** 미리보기 게임 루프에 독립된 배열을 넘겨 카탈로그 원본을 보호한다. */
    public String[] rows() {
        return rows.toArray(String[]::new);
    }

    /** 서버에는 맵 이름이 없으므로 GAME_INIT의 전체 배치가 일치할 때만 해당 테마를 적용한다. */
    public static CoastMap matching(String tiles) {
        for (CoastMap map : values()) {
            if (map.tiles.equals(tiles)) return map;
        }
        return null;
    }

    @Override
    public String toString() {
        return title;
    }
}
