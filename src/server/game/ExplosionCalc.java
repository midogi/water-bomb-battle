package server.game;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ExplosionCalc {
    public record Blast(int up, int down, int left, int right, Set<GameMap.Cell> cells) {
        public Blast {
            cells = Collections.unmodifiableSet(new LinkedHashSet<>(cells));
        }
    }

    private ExplosionCalc() {
    }

    /** 폭탄이 놓인 칸을 포함해 상하좌우로 닿는 칸과 방향별 거리를 구한다. */
    public static Blast calculate(GameMap map, Bomb bomb) {
        Set<GameMap.Cell> cells = new LinkedHashSet<>();
        cells.add(bomb.cell());
        int up = ray(map, bomb, 0, -1, cells);
        int down = ray(map, bomb, 0, 1, cells);
        int left = ray(map, bomb, -1, 0, cells);
        int right = ray(map, bomb, 1, 0, cells);
        return new Blast(up, down, left, right, cells);
    }

    private static int ray(GameMap map, Bomb bomb, int dx, int dy, Set<GameMap.Cell> cells) {
        int distance = 0;
        for (int step = 1; step <= bomb.range(); step++) {
            int col = bomb.col() + dx * step;
            int row = bomb.row() + dy * step;
            char tile = map.tile(col, row);
            // 벽은 범위에 넣기 전에 멈춘다. 상자는 맞아야 부서지므로 범위에 넣고 멈춘다.
            if (tile == '#') {
                break;
            }
            cells.add(new GameMap.Cell(col, row));
            distance = step;
            if (tile == 'B') {
                break;
            }
        }
        return distance;
    }
}
