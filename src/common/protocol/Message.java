package common.protocol;

import java.util.Objects;

/** body는 추가 |를 포함할 수 있으므로 공통 계층에서 다시 나누지 않습니다. */
public record Message(MsgType type, int roomId, int userId, String body) {
    public Message {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(body, "body");
        if (roomId < 0 || userId < 0) {
            throw new IllegalArgumentException("IDs must be non-negative");
        }
    }

    public String toWire() {
        return type.name() + "|" + roomId + "|" + userId + "|" + body;
    }

    public static Message fromWire(String wire) {
        String[] fields = wire.split("[|]", 4);
        if (fields.length != 4) {
            throw new IllegalArgumentException("Expected TYPE|roomId|userId|body");
        }
        return new Message(MsgType.valueOf(fields[0]),
                Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), fields[3]);
    }
}
