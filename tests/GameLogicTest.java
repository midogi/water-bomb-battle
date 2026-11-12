package tests;

import common.Constants;
import common.model.Direction;
import common.model.PlayerState;
import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import server.game.Bomb;
import server.game.ExplosionCalc;
import server.game.GameCommand;
import server.game.GameLoop;
import server.game.GameMap;
import server.game.GameState;
import server.game.ServerPlayer;

public final class GameLogicTest {
    private static int passed;

    private GameLogicTest() { }

    public static void main(String[] args) throws Exception {
        run("map serialization and validation", GameLogicTest::mapValidation);
        run("default 4-player safe spawns", GameLogicTest::defaultSpawns);
        run("held direction and STOP", GameLogicTest::movement);
        run("walls, boxes and map boundaries block movement", GameLogicTest::collision);
        run("fast movement cannot tunnel through a wall", GameLogicTest::tunneling);
        run("bomb limits and 60-tick fuse", GameLogicTest::fuse);
        run("bomb center tile and owner walk-off", GameLogicTest::walkOff);
        run("wall and box stop blast propagation", GameLogicTest::blastStops);
        run("later bomb detonates in a chain", GameLogicTest::chain);
        run("box break happens once", GameLogicTest::boxBreak);
        run("trap timeout produces one winner", GameLogicTest::trapAndWin);
        run("same-tick final deaths produce a draw", GameLogicTest::draw);
        run("same-tick disconnects produce a draw", GameLogicTest::disconnect);
        run("session identity overrides forged headers", GameLogicTest::identity);
        run("invalid inputs and full queue are rejected", GameLogicTest::inputValidation);
        run("room isolation and immutable snapshots", GameLogicTest::isolation);
        run("game messages round-trip through existing codec", GameLogicTest::protocol);
        run("50ms worker starts, publishes and closes", GameLogicTest::worker);
        System.out.println("Game logic tests passed: " + passed + "/" + passed);
    }

    private static void mapValidation() {
        GameMap map = map(-1, -1, '.');
        check(map.toWire().length() == 195, "Map length must be 195");
        check(map.toWire().charAt(16) == '.', "Row-major index");
        expectInvalid(() -> GameMap.fromRows("..."));
        expectInvalid(() -> map(3, 3, 'X'));
        expectInvalid(() -> GameState.arena(1, List.of(1), 4));
        expectInvalid(() -> GameState.arena(1, List.of(1, 1), 4));
        expectInvalid(() -> GameState.arena(1, List.of(1, 2), 0));
    }

    private static void defaultSpawns() {
        GameState game = GameState.arena(3, List.of(10, 20, 30, 40), 4);
        check(game.snapshot().players().size() == 4, "Four players");
        String[] init = game.initialMessage().body().split("[|]", 2);
        check(init[0].length() == 195 && init[1].contains("40:520:440"), "Map and spawns");
    }

    private static void movement() {
        GameState game = game(1, 1, 13, 11);
        game.step(List.of(new GameCommand.Input(1, Direction.RIGHT)));
        check(player(game, 1).x() == 44, "Right input");
        game.step(List.of());
        check(player(game, 1).x() == 48, "Direction remains held");
        game.step(List.of(new GameCommand.Input(1, Direction.STOP)));
        check(player(game, 1).x() == 48, "STOP prevents movement");
    }

    private static void collision() {
        for (char tile : new char[] {'#', 'B'}) {
            GameState game = new GameState(3, map(2, 1, tile),
                    List.of(new ServerPlayer(1, 40, 40), new ServerPlayer(2, 520, 440)), 4);
            game.step(List.of(new GameCommand.Input(1, Direction.RIGHT)));
            check(player(game, 1).x() == 40, "Obstacle blocks full rectangle");
        }
        GameState game = game(1, 1, 13, 11);
        for (int i = 0; i < 20; i++) {
            game.step(List.of(new GameCommand.Input(1, Direction.LEFT)));
        }
        check(player(game, 1).x() == 40, "Map boundary blocks movement");
    }

    private static void tunneling() {
        GameState game = new GameState(3, map(3, 1, '#'),
                List.of(new ServerPlayer(1, 79, 40), new ServerPlayer(2, 520, 440)), 40);
        game.step(List.of(new GameCommand.Input(1, Direction.RIGHT)));
        check(player(game, 1).x() == 80, "Pixel sweep stops before wall");
    }

    private static void fuse() {
        GameState game = game(1, 1, 13, 11);
        game.step(List.of(new GameCommand.PlaceBomb(1), new GameCommand.PlaceBomb(1)));
        check(game.snapshot().bombs().size() == 1, "One bomb per player");
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 60) {
            check(!contains(game.step(List.of()), "EXPLODE:"), "Must not explode early");
        }
        check(game.snapshot().bombs().size() == 1, "Still active at tick 60");
        check(contains(game.step(List.of()), "EXPLODE:1:1:"), "Explodes at placement tick + 60");
        check(game.snapshot().bombs().isEmpty(), "Exploded bomb removed");
    }

    private static void walkOff() {
        GameState game = game(1, 1, 13, 11);
        game.step(List.of(new GameCommand.Input(1, Direction.RIGHT), new GameCommand.PlaceBomb(1)));
        check(game.snapshot().bombs().get(0).col() == 1, "Plant at center tile");
        for (int i = 0; i < 9; i++) {
            game.step(List.of());
        }
        check(player(game, 1).x() == 80, "Owner may leave own bomb");
        game.step(List.of(new GameCommand.Input(1, Direction.LEFT)));
        check(player(game, 1).x() == 80, "Cannot walk back into bomb");
    }

    private static void blastStops() {
        Bomb bomb = new Bomb(1, 1, 1, 4, 60);
        ExplosionCalc.Blast wall = ExplosionCalc.calculate(map(3, 1, '#'), bomb);
        check(wall.right() == 1 && !wall.cells().contains(new GameMap.Cell(3, 1)), "Wall excluded");
        ExplosionCalc.Blast box = ExplosionCalc.calculate(map(3, 1, 'B'), bomb);
        check(box.right() == 2 && box.cells().contains(new GameMap.Cell(3, 1))
                && !box.cells().contains(new GameMap.Cell(4, 1)), "Box included; behind it excluded");
    }

    private static void chain() {
        GameState game = game(1, 1, 2, 1);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 30) {
            game.step(List.of());
        }
        game.step(List.of(new GameCommand.PlaceBomb(2)));
        while (game.snapshot().tick() < 60) {
            game.step(List.of());
        }
        List<Message> messages = game.step(List.of());
        check(messages.stream().filter(m -> m.body().startsWith("EXPLODE:")).count() == 2,
                "Second bomb explodes early in same tick");
        check(game.snapshot().bombs().isEmpty(), "Both bombs removed");
    }

    private static void boxBreak() {
        GameState game = new GameState(3, map(2, 1, 'B'),
                List.of(new ServerPlayer(1, 40, 40), new ServerPlayer(2, 520, 440)), 4);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 60) {
            game.step(List.of());
        }
        List<Message> events = game.step(List.of());
        check(events.stream().filter(m -> m.body().equals("BOX_BREAK:2:1:NONE")).count() == 1,
                "One box event");
        check(game.initialMessage().body().charAt(17) == '.', "Destroyed box becomes empty");
    }

    private static void trapAndWin() {
        GameState game = game(1, 1, 13, 11);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 61) {
            game.step(List.of());
        }
        check(player(game, 1).state() == PlayerState.TRAPPED, "Player trapped");
        int x = player(game, 1).x();
        game.step(List.of(new GameCommand.Input(1, Direction.RIGHT), new GameCommand.PlaceBomb(1)));
        check(player(game, 1).x() == x && game.snapshot().bombs().isEmpty(), "Trapped input ignored");
        while (game.snapshot().tick() < 160) {
            game.step(List.of());
        }
        check(!game.snapshot().finished(), "No early timeout");
        List<Message> result = game.step(List.of());
        check(contains(result, "DEAD:1") && contains(result, "WIN:2"), "Timeout and winner");
        check(game.step(List.of()).isEmpty(), "Result is not repeated");
    }

    private static void draw() {
        GameState game = game(1, 1, 2, 1);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 160) {
            game.step(List.of());
        }
        List<Message> result = game.step(List.of());
        check(result.stream().filter(m -> m.type() == MsgType.RESULT).count() == 1
                && contains(result, "DRAW:1,2"), "One draw for simultaneous final deaths");
    }

    private static void disconnect() {
        GameState game = game(1, 1, 13, 11);
        List<Message> messages = game.step(List.of(new GameCommand.Leave(1), new GameCommand.Leave(2)));
        check(messages.stream().filter(m -> m.type() == MsgType.PLAYER_LEFT).count() == 2,
                "Both disconnections are reported");
        check(contains(messages, "DRAW:1,2"), "Judge after all commands");
    }

    private static void identity() {
        try (GameLoop loop = new GameLoop(game(1, 1, 13, 11), message -> { })) {
            check(loop.submit(1, new Message(MsgType.INPUT, 999, 2, "RIGHT")), "Session input accepted");
            check(!loop.submit(999, new Message(MsgType.BOMB, 3, 1, "")), "Non-member denied");
            loop.advanceOneTick();
            check(loop.snapshot().players().get(0).x() == 44, "Session owner moves");
            check(loop.snapshot().players().get(1).x() == 520, "Forged target does not move");
        }
    }

    private static void inputValidation() {
        try (GameLoop loop = new GameLoop(game(1, 1, 13, 11), message -> { })) {
            expectInvalid(() -> loop.submit(1, new Message(MsgType.INPUT, 3, 1, "TELEPORT")));
            expectInvalid(() -> loop.submit(1, new Message(MsgType.BOMB, 3, 1, "1:1")));
            expectInvalid(() -> loop.submit(1, new Message(MsgType.CHAT, 3, 1, "hello")));
            int accepted = 0;
            for (int i = 0; i < 1100; i++) {
                if (loop.submit(1, new Message(MsgType.INPUT, 3, 1, "STOP"))) {
                    accepted++;
                }
            }
            check(accepted == 1024, "Bounded queue");
            loop.advanceOneTick();
            check(loop.submit(1, new Message(MsgType.INPUT, 3, 1, "STOP")), "Queue drains");
        }
    }

    private static void isolation() {
        GameState first = game(1, 1, 13, 11);
        GameState second = game(1, 1, 13, 11);
        GameState.Snapshot before = first.snapshot();
        first.step(List.of(new GameCommand.Input(1, Direction.RIGHT)));
        check(player(second, 1).x() == 40 && second.snapshot().tick() == 0, "Room isolation");
        check(before.players().get(0).x() == 40, "Snapshot not mutated");
        try {
            before.players().clear();
            throw new AssertionError("Snapshot list is mutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void protocol() throws Exception {
        GameState game = game(1, 1, 13, 11);
        List<Message> messages = new ArrayList<>();
        messages.add(game.initialMessage());
        messages.addAll(game.step(List.of(new GameCommand.PlaceBomb(1))));
        while (game.snapshot().tick() < 161) {
            messages.addAll(game.step(List.of()));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        for (Message message : messages) {
            check(message.roomId() == 3 && message.userId() == 0, "Room broadcast header");
            MessageCodec.write(output, message);
        }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        for (Message expected : messages) {
            check(expected.equals(MessageCodec.read(input)), "Codec preserves game message");
        }
        check(MessageCodec.read(input) == null, "All frames consumed");
    }

    private static void worker() throws Exception {
        CountDownLatch states = new CountDownLatch(3);
        CountDownLatch initialized = new CountDownLatch(1);
        try (GameLoop loop = new GameLoop(game(1, 1, 13, 11), message -> {
            if (message.type() == MsgType.GAME_INIT) initialized.countDown();
            if (message.type() == MsgType.STATE) states.countDown();
        })) {
            loop.start();
            check(initialized.await(1, TimeUnit.SECONDS), "GAME_INIT emitted");
            check(states.await(2, TimeUnit.SECONDS), "Worker emits STATE");
            check(loop.snapshot().tick() >= 3, "Ticks advanced");
            loop.close();
            check(!loop.submit(1, new Message(MsgType.BOMB, 0, 0, "")), "Closed loop rejects input");
        }
    }

    private static GameState game(int c1, int r1, int c2, int r2) {
        return new GameState(3, map(-1, -1, '.'), List.of(
                new ServerPlayer(1, c1 * 40, r1 * 40), new ServerPlayer(2, c2 * 40, r2 * 40)), 4);
    }

    private static GameMap map(int obstacleCol, int obstacleRow, char obstacle) {
        String[] rows = new String[Constants.MAP_ROWS];
        for (int row = 0; row < rows.length; row++) {
            char[] cells = new char[Constants.MAP_COLUMNS];
            Arrays.fill(cells, '.');
            for (int col = 0; col < cells.length; col++) {
                if (row == 0 || row == rows.length - 1 || col == 0 || col == cells.length - 1) cells[col] = '#';
                if (row == obstacleRow && col == obstacleCol) cells[col] = obstacle;
            }
            rows[row] = new String(cells);
        }
        return GameMap.fromRows(rows);
    }

    private static GameState.PlayerView player(GameState game, int userId) {
        return game.snapshot().players().stream().filter(p -> p.userId() == userId).findFirst().orElseThrow();
    }

    private static boolean contains(List<Message> messages, String prefix) {
        return messages.stream().anyMatch(message -> message.body().startsWith(prefix));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectInvalid(Checked action) {
        try {
            action.run();
            throw new AssertionError("Invalid value was accepted");
        } catch (IllegalArgumentException expected) {
            // expected
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void run(String name, Checked test) throws Exception {
        test.run();
        passed++;
        System.out.println("PASS " + passed + " " + name);
    }

    @FunctionalInterface
    private interface Checked {
        void run() throws Exception;
    }
}
