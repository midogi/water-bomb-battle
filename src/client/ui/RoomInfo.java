package client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import client.resource.Avatar;
import client.game.GamePanel.Appearance;
import common.Constants;
import common.model.ArenaMap;

// ROOM_INFO 한 건에 담긴 방장과 참가자 목록이다.
record RoomInfo(int roomId, int hostId, List<Member> members, ArenaMap map) {
    record Member(int id, String nickname, boolean ready, Avatar avatar) { }

    /** showRoom()이 내 번호로 참가자를 찾을 때 쓴다. 목록에 없는 번호라면 null을 돌려준다. */
    Member member(int id) {
        return members.stream().filter(m -> m.id() == id).findFirst().orElse(null);
    }

    /** 선택 버튼을 갱신할 때 특정 캐릭터를 누가 쓰는지 찾는다. 미선택은 여러 명이 함께 사용할 수 있다. */
    Member owner(Avatar avatar) {
        if (avatar == Avatar.NONE) return null;
        return members.stream().filter(m -> m.avatar() == avatar).findFirst().orElse(null);
    }

    /** 방장 화면의 시작 버튼과 미리보기가 쓴다. 최소 인원, 전원 선택, 선택 중복, 일반 참가자의 준비 여부를 확인한다. */
    boolean canStart() {
        return members.size() >= Constants.MIN_PLAYERS
                && members.stream().allMatch(m -> (m.id() == hostId || m.ready()) && m.avatar() != Avatar.NONE)
                && members.stream().map(Member::avatar).distinct().count() == members.size();
    }

    /** 게임 시작 때 사용자 번호별 닉네임과 캐릭터를 묶어 게임 화면에서 해당 플레이어를 그릴 때 쓰게 한다. */
    Map<Integer, Appearance> appearances() {
        Map<Integer, Appearance> result = new HashMap<>();
        for (Member m : members) result.put(m.id(), new Appearance(m.nickname(), m.avatar()));
        return Map.copyOf(result);
    }

    /** LobbySession이 ROOM_INFO 본문을 방장과 참가자 목록으로 읽을 때 호출한다. 캐릭터 필드가 없으면 미선택으로 채운다. */
    static RoomInfo parse(int roomId, String body) {
        String[] parts = body.split("[|]", -1);
        if (parts.length != 2 && parts.length != 3) {
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
        ArenaMap map = parts.length == 3 ? ArenaMap.valueOf(parts[2]) : ArenaMap.TIDE;
        return new RoomInfo(roomId, Integer.parseInt(parts[0]), List.copyOf(members), map);
    }
}
