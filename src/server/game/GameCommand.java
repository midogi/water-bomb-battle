package server.game;

import common.model.Direction;
import java.util.Objects;

/** 소켓에서 받은 이동·설치·퇴장 요청을 게임 루프가 처리할 형태로 담는다. */
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
