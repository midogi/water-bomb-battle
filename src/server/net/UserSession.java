package server.net;

import common.protocol.Message;
import common.protocol.MessageCodec;
import java.io.DataOutputStream;
import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;



public final class UserSession{
    private static final int QUEUE_CAPACITY = 256;
    
    private final Socket socket;
    private final DataOutputStream output;
    private final int userId;
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private volatile String nickname;
    private volatile int roomId;
    private volatile boolean closed;
    private Thread writer;

    public UserSession(Socket socket, int userId) throws IOException{
        this.socket = socket;
        this.userId = userId;
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    public int userId(){ return userId; }
    public String nickname() {return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname;}
    public boolean isLoggedIn() { return nickname != null; }
    public int roomId() { return roomId; }
    public void setRoomId(int roomId){ this.roomId = roomId;}

    public void start() { 
        writer = new Thread(this::writeLoop, "session-writer-" + userId);
        writer.setDaemon(true);
        writer.start();
     }

    public boolean send(Message message) {
        if (closed) {
            return false;
        }
        if (!outbox.offer(message)){
            close();
            return false;
        }
        return true;
    }
    private void writeLoop() {
        try{
            while(!closed) {
                Message message = outbox.take();
                MessageCodec.write(output, message);
            }
        } catch ( InterruptedException error) {
            //close()가 대기 중인 스레드를 깨운 것이므로 정상 조료
        } catch (IOException error) {
            System.err.println("Client" + userId + " write failed: " + error.getMessage());
        } finally {
            close();
        }
    }

    public void close() {
        if(closed) {
            return;
        }
        closed = true;
        if(writer != null) {
            writer.interrupt();
        }
        try {
            socket.close();
        } catch(IOException error) {
            // 이미 끊긴 연결일 수 있으므로 무시
        } 
    }
}
