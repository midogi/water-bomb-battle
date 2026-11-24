package server.lobby;

import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import common.Constants;
import server.net.UserSession;
import common.protocol.ErrorCode;

public final class RoomManager {
    private final Map<Integer, Room> rooms = new TreeMap<>();
    private int nextRoomId = 1;
    private static final int MAX_TITLE_LENGTH = 20;

    public synchronized Room create(String title, UserSession host) throws LobbyException {
        if(host.roomId() != Constants.LOBBY_ROOM_ID) {
            throw new LobbyException(ErrorCode.ALREADY_IN_ROOM);
        }
        String name = title.strip();
        if ( name.isEmpty() || name.length() > MAX_TITLE_LENGTH || name.indexOf('|') >=0
        || name.indexOf(':') >=0 || name.indexOf(',')>=0) {
            throw new LobbyException(ErrorCode.INVALID_TITLE);
            }
        Room room = new Room(nextRoomId++, name, host);
        rooms.put(room.id(), room);
        return room;        
    }

    public synchronized Room join(int roomId, UserSession session) throws LobbyException {
        if(session.roomId() != Constants.LOBBY_ROOM_ID) {
            throw new LobbyException(ErrorCode.ALREADY_IN_ROOM);
        }
        Room room = rooms.get(roomId);
        if(room == null) {
            throw new LobbyException(ErrorCode.ROOM_NOT_FOUND);
        }
        room.add(session);
        return room;
    }

    public synchronized Room leave(UserSession session) {
       Room room = rooms.get(session.roomId());
       if(room == null){
        return null;
       }
       room.remove(session);
       if(room.isEmpty()){
        room.close();
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
