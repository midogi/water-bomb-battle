package server.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
}
