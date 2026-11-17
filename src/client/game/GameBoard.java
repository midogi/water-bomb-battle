package client.game;

import common.Constants;
import common.model.PlayerState;
import java.util.ArrayList;
import java.util.List;

// GAME_INIT으로 한 번 받는 타일 맵과 플레이어 시작 좌표다.
public record GameBoard(String tiles, List<Player> players) {
    public record Player(int id, int x, int y, PlayerState state) { }

    public static GameBoard parse(String body) {
        String[] parts = body.split("[|]", 2);
        if (parts.length != 2 || parts[0].length() != Constants.MAP_COLUMNS * Constants.MAP_ROWS
                || !parts[0].matches("[.#B]+")) {
            throw new IllegalArgumentException("게임 시작 맵 형식을 확인해 주세요.");
        }
        return new GameBoard(parts[0], parsePlayers(parts[1], false));
    }

    // STATE 본문은 틱|참가자 목록|폭탄 목록이다. 이동 화면에는 참가자 목록을 사용한다.
    public static List<Player> playersFromState(String body) {
        String[] parts = body.split("[|]", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("게임 상태 형식을 확인해 주세요.");
        }
        return parsePlayers(parts[1], true);
    }

    private static List<Player> parsePlayers(String body, boolean includesState) {
        List<Player> players = new ArrayList<>();
        if (body.isEmpty()) {
            return List.of();
        }
        for (String row : body.split(",")) {
            String[] fields = row.split(":", -1);
            if (fields.length != (includesState ? 4 : 3)) {
                throw new IllegalArgumentException("플레이어 좌표 형식을 확인해 주세요.");
            }
            players.add(new Player(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]),
                    Integer.parseInt(fields[2]), includesState ? PlayerState.valueOf(fields[3]) : PlayerState.ALIVE));
        }
        return List.copyOf(players);
    }
}
