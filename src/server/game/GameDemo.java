package server.game;

import common.protocol.Message;
import common.protocol.MsgType;
import java.util.List;

/** 소켓 없이 틱을 직접 진행해 폭발과 탈락 순서를 확인하는 예제다. */
public final class GameDemo {
    private GameDemo() { }

    public static void main(String[] args) {
        GameState state = GameState.arena(3, List.of(1, 2, 3, 4), 4);
        System.out.println(state.initialMessage().toWire());
        try (GameLoop loop = new GameLoop(state, message -> { })) {
            // 헤더 값과 상관없이 세션 ID인 1번 플레이어가 폭탄을 놓는다.
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
