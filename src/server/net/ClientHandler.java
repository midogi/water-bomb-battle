package server.net;

import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

public final class ClientHandler implements Runnable{
    private final Socket socket;
    private final int userId;
    private final SessionRegistry registry;
    private UserSession session;

    public ClientHandler(Socket socket, int userId, SessionRegistry registry){
        this.socket = socket;
        this.userId = userId;
        this.registry = registry;
    }

    @Override
    public void run(){
        try (Socket connection = socket ;
            DataInputStream input = new DataInputStream(connection.getInputStream())){
                session = new UserSession(connection, userId);
                session.start();
                Message request;
                while((request = MessageCodec.read(input)) != null){
                    handle(request);
                }
            } catch (IOException error) {
                System.err.println("Client" + userId + " disconnected" + error.getMessage());
            } finally{
                if (session != null) {
                    registry.remove(session);
                    session.close();
                }
            }
    }

    private void handle(Message request){
        if(request.type() == MsgType.LOGIN) {
            if(session.isLoggedIn()) {
                reply(MsgType.ERROR, "ALREADY_LOGGED_IN:이미 로그인 했습니다.");
                return;
            }
            String candidate = request.body().strip();
            if(candidate.isEmpty() || candidate.indexOf('|') >= 0
                || candidate.indexOf(':') >= 0|| candidate.indexOf(',') >= 0) {
                reply(MsgType.ERROR, "INVALID_NICK:닉네임과 구분자를 확인하세요.");
                return;
            }
            if(!registry.register(candidate, session)) {
                reply(MsgType.ERROR, "DUPLICATE_NICK:이미 사용 중인 닉네임입니다.");
                return;
            }
            reply(MsgType.LOGIN_OK, Integer.toString(userId));
        } else if( !session.isLoggedIn()) {
            reply(MsgType.ERROR, "NOT_LOGGED_IN:먼저 로그인하세요");
        } else if( request.type() == MsgType.ROOM_LIST) {
            reply(MsgType.ROOM_LIST, "");
        } else {
            reply(MsgType.ERROR, "NOT_IMPLEMENTED:아직 구현하지 않은 요청입니다.");
        }
    }

    private void reply(MsgType type, String  body){
        session.send(new Message(type, 0, userId, body));
    }
}