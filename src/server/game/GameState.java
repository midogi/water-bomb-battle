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

/** 방 하나의 상태. GameLoop 스레드만 변경하며, 외부에는 불변 Snapshot을 전달합니다. */
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
        this.map = map.copy();
        this.movementPixelsPerTick = movementPixelsPerTick;
        for (ServerPlayer participant : participants) {
            ServerPlayer player = new ServerPlayer(participant.userId(), participant.x(), participant.y());
            if (!map.canOccupy(player.x, player.y) || players.putIfAbsent(player.userId(), player) != null) {
                throw new IllegalArgumentException("Invalid spawn or duplicate userId");
            }
            for (ServerPlayer previous : players.values()) {
                if (previous != player && Math.abs(previous.x - player.x) < Constants.TILE_SIZE
                        && Math.abs(previous.y - player.y) < Constants.TILE_SIZE) {
                    throw new IllegalArgumentException("Overlapping spawn positions");
                }
            }
        }
    }

    public static GameState arena(int roomId, List<Integer> userIds, int movementPixelsPerTick) {
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
        return new Snapshot(tick, players.values().stream().map(player ->
                new PlayerView(player.userId(), player.x, player.y, player.state)).toList(),
                List.copyOf(bombs.values()), finished);
    }

    public Message initialMessage() {
        String positions = players.values().stream().map(player ->
                player.userId() + ":" + player.x + ":" + player.y).collect(Collectors.joining(","));
        return message(MsgType.GAME_INIT, map.toWire() + "|" + positions);
    }

    public Message stateMessage() {
        String positions = players.values().stream().map(player ->
                player.userId() + ":" + player.x + ":" + player.y + ":" + player.state)
                .collect(Collectors.joining(","));
        String bombList = bombs.values().stream().map(bomb -> bomb.col() + ":" + bomb.row()
                + ":" + Math.max(0, bomb.explodeAtTick() - tick)).collect(Collectors.joining(","));
        return message(MsgType.STATE, tick + "|" + positions + "|" + bombList);
    }

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
                player.connected = false;
                player.state = PlayerState.DEAD;
                player.direction = Direction.STOP;
                messages.add(message(MsgType.PLAYER_LEFT, Integer.toString(player.userId())));
            } else if (player.state == PlayerState.ALIVE && player.connected) {
                if (command instanceof GameCommand.Input input) {
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
        for (ServerPlayer player : players.values()) {
            if (player.state == PlayerState.TRAPPED && tick >= player.trappedUntil) {
                player.state = PlayerState.DEAD;
                messages.add(message(MsgType.EVENT, "DEAD:" + player.userId()));
            }
        }
        messages.add(stateMessage());
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
                // 아이템 드롭은 미구현이며 NONE은 무드롭을 표시하는 통합 전 임시 값입니다.
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

    private Message message(MsgType type, String body) {
        return new Message(type, roomId, 0, body);
    }
}
