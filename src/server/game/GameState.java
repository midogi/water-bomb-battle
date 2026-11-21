package server.game;

import common.Constants;
import common.model.Direction;
import common.model.PlayerState;
import common.protocol.Message;
import common.protocol.MsgType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/** 방 한 판의 맵, 플레이어, 폭탄 상태를 관리한다. */
public final class GameState {
    public record PlayerView(int userId, int x, int y, PlayerState state) { }
    public record Snapshot(long tick, List<PlayerView> players, List<Bomb> bombs, boolean finished) {
        public Snapshot {
            players = List.copyOf(players);
            bombs = List.copyOf(bombs);
        }
    }

    private final int roomId;
    private final GameMap map;
    private final int movementPixelsPerTick;
    private final Map<Integer, ServerPlayer> players = new LinkedHashMap<>();
    private final Map<GameMap.Cell, Bomb> bombs = new LinkedHashMap<>();
    // 설치할 때 폭탄과 겹친 플레이어는 그 칸에서 빠져나갈 수 있다.
    private final Map<GameMap.Cell, Set<Integer>> bombPassers = new HashMap<>();
    private long tick;
    private boolean finished;

    public GameState(int roomId, GameMap map, List<ServerPlayer> participants, int movementPixelsPerTick) {
        if (roomId <= 0 || participants.size() < Constants.MIN_PLAYERS
                || participants.size() > Constants.MAX_PLAYERS
                || movementPixelsPerTick <= 0 || movementPixelsPerTick > Constants.TILE_SIZE) {
            throw new IllegalArgumentException("Invalid room, player count, or movement speed");
        }
        this.roomId = roomId;
        // 방마다 상태를 따로 바꿀 수 있게 맵과 플레이어를 복사한다.
        this.map = map.copy();
        this.movementPixelsPerTick = movementPixelsPerTick;
        for (ServerPlayer participant : participants) {
            ServerPlayer player = new ServerPlayer(participant.userId(), participant.x(), participant.y());
            if (!map.canOccupy(player.x, player.y) || players.putIfAbsent(player.userId(), player) != null) {
                throw new IllegalArgumentException("Invalid spawn or duplicate userId");
            }
            for (ServerPlayer previous : players.values()) {
                if (previous != player && player.overlaps(previous)) {
                    throw new IllegalArgumentException("Overlapping spawn positions");
                }
            }
        }
    }

    public static GameState arena(int roomId, List<Integer> userIds, int movementPixelsPerTick) {
        // 전달받은 참가자 순서대로 맵 모서리에 시작 위치를 잡는다.
        int[][] cells = {{1, 1}, {13, 1}, {1, 11}, {13, 11}};
        if (userIds.size() < Constants.MIN_PLAYERS || userIds.size() > cells.length) {
            throw new IllegalArgumentException("Expected 2 to 4 players");
        }
        List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            players.add(new ServerPlayer(userIds.get(i), cells[i][0] * Constants.TILE_SIZE,
                    cells[i][1] * Constants.TILE_SIZE));
        }
        return new GameState(roomId, GameMap.arena(), players, movementPixelsPerTick);
    }

    public boolean hasPlayer(int userId) {
        return players.containsKey(userId);
    }

    public Snapshot snapshot() {
        // 다음 틱에 위치가 바뀌어도 이미 넘긴 스냅샷에는 반영되지 않는다.
        return new Snapshot(tick, players.values().stream().map(player ->
                new PlayerView(player.userId(), player.x, player.y, player.state)).toList(),
                List.copyOf(bombs.values()), finished);
    }

    /** 경기 시작 때 전체 맵과 플레이어의 시작 좌표를 보낸다. */
    public Message initialMessage() {
        String positions = players.values().stream().map(player ->
                player.userId() + ":" + player.x + ":" + player.y).collect(Collectors.joining(","));
        return message(MsgType.GAME_INIT, map.toWire() + "|" + positions);
    }

    /** 매 틱의 위치와 생존 상태, 남은 폭탄 시간을 넘긴다. 폭탄 시간은 틱 단위다. */
    public Message stateMessage() {
        String positions = players.values().stream().map(player ->
                player.userId() + ":" + player.x + ":" + player.y + ":" + player.state)
                .collect(Collectors.joining(","));
        String bombList = bombs.values().stream().map(bomb -> bomb.col() + ":" + bomb.row()
                + ":" + Math.max(0, bomb.explodeAtTick() - tick)).collect(Collectors.joining(","));
        return message(MsgType.STATE, tick + "|" + positions + "|" + bombList);
    }

    /** 입력, 이동, 설치, 폭발·탈락 순서로 한 틱을 처리한다. */
    public List<Message> step(List<GameCommand> commands) {
        if (finished) {
            return List.of();
        }
        tick++;
        List<Message> messages = new ArrayList<>();
        Set<Integer> bombRequests = new LinkedHashSet<>();
        for (GameCommand command : commands) {
            ServerPlayer player = players.get(command.userId());
            if (player == null) {
                continue;
            }
            if (command instanceof GameCommand.Leave && player.connected) {
                // 퇴장한 플레이어도 상태 목록에 남겨 클라이언트에 탈락 상태를 보낸다.
                player.connected = false;
                player.state = PlayerState.DEAD;
                player.direction = Direction.STOP;
                messages.add(message(MsgType.PLAYER_LEFT, Integer.toString(player.userId())));
            } else if (player.state == PlayerState.ALIVE && player.connected) {
                if (command instanceof GameCommand.Input input) {
                    // STOP이나 새 방향이 올 때까지 이 방향으로 계속 움직인다.
                    player.direction = input.direction();
                } else if (command instanceof GameCommand.PlaceBomb) {
                    bombRequests.add(player.userId());
                }
            }
        }
        for (ServerPlayer player : players.values()) {
            move(player);
        }
        for (int userId : bombRequests) {
            plant(players.get(userId));
        }
        explode(messages);
        resolveTrappedPlayers(messages);
        messages.add(stateMessage());
        // 같은 틱에 마지막 두 명이 탈락해도 무승부가 나오도록 여기서 한 번만 판정한다.
        Judge.result(players.values()).ifPresent(result -> {
            finished = true;
            messages.add(message(MsgType.RESULT, result));
        });
        return List.copyOf(messages);
    }

    private void move(ServerPlayer player) {
        if (player.state != PlayerState.ALIVE) {
            return;
        }
        int dx = player.direction == Direction.LEFT ? -1 : player.direction == Direction.RIGHT ? 1 : 0;
        int dy = player.direction == Direction.UP ? -1 : player.direction == Direction.DOWN ? 1 : 0;
        // 이동 속도가 높아도 중간의 벽이나 폭탄을 건너뛰지 않도록 1픽셀씩 확인한다.
        for (int pixel = 0; pixel < movementPixelsPerTick && (dx != 0 || dy != 0); pixel++) {
            int x = player.x + dx;
            int y = player.y + dy;
            if (!map.canOccupy(x, y)) {
                break;
            }
            boolean blocked = bombs.keySet().stream().anyMatch(cell -> player.overlaps(cell, x, y)
                    && !bombPassers.getOrDefault(cell, Set.of()).contains(player.userId()));
            if (blocked) {
                break;
            }
            player.x = x;
            player.y = y;
            // 폭탄 칸을 완전히 벗어나면 다시 들어갈 수 없다.
            for (Map.Entry<GameMap.Cell, Set<Integer>> entry : bombPassers.entrySet()) {
                if (!player.overlaps(entry.getKey(), x, y)) {
                    entry.getValue().remove(player.userId());
                }
            }
        }
    }

    private void plant(ServerPlayer player) {
        if (player.state != PlayerState.ALIVE || !player.connected) {
            return;
        }
        long active = bombs.values().stream().filter(bomb -> bomb.ownerId() == player.userId()).count();
        int size = Constants.TILE_SIZE;
        // 캐릭터가 여러 칸에 걸쳐 있어도 중심이 있는 칸에 폭탄을 놓는다.
        GameMap.Cell cell = new GameMap.Cell((player.x + size / 2) / size, (player.y + size / 2) / size);
        if (active >= Constants.DEFAULT_BOMB_LIMIT || bombs.containsKey(cell)
                || map.tile(cell.col(), cell.row()) != '.') {
            return;
        }
        bombs.put(cell, new Bomb(player.userId(), cell.col(), cell.row(),
                Constants.DEFAULT_BOMB_RANGE, tick + Constants.BOMB_FUSE_TICKS));
        Set<Integer> passers = new HashSet<>();
        for (ServerPlayer other : players.values()) {
            if (other.overlaps(cell, other.x, other.y)) {
                passers.add(other.userId());
            }
        }
        bombPassers.put(cell, passers);
    }

    private void explode(List<Message> messages) {
        // 같은 틱의 폭발은 모두 상자가 부서지기 전 맵으로 계산한다.
        GameMap before = map.copy();
        Queue<Bomb> pending = new ArrayDeque<>();
        for (Bomb bomb : bombs.values()) {
            if (bomb.explodeAtTick() <= tick) {
                pending.add(bomb);
            }
        }
        Set<GameMap.Cell> exploded = new HashSet<>();
        Set<GameMap.Cell> hit = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            Bomb bomb = pending.remove();
            // 여러 폭발에 걸려 큐에 중복으로 들어간 폭탄은 한 번만 터뜨린다.
            if (!exploded.add(bomb.cell())) {
                continue;
            }
            ExplosionCalc.Blast blast = ExplosionCalc.calculate(before, bomb);
            messages.add(message(MsgType.EVENT, "EXPLODE:" + bomb.col() + ":" + bomb.row()
                    + ":" + blast.up() + ":" + blast.down() + ":" + blast.left() + ":" + blast.right()));
            hit.addAll(blast.cells());
            for (GameMap.Cell cell : blast.cells()) {
                Bomb chained = bombs.get(cell);
                if (chained != null && !exploded.contains(cell)) {
                    // 예정 시간이 남았어도 연쇄에 걸리면 이번 틱에 터진다.
                    pending.add(chained);
                }
            }
        }
        for (GameMap.Cell cell : exploded) {
            bombs.remove(cell);
            bombPassers.remove(cell);
        }
        for (GameMap.Cell cell : hit) {
            if (map.breakBox(cell)) {
                // 아이템 드롭을 붙이기 전까지는 상자 파괴 때 NONE을 보낸다.
                messages.add(message(MsgType.EVENT, "BOX_BREAK:" + cell.col() + ":" + cell.row() + ":NONE"));
            }
        }
        for (ServerPlayer player : players.values()) {
            if (player.state == PlayerState.ALIVE && hit.stream()
                    .anyMatch(cell -> player.overlaps(cell, player.x, player.y))) {
                player.state = PlayerState.TRAPPED;
                player.direction = Direction.STOP;
                player.trappedUntil = tick + Constants.TRAPPED_TIMEOUT_TICKS;
                messages.add(message(MsgType.EVENT, "TRAPPED:" + player.userId()));
            }
        }
    }

    // 방금 폭탄에 맞아 갇힌 플레이어도 이 틱의 접촉 판정에 포함한다.
    private void resolveTrappedPlayers(List<Message> messages) {
        for (ServerPlayer player : players.values()) {
            if (player.state != PlayerState.TRAPPED) {
                continue;
            }
            // 살아서 움직일 수 있는 상대와 겹치거나 갇힘 시간이 끝나면 탈락한다.
            if (tick >= player.trappedUntil || players.values().stream()
                    .anyMatch(other -> other.state == PlayerState.ALIVE && player.overlaps(other))) {
                player.state = PlayerState.DEAD;
                messages.add(message(MsgType.EVENT, "DEAD:" + player.userId()));
            }
        }
    }

    private Message message(MsgType type, String body) {
        return new Message(type, roomId, 0, body);
    }
}
