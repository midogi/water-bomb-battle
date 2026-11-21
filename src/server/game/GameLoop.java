package server.game;

import common.Constants;
import common.model.Direction;
import common.protocol.Message;
import common.protocol.MsgType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/** 방 하나의 입력을 모아 정해진 간격으로 게임 상태를 갱신한다. */
public final class GameLoop implements AutoCloseable {
    private static final int MAX_PENDING_COMMANDS = 1024;
    private final GameState state;
    // 소켓에 직접 쓰면서 틱이 밀리지 않도록 송신 큐에 넣는 콜백을 받는다.
    private final Consumer<Message> broadcaster;
    private final ConcurrentLinkedQueue<GameCommand> commands = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile GameState.Snapshot latest;
    private volatile Thread worker;

    public GameLoop(GameState state, Consumer<Message> broadcaster) {
        this.state = Objects.requireNonNull(state, "state");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        latest = state.snapshot();
    }

    /** 요청을 큐에 넣는다. 플레이어는 메시지 헤더 대신 소켓 세션의 ID로 찾는다. */
    public synchronized boolean submit(int authenticatedUserId, Message message) {
        Objects.requireNonNull(message, "message");
        if (closed.get() || latest.finished() || !state.hasPlayer(authenticatedUserId)) {
            return false;
        }
        GameCommand command = switch (message.type()) {
            case INPUT -> new GameCommand.Input(authenticatedUserId, Direction.valueOf(message.body()));
            case BOMB -> {
                requireEmptyBody(message);
                yield new GameCommand.PlaceBomb(authenticatedUserId);
            }
            case ROOM_LEAVE -> {
                requireEmptyBody(message);
                yield new GameCommand.Leave(authenticatedUserId);
            }
            default -> throw new IllegalArgumentException("Not a game command: " + message.type());
        };
        if (pending.incrementAndGet() > MAX_PENDING_COMMANDS) {
            pending.decrementAndGet();
            return false;
        }
        commands.add(command);
        return true;
    }

    /** 퇴장도 입력과 함께 게임 루프에서 처리할 수 있게 큐에 넣는다. */
    public boolean playerLeft(int authenticatedUserId) {
        return submit(authenticatedUserId, new Message(MsgType.ROOM_LEAVE, 0, 0, ""));
    }

    public GameState.Snapshot snapshot() {
        return latest;
    }

    public synchronized void start() {
        if (worker != null || closed.get() || latest.finished()) {
            throw new IllegalStateException("GameLoop cannot be started again");
        }
        // 클라이언트에 맵과 시작 위치를 먼저 보내고 틱을 시작한다.
        broadcaster.accept(state.initialMessage());
        worker = new Thread(this::run, "game-room-loop");
        worker.start();
    }

    /** 시작 전에는 테스트에서 직접 틱을 진행할 수 있다. 시작 후에는 게임 스레드가 호출한다. */
    public synchronized List<Message> advanceOneTick() {
        if (worker != null && Thread.currentThread() != worker) {
            throw new IllegalStateException("Only the game thread may advance a running game");
        }
        if (closed.get() || latest.finished()) {
            return List.of();
        }
        List<GameCommand> batch = new ArrayList<>();
        // 이번 틱을 시작할 때 큐에 있던 입력만 꺼낸다.
        int count = Math.min(pending.get(), MAX_PENDING_COMMANDS);
        for (int i = 0; i < count; i++) {
            GameCommand command = commands.poll();
            if (command == null) {
                break;
            }
            pending.decrementAndGet();
            batch.add(command);
        }
        List<Message> result = state.step(batch);
        latest = state.snapshot();
        return result;
    }

    private void run() {
        long interval = Constants.TICK_MILLIS * 1_000_000L;
        long next = System.nanoTime();
        try {
            while (!closed.get() && !latest.finished()) {
                next += interval;
                LockSupport.parkNanos(Math.max(0, next - System.nanoTime()));
                if (closed.get() || Thread.currentThread().isInterrupted()) {
                    break;
                }
                for (Message message : advanceOneTick()) {
                    broadcaster.accept(message);
                }
                // 처리 시간이 길어졌으면 밀린 틱을 몰아서 돌리지 않고 현재 시각부터 다시 센다.
                next = Math.max(next, System.nanoTime());
            }
        } finally {
            synchronized (this) {
                closed.set(true);
                commands.clear();
                pending.set(0);
            }
        }
    }

    private static void requireEmptyBody(Message message) {
        if (!message.body().isEmpty()) {
            throw new IllegalArgumentException("Expected an empty body");
        }
    }

    @Override
    public synchronized void close() {
        closed.set(true);
        commands.clear();
        pending.set(0);
        Thread thread = worker;
        if (thread != null) {
            // 다음 틱을 기다리는 중이면 깨워 바로 종료하게 한다.
            thread.interrupt();
        }
    }
}
