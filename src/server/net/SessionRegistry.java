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

    /** 방에 들어간 세션은 로비 대화의 수신 대상에서 제외한다. */
    public void broadcastLobby(common.protocol.Message message) {
        for (UserSession target : byNickname.values()) {
            if (target.acceptsLobbyChat()) {
                target.send(message);
            }
        }
    }

    /** 한 번의 갱신에 명단과 방 목록을 함께 보낸다. 동시에 여러 변경이 생겨도 송신 순서를 유지한다. */
    public synchronized void publishLobby(server.lobby.RoomManager rooms) {
        var members = byNickname.values().stream().filter(UserSession::acceptsLobbyChat).toList();
        if (members.stream().noneMatch(UserSession::watchesLobby)) return;
        String names = members.stream().map(UserSession::nickname).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        var update = new common.protocol.Message(common.protocol.MsgType.LOBBY_UPDATE,
                common.Constants.LOBBY_ROOM_ID, 0, names + "|" + rooms.listBody());
        for (UserSession member : members) {
            if (member.watchesLobby()) member.send(update);
        }
    }

    public void remove(UserSession session) {
        String nickname = session.nickname();
        if(nickname != null) {
            byNickname.remove(nickname, session);
        }
    }
}
