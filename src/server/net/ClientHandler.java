package server.net;

import common.model.ArenaMap;
import common.model.CharacterType;
import common.model.ItemType;
import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import server.lobby.LobbyException;
import server.lobby.Room;
import server.lobby.RoomManager;
import common.protocol.ErrorCode;


public final class ClientHandler implements Runnable{
    private final Socket socket;
    private final int userId;
    private final SessionRegistry registry;
    private UserSession session;
    private final RoomManager rooms;
    private static final int MAX_CHAT_LENGTH = 100;
    private static final int EMOJI_COUNT = 8;
    private static final int MAX_NICK_LENGTH = 12;

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
                    registry.remove(session);
                    leaveRoom();
                    if (session.isLoggedIn()) registry.publishLobby(rooms);
                    session.close();
                }
            }
    }

    private void handleLobby(Message request) throws LobbyException{
        switch(request.type()) {
            case LOBBY_WATCH -> {
                session.watchLobby();
                registry.publishLobby(rooms);
            }
            case ROOM_LIST -> reply(MsgType.ROOM_LIST, rooms.listBody());
            case ROOM_CREATE -> {
                rooms.create(request.body(), session).broadcastInfo();
                registry.publishLobby(rooms);
            }
            case ROOM_JOIN -> {
                rooms.join(parseRoomId(request.body()), session).broadcastInfo();
                registry.publishLobby(rooms);
            }
            case ROOM_LEAVE -> {
                leaveRoom();
                reply(MsgType.ROOM_LIST, rooms.listBody());
                registry.publishLobby(rooms);
            }
            case CHARACTER_SELECT -> currentRoom().selectCharacter(userId, parseCharacter(request.body()));
            case MAP_SELECT -> currentRoom().selectMap(userId, parseMap(request.body()));
            case READY -> currentRoom().setReady(userId, parseReady(request.body()));
            case START -> {
                Room room = currentRoom();
                room.startGame(userId, () -> registry.publishLobby(rooms));
                registry.publishLobby(rooms);
            }
            case INPUT, BOMB -> currentRoom().submitGameInput(userId, request);
            case ITEM_USE -> currentRoom().useItem(userId, parseItem(request.body()));
            case CHAT, LOBBY_CHAT -> {
                String text = request.body().strip();
                if (text.isEmpty()) throw new LobbyException(ErrorCode.EMPTY_CHAT);
                if (text.length() > MAX_CHAT_LENGTH) throw new LobbyException(ErrorCode.CHAT_TOO_LONG);
                relayChat(request.type(), userId + ":" + session.nickname() + "|" + text);
            }
            case EMOJI, LOBBY_EMOJI -> {
                int emoji = parseEmoji(request.body());
                // 로비에는 방 명단이 없으므로 서버가 확인한 발신자의 닉네임도 전달한다.
                String body = userId + ":" + emoji;
                if (request.type() == MsgType.LOBBY_EMOJI) body += ":" + session.nickname();
                relayChat(request.type(), body);
            }
                default -> throw new LobbyException(ErrorCode.INVALID_REQUEST);
            }
        }

    /** 요청 종류와 실제 소속을 함께 확인해 방 대화와 로비 대화가 섞이지 않게 한다. */
    private void relayChat(MsgType type, String body) throws LobbyException {
        if (type == MsgType.LOBBY_CHAT || type == MsgType.LOBBY_EMOJI) {
            if (session.roomId() != common.Constants.LOBBY_ROOM_ID) {
                throw new LobbyException(ErrorCode.INVALID_REQUEST);
            }
            registry.broadcastLobby(new Message(type, common.Constants.LOBBY_ROOM_ID, 0, body));
        } else {
            Room room = currentRoom();
            room.broadcast(new Message(type, room.id(), 0, body));
        }
    }

    private int parseRoomId(String body) throws LobbyException{
        try{
            return Integer.parseInt(body.strip());
        } catch (NumberFormatException error) {
            throw new LobbyException(ErrorCode.INVALID_ROOM_ID);
        }
    }
    private void leaveRoom(){
       rooms.leave(session);
    }

    

    private void handle(Message request){
        if(request.type() == MsgType.LOGIN) {
            if(session.isLoggedIn()) {
                replyError(ErrorCode.ALREADY_LOGGED_IN);
                return;
            }
            String candidate = request.body().strip();
            if(candidate.isEmpty() || candidate.length() > MAX_NICK_LENGTH ||
        candidate.indexOf('|') >= 0 || candidate.indexOf(':')>=0 || candidate.indexOf(',')>=0) {
            replyError(ErrorCode.INVALID_NICK);
            return;
        }
            if(!registry.register(candidate, session)) {
                replyError(ErrorCode.DUPLICATE_NICK);
                return;
            }
            reply(MsgType.LOGIN_OK, Integer.toString(userId));
            session.enableLobbyChat();
            registry.publishLobby(rooms);
        } else if( !session.isLoggedIn()) {
            replyError(ErrorCode.NOT_LOGGED_IN);
        } else {
            try {
                handleLobby(request);
            } catch (LobbyException error) {
                replyError(error.code());
            } catch (RuntimeException error) {
                System.err.println("Client" + userId + "request failed: " + request.type() + " " + error);
                replyError(ErrorCode.SERVER_ERROR);
            }   
        }
    }

    private void reply(MsgType type, String  body){
        session.send(new Message(type, 0, userId, body));
    }

    private Room currentRoom() throws LobbyException {
        Room room = rooms.find(session.roomId());
        if(room == null) {
            throw new LobbyException(ErrorCode.NOT_IN_ROOM);
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
        throw new LobbyException(ErrorCode.INVALID_READY);
    }

    private CharacterType parseCharacter(String body) throws LobbyException {
        try {
            return CharacterType.valueOf(body.strip());
        } catch (IllegalArgumentException error) {
            throw new LobbyException(ErrorCode.INVALID_CHARACTER);
        }
    }

    private ArenaMap parseMap(String body) throws LobbyException {
        try {
            return ArenaMap.fromSelection(body.strip());
        } catch (IllegalArgumentException error) {
            throw new LobbyException(ErrorCode.INVALID_MAP);
        }
    }

    private ItemType parseItem(String body) throws LobbyException {
        try {
            return ItemType.valueOf(body.strip());
        } catch (IllegalArgumentException error) {
            throw new LobbyException(ErrorCode.INVALID_ITEM);
        }
    }

    private int parseEmoji(String body) throws LobbyException {
        try{
            int number = Integer.parseInt(body.strip());
            if(number >= 0 && number < EMOJI_COUNT) {
                return number;
            }
        } catch (NumberFormatException error) {

        }
        throw new LobbyException(ErrorCode.INVALID_EMOJI);
    }

    private void replyError(ErrorCode code) {
        reply(MsgType.ERROR,code.body());
    }
}
