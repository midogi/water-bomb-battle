package client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import client.resource.Avatar;
import client.game.GamePanel.Appearance;
import common.Constants;

// ROOM_INFO 한 건에 담긴 방장과 참가자 목록이다.
record RoomInfo(int roomId, int hostId, List<Member> members) {
    record Member(int id, String nickname, boolean ready, Avatar avatar) { }

    Member member(int id) {
        return members.stream().filter(m -> m.id() == id).findFirst().orElse(null);
    }

    Member owner(Avatar avatar) {
        if (avatar == Avatar.NONE) return null;
        return members.stream().filter(m -> m.avatar() == avatar).findFirst().orElse(null);
    }

    boolean canStart() {
        return members.size() >= Constants.MIN_PLAYERS
                && members.stream().allMatch(m -> (m.id() == hostId || m.ready()) && m.avatar() != Avatar.NONE)
                && members.stream().map(Member::avatar).distinct().count() == members.size();
    }

    Map<Integer, Appearance> appearances() {
        Map<Integer, Appearance> result = new HashMap<>();
        for (Member m : members) result.put(m.id(), new Appearance(m.nickname(), m.avatar()));
        return Map.copyOf(result);
    }

    static RoomInfo parse(int roomId, String body) {
        String[] parts = body.split("[|]", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("방 정보 형식을 확인해 주세요.");
        }
        List<Member> members = new ArrayList<>();
        if (!parts[1].isEmpty()) {
            for (String row : parts[1].split(",")) {
                String[] fields = row.split(":", -1);
                if ((fields.length != 3 && fields.length != 4)
                        || !(fields[2].equals("true") || fields[2].equals("false"))) {
                    throw new IllegalArgumentException("참가자 정보 형식을 확인해 주세요.");
                }
                members.add(new Member(Integer.parseInt(fields[0]), fields[1],
                        Boolean.parseBoolean(fields[2]), fields.length == 4 ? Avatar.valueOf(fields[3]) : Avatar.NONE));
            }
        }
        return new RoomInfo(roomId, Integer.parseInt(parts[0]), List.copyOf(members));
    }
}
