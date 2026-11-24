package tests;

import client.game.CoastMap;
import client.game.GameBoard;
import common.Constants;
import common.model.ArenaMap;
import common.model.Direction;
import common.model.ItemType;
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
import java.util.Random;
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
        run("nine shared maps initialize 2, 3 and 4 real player IDs", GameLogicTest::selectedMapSpawns);
        run("held direction and STOP", GameLogicTest::movement);
        run("walls, boxes and map boundaries block movement", GameLogicTest::collision);
        run("fast movement cannot tunnel through a wall", GameLogicTest::tunneling);
        run("bomb limits and 60-tick fuse", GameLogicTest::fuse);
        run("bomb center tile and owner walk-off", GameLogicTest::walkOff);
        run("wall and box stop blast propagation", GameLogicTest::blastStops);
        run("later bomb detonates in a chain", GameLogicTest::chain);
        run("box break happens once", GameLogicTest::boxBreak);
        run("one crate drop goes to one player", GameLogicTest::itemPickup);
        run("invisibility and lens have separate expiry times", GameLogicTest::visibilityItems);
        run("needle is consumed only when escaping a trap", GameLogicTest::needle);
        run("dart stops at a wall and triggers a clear target early", GameLogicTest::dart);
        run("trap timeout produces one winner", GameLogicTest::trapAndWin);
        run("three-player contact eliminates only the trapped player", GameLogicTest::trappedContact);
        run("contact resolves on the same tick as trapping", GameLogicTest::contactOnExplosion);
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

    private static void selectedMapSpawns() {
        List<Integer> ids = List.of(17, 82, 103, 221);
        for (CoastMap theme : CoastMap.values()) {
            ArenaMap layout = theme.layout();
            for (int count = 2; count <= 4; count++) {
                GameState game = GameState.arena(3, layout, ids.subList(0, count), 4);
                GameBoard board = GameBoard.parse(game.initialMessage().body());
                check(CoastMap.matching(board.tiles()) == theme, "Client and server share the selected layout");
                check(board.players().stream().map(GameBoard.Player::id).toList().equals(ids.subList(0, count)),
                        "GAME_INIT uses the IDs that joined the room");
                Message update = game.step(List.of(new GameCommand.PlaceBomb(ids.get(0)))).stream()
                        .filter(message -> message.type() == MsgType.STATE).findFirst().orElseThrow();
                check(GameBoard.parseState(update.body()).balloons().size() == 1, "Selected map supports placement");
            }
        }
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
                List.of(new ServerPlayer(1, 40, 40), new ServerPlayer(2, 520, 440)), 4, fixedDrops());
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 60) {
            game.step(List.of());
        }
        List<Message> events = game.step(List.of());
        check(events.stream().filter(m -> m.body().equals("BOX_BREAK:2:1:NONE")).count() == 1,
                "One box event");
        check(game.initialMessage().body().charAt(17) == '.', "Destroyed box becomes empty");
    }

    private static void itemPickup() {
        GameState game = crateGame(ItemType.NEEDLE, false);
        GameState.Snapshot beforePickup = game.snapshot();
        check(beforePickup.groundItems().get(new GameMap.Cell(2, 1)) == ItemType.NEEDLE, "Crate drops a needle");
        move(game, 1, Direction.RIGHT);
        move(game, 1, Direction.UP);
        List<Message> events = game.step(List.of(new GameCommand.Input(2, Direction.LEFT)));
        check(events.stream().filter(m -> m.body().equals("ITEM_GET:1:2:1:NEEDLE")).count() == 1,
                "Earlier participant gets the contested item once");
        check(game.snapshot().groundItems().isEmpty() && inventory(game, 1, ItemType.NEEDLE) == 1
                && inventory(game, 2, ItemType.NEEDLE) == 0, "Only one inventory changes");
        check(beforePickup.groundItems().size() == 1 && beforePickup.playerItems().get(1).inventory().isEmpty(),
                "Previous snapshot is unaffected by pickup");
    }

    private static void visibilityItems() {
        String[] rows = mapRows(2, 3, 'B');
        rows[3] = rows[3].substring(0, 4) + 'B' + rows[3].substring(5);
        GameState game = new GameState(3, GameMap.fromRows(rows),
                List.of(new ServerPlayer(1, 120, 120), new ServerPlayer(2, 200, 120)), 40,
                fixedDrops(ItemType.INVISIBILITY, ItemType.LENS));
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        move(game, 1, Direction.UP);
        game.step(List.of());
        move(game, 1, Direction.STOP);
        advanceTo(game, 61);
        game.step(List.of(new GameCommand.Input(1, Direction.LEFT), new GameCommand.Input(2, Direction.LEFT)));
        game.step(List.of(new GameCommand.Input(1, Direction.DOWN), new GameCommand.Input(2, Direction.STOP)));
        game.step(List.of());
        game.step(List.of(new GameCommand.Input(1, Direction.STOP), new GameCommand.UseItem(1, ItemType.INVISIBILITY)));
        long invisibleTick = game.snapshot().tick();
        check(!game.snapshot().canSee(2, 1) && game.snapshot().canSee(1, 1), "Only the opponent loses sight");
        check(GameBoard.parseState(game.snapshot().stateBodyFor(2)).players().stream().noneMatch(p -> p.id() == 1),
                "Opponent does not receive the hidden player's position over the wire");
        check(GameBoard.parseState(game.snapshot().stateBodyFor(1)).players().stream().anyMatch(p -> p.id() == 1),
                "Hidden player still receives their own position");
        check(inventory(game, 1, ItemType.INVISIBILITY) == 0, "Invisibility consumed");
        game.step(List.of(new GameCommand.UseItem(2, ItemType.LENS)));
        check(game.snapshot().canSee(2, 1) && inventory(game, 2, ItemType.LENS) == 0, "Lens reveals hidden opponent");
        check(GameBoard.parseState(game.snapshot().stateBodyFor(2)).players().stream().anyMatch(p -> p.id() == 1),
                "Lens holder receives the hidden player's position");
        advanceTo(game, invisibleTick + 99);
        check(game.snapshot().playerItems().get(1).invisible(), "Invisibility lasts through its last tick");
        game.step(List.of());
        check(!game.snapshot().playerItems().get(1).invisible() && game.snapshot().playerItems().get(2).hasLens(),
                "Invisibility expires before the later lens");
        game.step(List.of());
        check(!game.snapshot().playerItems().get(2).hasLens(), "Lens expires independently");
    }

    private static void needle() {
        GameState game = collectCrateItem(ItemType.NEEDLE, false);
        game.step(List.of(new GameCommand.UseItem(1, ItemType.NEEDLE), new GameCommand.PlaceBomb(1)));
        check(inventory(game, 1, ItemType.NEEDLE) == 1, "An alive player keeps the needle");
        advanceTo(game, game.snapshot().bombs().get(0).explodeAtTick());
        check(player(game, 1).state() == PlayerState.TRAPPED, "Player is trapped before using needle");
        long oldTimeout = game.snapshot().tick() + Constants.TRAPPED_TIMEOUT_TICKS;
        try (GameLoop loop = new GameLoop(game, message -> { })) {
            check(loop.useItem(1, ItemType.NEEDLE), "Preview can queue needle use");
            check(contains(loop.advanceOneTick(), "ITEM_USED:1:NEEDLE"), "Needle announces successful escape");
            check(player(game, 1).state() == PlayerState.ALIVE && inventory(game, 1, ItemType.NEEDLE) == 0,
                    "Needle restores player, then consumes one item");
        }
        advanceTo(game, oldTimeout);
        check(player(game, 1).state() == PlayerState.ALIVE, "Old trap timeout cannot eliminate the escaped player");
    }

    private static void dart() {
        GameState game = collectCrateItem(ItemType.DART, true);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        long scheduled = game.snapshot().bombs().get(0).explodeAtTick();
        move(game, 1, Direction.DOWN);
        move(game, 1, Direction.RIGHT);
        game.step(List.of());
        move(game, 1, Direction.UP);
        List<Message> blocked = game.step(List.of(new GameCommand.Input(1, Direction.LEFT), new GameCommand.Input(1, Direction.STOP),
                new GameCommand.UseItem(1, ItemType.DART)));
        check(inventory(game, 1, ItemType.DART) == 0 && game.snapshot().bombs().size() == 1
                && contains(blocked, "DART_SHOT:1:3:1:BLOCK:LEFT"), "Wall stops a spent dart");
        game = collectCrateItem(ItemType.DART, true);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        scheduled = game.snapshot().bombs().get(0).explodeAtTick();
        move(game, 1, Direction.DOWN);
        move(game, 1, Direction.RIGHT);
        game.step(List.of());
        move(game, 1, Direction.UP);
        move(game, 1, Direction.DOWN);
        move(game, 1, Direction.LEFT);
        game.step(List.of());
        List<Message> hit = game.step(List.of(new GameCommand.Input(1, Direction.UP),
                new GameCommand.Input(1, Direction.STOP), new GameCommand.UseItem(1, ItemType.DART)));
        check(contains(hit, "ITEM_USED:1:DART") && contains(hit, "EXPLODE:2:1:")
                && game.snapshot().tick() < scheduled, "A clear shot uses the normal explosion in the same tick");
        check(inventory(game, 1, ItemType.DART) == 0 && game.snapshot().bombs().isEmpty(), "Dart and bomb consumed");
        game = collectCrateItem(ItemType.DART, false);
        List<Message> miss = game.step(List.of(new GameCommand.Input(1, Direction.RIGHT),
                new GameCommand.Input(1, Direction.STOP), new GameCommand.UseItem(1, ItemType.DART)));
        check(contains(miss, "DART_SHOT:1:7:1:MISS:RIGHT") && !contains(miss, "EXPLODE:")
                && inventory(game, 1, ItemType.DART) == 0, "Empty lane consumes a dart at maximum range");
    }

    private static GameState crateGame(ItemType item, boolean withWall) {
        String[] rows = mapRows(2, 1, 'B');
        if (withWall) {
            rows[1] = rows[1].substring(0, 3) + '#' + rows[1].substring(4);
        }
        int guestX = withWall ? 520 : 120;
        int guestY = withWall ? 440 : 40;
        GameState game = new GameState(3, GameMap.fromRows(rows),
                List.of(new ServerPlayer(1, 40, 40), new ServerPlayer(2, guestX, guestY)), 40, fixedDrops(item));
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        move(game, 1, Direction.DOWN);
        game.step(List.of());
        move(game, 1, Direction.STOP);
        advanceTo(game, 61);
        return game;
    }

    private static GameState collectCrateItem(ItemType item, boolean withWall) {
        GameState game = crateGame(item, withWall);
        move(game, 1, Direction.RIGHT);
        move(game, 1, Direction.UP);
        game.step(List.of());
        move(game, 1, Direction.STOP);
        check(inventory(game, 1, item) == 1, "Fixture collected " + item);
        return game;
    }

    private static Random fixedDrops(ItemType... items) {
        return new Random(0) {
            private int index;

            @Override
            public int nextInt(int bound) {
                return index < items.length ? items[index++].ordinal() : ItemType.values().length;
            }
        };
    }

    private static int inventory(GameState game, int userId, ItemType item) {
        return game.snapshot().playerItems().get(userId).inventory().getOrDefault(item, 0);
    }

    private static void move(GameState game, int userId, Direction direction) {
        game.step(List.of(new GameCommand.Input(userId, direction)));
    }

    private static void advanceTo(GameState game, long target) {
        while (game.snapshot().tick() < target) {
            check(!game.snapshot().finished(), "Game ended before target tick " + target);
            game.step(List.of());
        }
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

    private static void trappedContact() {
        GameState game = new GameState(3, map(-1, -1, '.'), List.of(
                new ServerPlayer(1, 40, 40), new ServerPlayer(2, 120, 40),
                new ServerPlayer(3, 200, 40)), 4);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        while (game.snapshot().tick() < 60) {
            game.step(List.of());
        }
        check(contains(game.step(List.of()), "TRAPPED:1"), "Bomb traps player 1 at tick 61");

        game.step(List.of(new GameCommand.Input(2, Direction.LEFT)));
        while (game.snapshot().tick() < 71) {
            game.step(List.of());
        }
        check(player(game, 2).x() == 80 && player(game, 1).state() == PlayerState.TRAPPED,
                "At tick 71 the rectangles share an edge without overlapping");

        List<Message> contact = game.step(List.of());
        check(player(game, 2).x() == 76 && player(game, 1).state() == PlayerState.DEAD,
                "At tick 72 the first overlap eliminates player 1");
        check(contact.stream().filter(m -> m.type() == MsgType.EVENT && m.body().equals("DEAD:1")).count() == 1,
                "Exactly one death event");
        check(contact.stream().anyMatch(m -> m.type() == MsgType.STATE && m.body().contains("1:40:40:DEAD")),
                "The same tick broadcasts the updated player state");
        check(player(game, 2).state() == PlayerState.ALIVE && player(game, 3).state() == PlayerState.ALIVE
                && contact.stream().noneMatch(m -> m.type() == MsgType.RESULT) && !game.snapshot().finished(),
                "The other two players keep playing");
        check(!contains(game.step(List.of(new GameCommand.Input(2, Direction.STOP))), "DEAD:1"),
                "Continued overlap does not repeat the death event");
    }

    private static void contactOnExplosion() {
        GameState game = new GameState(3, map(-1, -1, '.'), List.of(
                new ServerPlayer(1, 40, 40), new ServerPlayer(2, 80, 40),
                new ServerPlayer(3, 120, 40)), 4);
        game.step(List.of(new GameCommand.PlaceBomb(1)));
        game.step(List.of(new GameCommand.Input(1, Direction.DOWN), new GameCommand.Input(2, Direction.RIGHT)));
        game.step(List.of(new GameCommand.Input(2, Direction.STOP)));
        while (game.snapshot().tick() < 21) {
            game.step(List.of());
        }
        game.step(List.of(new GameCommand.Input(1, Direction.STOP)));
        while (game.snapshot().tick() < 60) {
            game.step(List.of());
        }
        check(player(game, 2).state() == PlayerState.ALIVE && player(game, 2).x() == 84,
                "Overlap with a player who is not trapped has no effect");

        List<String> events = game.step(List.of()).stream().filter(m -> m.type() == MsgType.EVENT)
                .map(Message::body).toList();
        check(events.equals(List.of("EXPLODE:1:1:0:1:0:1", "TRAPPED:2", "DEAD:2")),
                "The explosion traps player 2 and the nearby alive player eliminates them in the same tick");
        check(player(game, 3).state() == PlayerState.ALIVE, "Player 3 stood outside the blast");
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
        return GameMap.fromRows(mapRows(obstacleCol, obstacleRow, obstacle));
    }

    private static String[] mapRows(int obstacleCol, int obstacleRow, char obstacle) {
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
        return rows;
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
