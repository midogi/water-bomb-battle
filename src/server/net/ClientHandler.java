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
                    leaveRoom();
                    registry.remove(session);
                    session.close();
                }
            }
    }

    private void handleLobby(Message request) throws LobbyException{
        switch(request.type()) {
            case ROOM_LIST -> reply(MsgType.ROOM_LIST, rooms.listBody());
            case ROOM_CREATE -> rooms.create(request.body(), session).broadcastInfo();
            case ROOM_JOIN -> rooms.join(parseRoomId(request.body()), session).broadcastInfo();
            case ROOM_LEAVE -> {
                leaveRoom();
                reply(MsgType.ROOM_LIST, rooms.listBody());
            }
            case CHARACTER_SELECT -> currentRoom().selectCharacter(userId, parseCharacter(request.body()));
            case MAP_SELECT -> currentRoom().selectMap(userId, parseMap(request.body()));
            case READY -> currentRoom().setReady(userId, parseReady(request.body()));
            case START -> {
                Room room = currentRoom();
                room.startGame(userId);
            }
            case INPUT, BOMB -> currentRoom().submitGameInput(userId, request);
            case ITEM_USE -> currentRoom().useItem(userId, parseItem(request.body()));
            case CHAT -> {
                Room room = currentRoom();
                String text = request.body().strip();
                if(text.isEmpty()) {
                    throw new LobbyException(ErrorCode.EMPTY_CHAT);
                }
                if(text.length() > MAX_CHAT_LENGTH) {
                    throw new LobbyException(ErrorCode.CHAT_TOO_LONG);
                }
                room.broadcast(new Message(MsgType.CHAT, room.id(), 0, userId + ":" + session.nickname() + "|" + text));
            }
                case EMOJI -> {
                    Room room = currentRoom();
                    int emoji = parseEmoji(request.body());
                    room.broadcast(new Message(MsgType.EMOJI, room.id(), 0, userId + ":" + emoji));
                }
                default -> throw new LobbyException(ErrorCode.INVALID_REQUEST);
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
            return ArenaMap.valueOf(body.strip());
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
