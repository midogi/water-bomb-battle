package server.net;

import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import server.lobby.LobbyException;
import server.lobby.Room;
import server.lobby.RoomManager;



public final class ClientHandler implements Runnable{
    private final Socket socket;
    private final int userId;
    private final SessionRegistry registry;
    private UserSession session;
    private final RoomManager rooms;

    public ClientHandler(Socket socket, int userId, SessionRegistry registry, RoomManager rooms){
        this.socket = socket;
        this.userId = userId;
        this.registry = registry;
        this.rooms = rooms;
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
                    leaveRoom();
                    registry.remove(session);
                    session.close();
                }
            }
    }

    private void handleLobby(Message request) throws LobbyException{
        switch(request.type()) {
            case ROOM_LIST -> reply(MsgType.ROOM_LIST, rooms.listBody());
            case ROOM_CREATE -> sendRoomInfo(rooms.create(request.body(), session));
            case ROOM_JOIN -> sendRoomInfo(rooms.join(parseRoomId(request.body()), session));
            case ROOM_LEAVE -> {
                leaveRoom();
                reply(MsgType.ROOM_LIST, rooms.listBody());
            }
            default -> reply(MsgType.ERROR, "NOT_IMPLEMENTED:아직 구현하지 않은 요청입니다.");
            case READY-> {
                Room room = currentRoom();
                room.setReady(userId, parseReady(request.body()));
                sendRoomInfo(room);
            }
            case START -> {
                Room room = currentRoom();
                room.startGame(userId);
            }
        }
    }

    private int parseRoomId(String body) throws LobbyException{
        try{
            return Integer.parseInt(body.strip());
        } catch (NumberFormatException error) {
            throw new LobbyException("INVALID_ROOM_ID", "방 번호를 확인하세요.");
        }
    }
    private void sendRoomInfo(Room room){
        room.broadcast(new Message(MsgType.ROOM_INFO, room.id(), 0, room.infoBody()));
    }

    private void leaveRoom(){
        Room room = rooms.leave(session);
        if( room == null) {
            return;
        }
        room.broadcast(new Message(MsgType.PLAYER_LEFT, room.id(), 0, Integer.toString(userId)));
        sendRoomInfo(room);
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
        } else {
            try {
                handleLobby(request);
            } catch (LobbyException error) {
                reply(MsgType.ERROR, error.code() + ":" + error.getMessage());
            }
        }
    }

    private void reply(MsgType type, String  body){
        session.send(new Message(type, 0, userId, body));
    }

    private Room currentRoom() throws LobbyException {
        Room room = rooms.find(session.roomId());
        if(room == null) {
            throw new LobbyException("NOT_IN_ROOM", "방에 들어가 있지 않습니다.");
        }
        return room;
    }

    private boolean parseReady(String body) throws LobbyException{
        String value = body.strip();
        if(value.equals("true")) {
            return true;
        }
        if(value.equals("false")){
            return false;
        }
        throw new LobbyException("INVALID_READY", "준비 값은 true 또는 false 입니다.");
    }
}