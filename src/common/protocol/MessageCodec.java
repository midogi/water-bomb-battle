package common.protocol;

import common.Constants;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class MessageCodec {
    private MessageCodec() {
    }

    public static void write(DataOutputStream output, Message message) throws IOException {
        byte[] bytes = message.toWire().getBytes(StandardCharsets.UTF_8);
        checkLength(bytes.length);
        synchronized (output) {
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
        }
    }

    /** 정상적인 프레임 경계의 EOF만 null로 반환하고, 잘린 프레임은 예외로 처리합니다. */
    public static Message read(DataInputStream input) throws IOException {
        int first = input.read();
        if (first == -1) {
            return null;
        }
        int length = (first << 24) | (input.readUnsignedByte() << 16)
                | (input.readUnsignedByte() << 8) | input.readUnsignedByte();
        checkLength(length);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        try {
            String wire = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return Message.fromWire(wire);
        } catch (IllegalArgumentException | CharacterCodingException error) {
            throw new IOException("Invalid message", error);
        }
    }

    private static void checkLength(int length) throws IOException {
        if (length <= 0 || length > Constants.MAX_FRAME_BYTES) {
            throw new IOException("Invalid frame length: " + length);
        }
    }
}
