package server.game;

import common.protocol.Message;
import common.protocol.MsgType;
import java.util.List;

/** 네트워크 없이 결정적으로 실행하는 게임 로직 시연입니다. */
public final class GameDemo {
    private GameDemo() { }

    public static void main(String[] args) {
        GameState state = GameState.arena(3, List.of(1, 2, 3, 4), 4);
        System.out.println(state.initialMessage().toWire());
        try (GameLoop loop = new GameLoop(state, message -> { })) {
            loop.submit(1, new Message(MsgType.BOMB, 999, 999, ""));
            for (int tick = 1; tick <= 162; tick++) {
                for (Message message : loop.advanceOneTick()) {
                    if (message.type() != MsgType.STATE || tick == 1 || tick % 20 == 0) {
                        System.out.println(message.toWire());
                    }
                }
            }
        }
    }
}
