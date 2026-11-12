package client.net;

import common.protocol.Message;
import java.io.IOException;
import java.util.function.Consumer;

/** Swing 연결 시 onMessage 안에서 invokeLater로 UI 갱신을 넘겨야 합니다. */
public final class ReceiverThread extends Thread {
    private final Connection connection;
    private final Consumer<Message> onMessage;
    private final Runnable onClose;
    private final Consumer<IOException> onError;

    /** 정상 종료 알림이 필요 없는 기존 코드용 생성자 */
    public ReceiverThread(Connection connection, Consumer<Message> onMessage,
            Consumer<IOException> onError) {
        this(connection, onMessage, () -> { }, onError);
    }

    public ReceiverThread(Connection connection, Consumer<Message> onMessage,
                Runnable onClose, Consumer<IOException> onError) {
        super("server-receiver");
        this.connection = connection;
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.onError = onError;
    }

    @Override
    public void run() {
        try {
            Message message;
            while ((message = connection.receive()) != null) {
                onMessage.accept(message);
            }
            onClose.run();
        } catch (IOException error) {
            onError.accept(error);
        }
    }
}
