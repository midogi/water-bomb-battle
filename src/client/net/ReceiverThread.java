package client.net;

import common.protocol.Message;
import java.io.IOException;
import java.util.function.Consumer;

/** Swing 연결 시 onMessage 안에서 invokeLater로 UI 갱신을 넘겨야 합니다. */
public final class ReceiverThread extends Thread {
    private final Connection connection;
    private final Consumer<Message> onMessage;
    private final Consumer<IOException> onError;

    public ReceiverThread(Connection connection, Consumer<Message> onMessage,
            Consumer<IOException> onError) {
        super("server-receiver");
        this.connection = connection;
        this.onMessage = onMessage;
        this.onError = onError;
    }

    @Override
    public void run() {
        try {
            Message message;
            while ((message = connection.receive()) != null) {
                onMessage.accept(message);
            }
        } catch (IOException error) {
            onError.accept(error);
        }
    }
}
