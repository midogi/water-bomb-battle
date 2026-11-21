package server.game;

import common.model.PlayerState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Judge {
    private Judge() {
    }

    /** 남은 인원이 한 명이면 승리, 없으면 무승부다. 두 명 이상이면 경기를 계속한다. */
    public static Optional<String> result(Collection<ServerPlayer> players) {
        // 물방울에 갇혀 있어도 탈락하기 전까지는 남은 인원으로 센다.
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
