package server.game;

import common.model.PlayerState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Judge {
    private Judge() {
    }

    /** 한 틱의 모든 피격·탈락 처리가 끝난 뒤 한 번만 호출합니다. */
    public static Optional<String> result(Collection<ServerPlayer> players) {
        List<ServerPlayer> remaining = players.stream()
                .filter(player -> player.state() != PlayerState.DEAD).toList();
        if (remaining.size() == 1) {
            return Optional.of("WIN:" + remaining.get(0).userId());
        }
        if (remaining.isEmpty()) {
            return Optional.of("DRAW:" + players.stream().map(player -> Integer.toString(player.userId()))
                    .collect(Collectors.joining(",")));
        }
        return Optional.empty();
    }
}
