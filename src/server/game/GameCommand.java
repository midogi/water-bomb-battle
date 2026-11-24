package server.game;

import common.model.Direction;
import common.model.ItemType;
import java.util.Objects;

/** 한 틱에서 처리할 참가자 요청이다. */
public sealed interface GameCommand {
    int userId();

    record Input(int userId, Direction direction) implements GameCommand {
        public Input {
            Objects.requireNonNull(direction, "direction");
        }
    }

    record PlaceBomb(int userId) implements GameCommand { }

    record UseItem(int userId, ItemType item) implements GameCommand {
        public UseItem {
            Objects.requireNonNull(item, "item");
        }
    }

    record Leave(int userId) implements GameCommand { }
}
