package tests;

import common.Constants;
import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** 외부 라이브러리 없이 전송 규약의 경계 조건을 확인합니다. */
public final class ProtocolTest {
    private ProtocolTest() {
    }

    public static void main(String[] args) throws IOException {
        Message chat = Message.fromWire("CHAT|3|2|안녕 | 반가워 🎈");
        check(chat.body().equals("안녕 | 반가워 🎈"), "Pipe and UTF-8 payload preserved");
        Message state = Message.fromWire(
                "STATE|3|0|418|1:200:120:ALIVE,2:360:80:TRAPPED|5:3:12");
        check(state.body().split("[|]", 3).length == 3, "STATE fields preserved");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(buffer);
        Message empty = new Message(MsgType.ROOM_LIST, 0, 0, "");
        MessageCodec.write(output, chat);
        MessageCodec.write(output, state);
        MessageCodec.write(output, empty);
        byte[] frames = buffer.toByteArray();
        DataInputStream raw = new DataInputStream(new ByteArrayInputStream(frames));
        check(raw.readInt() == chat.toWire().getBytes(StandardCharsets.UTF_8).length,
                "Length counts bytes, not characters");

        InputStream fragmented = new ByteArrayInputStream(frames) {
            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(1, length));
            }
        };
        DataInputStream input = new DataInputStream(fragmented);
        check(chat.equals(MessageCodec.read(input)), "Fragmented chat frame");
        check(state.equals(MessageCodec.read(input)), "Back-to-back STATE frame");
        check(empty.equals(MessageCodec.read(input)), "Empty body retained");
        check(MessageCodec.read(input) == null, "Clean EOF");

        expectIOException(new byte[] {0, 0}, true);
        expectIOException(Arrays.copyOf(frames, 8), true);
        expectIOException(header(-1), false);
        expectIOException(header(0), false);
        expectIOException(header(Constants.MAX_FRAME_BYTES + 1), false);
        expectIOException(frame("UNKNOWN|0|0|".getBytes(StandardCharsets.UTF_8)), false);
        expectIOException(frame("LOGIN|x|0|name".getBytes(StandardCharsets.UTF_8)), false);
        expectIOException(frame(new byte[] {(byte) 0xC3, 0x28}), false);
        System.out.println("Protocol tests passed: UTF-8, delimiters, framing, EOF, invalid inputs.");
    }

    private static byte[] header(int length) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(length);
        return bytes.toByteArray();
    }

    private static byte[] frame(byte[] body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(body.length);
        output.write(body);
        return bytes.toByteArray();
    }

    private static void expectIOException(byte[] frame, boolean truncated) throws IOException {
        try {
            MessageCodec.read(new DataInputStream(new ByteArrayInputStream(frame)));
            throw new AssertionError("Invalid frame accepted");
        } catch (IOException expected) {
            if (truncated) {
                check(expected instanceof EOFException, "Partial frame reports EOFException");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
