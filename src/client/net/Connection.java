package client.net;

import common.protocol.Message;
import common.protocol.MessageCodec;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public final class Connection implements Closeable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    public Connection(String host, int port) throws IOException {
        socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setTcpNoDelay(true);
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
        } catch (IOException error) {
            try {
                socket.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    public void send(Message message) throws IOException {
        MessageCodec.write(output, message);
    }

    public Message receive() throws IOException {
        return MessageCodec.read(input);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
