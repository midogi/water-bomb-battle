package server.lobby;

import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import common.Constants;
import server.net.UserSession;

public final class RoomManager {
    private final Map<Integer, Room> rooms = new TreeMap<>();
    private int nextRoomId = 1;

    public synchronized Room create(String title, UserSession host) throws LobbyException {
        if(host.roomId() != Constants.LOBBY_ROOM_ID) {
            throw new LobbyException("ALREADY_IN_ROOM", "이미 방에 있습니다.");
        }
        String name = title.strip();
        if ( name.isEmpty() || name.indexOf('|') >= 0
            ||name.indexOf(':') >= 0 || name.indexOf(',') >= 0) {
                throw new LobbyException("INVALID_TITLE", "방 제목과 구분자를 확인하세요.");
            }
        Room room = new Room(nextRoomId++, name, host);
        rooms.put(room.id(), room);
        return room;        
    }

    public synchronized Room join(int roomId, UserSession session) throws LobbyException {
        if(session.roomId() != Constants.LOBBY_ROOM_ID) {
            throw new LobbyException("ALREADY_IN_ROOM", "이미 방에 들어가 있습니다.");
        }
        Room room = rooms.get(roomId);
        if(room == null) {
            throw new LobbyException("ROOM_NOT_FOUND", "존재하지 않는 방입니다.");
        }
        room.add(session);
        return room;
        
    }

    public synchronized Room leave(UserSession session) {
        Room room = rooms.get(session.roomId());
        if(room == null ){
            return null;
        }
        room.remove(session);
        if(room.isEmpty()){
            rooms.remove(room.id());
        }
        return room;
    }
    public synchronized Room find(int roomId) {
        return rooms.get(roomId);
    }

    public synchronized String listBody() {
        StringJoiner list = new StringJoiner(",");
        for ( Room room : rooms.values()) {
            list.add(room.listEntry());
        }
        return list.toString();
    }
}
