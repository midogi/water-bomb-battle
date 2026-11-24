package server.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import common.protocol.Message;
import common.Constants;

public class SessionRegistry {
    private final Map<String, UserSession> byNickname = new ConcurrentHashMap<>();
    
    public boolean register(String nickname, UserSession session) {
        if(byNickname.putIfAbsent(nickname, session) != null){
            return false;
        }
        session.setNickname(nickname);
        return true;
    }

    public void remove(UserSession session) {
        String nickname = session.nickname();
        if(nickname != null) {
            byNickname.remove(nickname, session);
        }
    }

    public void broadcastToLobby(Message message) {
        for(UserSession session : byNickname.values()) {
            if(session.roomId() == Constants.LOBBY_ROOM_ID) {
                session.send(message);
            }
        }
    }
}
