package client.ui;

import java.util.ArrayList;
import java.util.List;

// 제목이 같은 방도 ID로 구분할 수 있도록 목록 한 줄의 정보를 함께 보관한다.
record RoomSummary(int id, String title, int count, String status) {
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

    @Override
    public String toString() {
        String state = switch (status) {
            case "WAIT" -> "대기 중";
            case "PLAYING" -> "게임 중";
            default -> status;
        };
        return id + "번  " + title + "  ·  " + count + "명  ·  " + state;
    }
}
