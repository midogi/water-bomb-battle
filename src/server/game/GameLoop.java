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

/** 방 하나당 인스턴스 하나. broadcaster는 소켓 쓰기 대신 송신 큐 적재 등으로 빨리 반환해야 합니다. */
public final class GameLoop implements AutoCloseable {
    private static final int MAX_PENDING_COMMANDS = 1024;
    private final GameState state;
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

    /** 네트워크 담당은 소켓 세션의 ID를 전달합니다. message의 두 ID 필드는 사용하지 않습니다. */
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
        broadcaster.accept(state.initialMessage());
        worker = new Thread(this::run, "game-room-loop");
        worker.start();
    }

    /** start 전에는 테스트용으로 직접 틱을 진행할 수 있습니다. 실행 중에는 소유 스레드만 호출합니다. */
    public synchronized List<Message> advanceOneTick() {
        if (worker != null && Thread.currentThread() != worker) {
            throw new IllegalStateException("Only the game thread may advance a running game");
        }
        if (closed.get() || latest.finished()) {
            return List.of();
        }
        List<GameCommand> batch = new ArrayList<>();
        // 틱 시작 때 대기하던 수만 처리하여 입력 폭주가 다음 틱을 막지 않도록 합니다.
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
                // 밀린 틱을 한꺼번에 실행하여 상태 패킷이 몰리는 것을 피합니다.
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
            thread.interrupt();
        }
    }
}
