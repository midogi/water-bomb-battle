package client.game;

import common.Constants;
import common.model.PlayerState;
import java.util.ArrayList;
import java.util.List;

// GAME_INIT에서 만든 게임판이다. STATE가 오면 참가자 목록을, BOX_BREAK가 오면 해당 타일을 바꾼 새 GameBoard를 사용한다.
public record GameBoard(String tiles, List<Player> players) {
    /** x와 y는 캐릭터가 차지하는 영역의 왼쪽 위 픽셀 좌표다. */
    public record Player(int id, int x, int y, PlayerState state) { }

    /** GAME_INIT 본문에서 맵 크기와 타일 문자를 검사하고, 게임 화면에 처음 그릴 맵과 참가자를 만든다. */
    public static GameBoard parse(String body) {
        String[] parts = body.split("[|]", 2);
        if (parts.length != 2 || parts[0].length() != Constants.MAP_COLUMNS * Constants.MAP_ROWS
                || !parts[0].matches("[.#B]+")) {
            throw new IllegalArgumentException("게임 시작 맵 형식을 확인해 주세요.");
        }
        return new GameBoard(parts[0], parsePlayers(parts[1], false));
    }

    /** col과 row는 물풍선의 칸 번호다. ticksLeft는 폭발까지 남은 서버 틱 수이며, 그리기 직전에만 칸을 픽셀로 바꾼다. */
    public record Balloon(int col, int row, int ticksLeft) { }
    public record State(List<Player> players, List<Balloon> balloons) { }

    /** LobbySession과 미리보기가 STATE를 받으면 호출한다. 참가자와 물풍선 목록을 같은 틱의 결과로 묶는다. */
    public static State parseState(String body) {
        String[] parts = body.split("[|]", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("게임 상태 형식을 확인해 주세요.");
        }
        List<Balloon> balloons = new ArrayList<>();
        if (!parts[2].isEmpty()) {
            for (String entry : parts[2].split(",")) {
                String[] fields = entry.split(":", -1);
                if (fields.length != 3) throw new IllegalArgumentException("물풍선 형식을 확인해 주세요.");
                int col = Integer.parseInt(fields[0]);
                int row = Integer.parseInt(fields[1]);
                int ticks = Integer.parseInt(fields[2]);
                if (col < 0 || col >= Constants.MAP_COLUMNS || row < 0 || row >= Constants.MAP_ROWS || ticks < 0)
                    throw new IllegalArgumentException("물풍선 좌표 또는 시간이 잘못되었습니다.");
                balloons.add(new Balloon(col, row, ticks));
            }
        }
        return new State(parsePlayers(parts[1], true), List.copyOf(balloons));
    }

    /** 서버가 보낸 BOX_BREAK:열:행:아이템에서 좌표를 읽는다. 다른 이벤트는 맵을 바꾸지 않는다. */
    public GameBoard afterEvent(String body) {
        if (!body.startsWith("BOX_BREAK:")) return this;
        String[] parts = body.split(":", -1);
        if (parts.length != 4) return this;
        int col, row;
        try {
            col = Integer.parseInt(parts[1]);
            row = Integer.parseInt(parts[2]);
        } catch (NumberFormatException invalidPosition) {
            return this;
        }
        if (col < 0 || col >= Constants.MAP_COLUMNS || row < 0 || row >= Constants.MAP_ROWS) return this;
        int index = row * Constants.MAP_COLUMNS + col;
        if (tiles.charAt(index) != 'B') return this;
        StringBuilder next = new StringBuilder(tiles);
        next.setCharAt(index, '.');
        return new GameBoard(next.toString(), players);
    }

    /** 두 메시지에서 공통으로 사용하는 참가자 해석 과정이다. 시작 좌표에는 ALIVE를 붙이고 STATE에서는 전달받은 상태를 읽는다. */
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
