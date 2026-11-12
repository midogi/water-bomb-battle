package server.game;

import common.model.Direction;
import java.util.Objects;

/** userId는 소켓 세션에서 확인한 값을 넣습니다. 클라이언트 헤더 값은 사용하지 않습니다. */
public sealed interface GameCommand {
    int userId();

    record Input(int userId, Direction direction) implements GameCommand {
        public Input {
            Objects.requireNonNull(direction, "direction");
        }
    }

    record PlaceBomb(int userId) implements GameCommand { }

    record Leave(int userId) implements GameCommand { }
}
