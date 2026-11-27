package client.ui;

import common.Constants;
import java.util.ArrayList;
import java.util.List;

// 제목이 같은 방도 ID로 구분할 수 있도록 목록 한 줄의 정보를 함께 보관한다.
record RoomSummary(int id, String title, int count, String status) {
    boolean isJoinable() {
        return status.equals("WAIT") && count < Constants.MAX_PLAYERS;
    }

    /** LobbySession이 ROOM_LIST를 받으면 방 번호, 제목, 인원, 상태로 나눠 로비에 전달할 목록을 만든다. */
    static List<RoomSummary> parseList(String body) {
        List<RoomSummary> rooms = new ArrayList<>();
        if (!body.isEmpty()) {
            for (String row : body.split(",")) {
                String[] fields = row.split(":", -1);
                if (fields.length != 4) {
                    throw new IllegalArgumentException("방 목록 형식을 확인해 주세요.");
                }
                rooms.add(new RoomSummary(Integer.parseInt(fields[0]), fields[1],
                        Integer.parseInt(fields[2]), fields[3]));
            }
        }
        return List.copyOf(rooms);
    }

    /** Swing의 방 목록이 각 항목을 그릴 때 사용할 표시 문구다. 화면에 보이는 문구와 별도로 방 번호는 입장 요청에 쓰인다. */
    @Override
    public String toString() {
        String state = switch (status) {
            case "WAIT" -> isJoinable() ? "입장 가능" : "만석";
            case "PLAYING" -> "게임 중";
            default -> status;
        };
        return id + "번  " + title + "  ·  " + count + "/" + Constants.MAX_PLAYERS + "명  ·  " + state;
    }
}
