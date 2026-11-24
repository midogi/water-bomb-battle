package tests;

import client.game.GameBoard;
import common.model.ArenaMap;
import common.protocol.ErrorCode;
import common.protocol.ItemState;
import common.protocol.Message;
import common.protocol.MessageCodec;
import common.protocol.MsgType;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import server.lobby.RoomManager;
import server.net.ClientHandler;
import server.net.SessionRegistry;

public final class LobbySelectionTest {
    private LobbySelectionTest() { }

    public static void main(String[] args) throws IOException {
        RoomManager rooms = new RoomManager();
        SessionRegistry sessions = new SessionRegistry();
        try (ServerSocket listener = new ServerSocket(0, 3, InetAddress.getLoopbackAddress());
                Peer host = Peer.open(listener, sessions, rooms, 1, "host");
                Peer guest = Peer.open(listener, sessions, rooms, 2, "guest");
                Peer third = Peer.open(listener, sessions, rooms, 3, "third")) {
            host.send(MsgType.ROOM_CREATE, "Selection test");
            Message created = host.receive(MsgType.ROOM_INFO);
            int roomId = created.roomId();
            check(created.body().equals("1|1:host:true:NONE|TIDE"), "New room uses the default map");
            guest.send(MsgType.ROOM_JOIN, Integer.toString(roomId));
            sharedRoomInfo(host, guest);
            third.send(MsgType.ROOM_JOIN, Integer.toString(roomId));
            sharedRoomInfo(host, guest, third);

            guest.send(MsgType.READY, "true");
            guest.expectError(ErrorCode.CHARACTER_REQUIRED);
            host.send(MsgType.START, "");
            host.expectError(ErrorCode.NOT_ALL_SELECTED);
            guest.send(MsgType.ITEM_USE, "LENS");
            guest.expectError(ErrorCode.NOT_PLAYING);
            guest.send(MsgType.CHARACTER_SELECT, "UNKNOWN");
            guest.expectError(ErrorCode.INVALID_CHARACTER);

            host.send(MsgType.CHARACTER_SELECT, "CRAB");
            sharedRoomInfo(host, guest, third);
            guest.send(MsgType.CHARACTER_SELECT, "PLOVER");
            sharedRoomInfo(host, guest, third);
            guest.send(MsgType.CHARACTER_SELECT, "CRAB");
            guest.expectError(ErrorCode.CHARACTER_TAKEN);
            third.send(MsgType.CHARACTER_SELECT, "TURTLE");
            check(sharedRoomInfo(host, guest, third).contains("2:guest:false:PLOVER"),
                    "Denied selection retains the previous character");
            third.send(MsgType.CHARACTER_SELECT, "NONE");
            check(sharedRoomInfo(host, guest, third).contains("3:third:false:NONE"), "NONE releases the character");
            third.send(MsgType.CHARACTER_SELECT, "TURTLE");
            sharedRoomInfo(host, guest, third);

            guest.send(MsgType.MAP_SELECT, "FROST");
            guest.expectError(ErrorCode.NOT_HOST);
            host.send(MsgType.MAP_SELECT, "UNKNOWN");
            host.expectError(ErrorCode.INVALID_MAP);
            host.send(MsgType.MAP_SELECT, "FROST");
            check(sharedRoomInfo(host, guest, third).endsWith("|FROST"), "Host map is shared with every participant");
            host.send(MsgType.START, "");
            host.expectError(ErrorCode.NOT_ALL_READY);

            guest.send(MsgType.READY, "true");
            sharedRoomInfo(host, guest, third);
            guest.send(MsgType.CHARACTER_SELECT, "NONE");
            guest.expectError(ErrorCode.ALREADY_READY);
            third.send(MsgType.READY, "true");
            String ready = sharedRoomInfo(host, guest, third);
            guest.send(MsgType.START, "");
            guest.expectError(ErrorCode.NOT_HOST);
            host.send(MsgType.START, "");
            check(sharedRoomInfo(host, guest, third).equals(ready), "Final room info is delivered before game init");
            for (Peer view : List.of(host, guest, third)) {
                GameBoard board = GameBoard.parse(view.receive(MsgType.GAME_INIT).body());
                check(ArenaMap.FROST.matches(board.tiles()), "Game initializes with the selected map");
                check(board.players().stream().map(GameBoard.Player::id).toList().equals(List.of(1, 2, 3)),
                        "Game initializes with actual participants");
                Message itemState = view.receive(MsgType.ITEM_STATE);
                check(itemState.userId() == view.id() && ItemState.parse(itemState.body()).inventory().isEmpty(),
                        "Each player receives only their own inventory view");
            }
            guest.send(MsgType.ITEM_USE, "UNKNOWN");
            guest.expectError(ErrorCode.INVALID_ITEM);
            guest.send(MsgType.ITEM_USE, "LENS");
            check(GameBoard.parseState(guest.receive(MsgType.STATE).body()).players().size() == 3,
                    "Using an item with no inventory has no side effect");
            host.send(MsgType.MAP_SELECT, "TIDE");
            host.expectError(ErrorCode.ROOM_PLAYING);
            guest.send(MsgType.CHARACTER_SELECT, "MUDSKIPPER");
            guest.expectError(ErrorCode.ROOM_PLAYING);

            guest.send(MsgType.ROOM_LEAVE, "");
            guest.receive(MsgType.ROOM_LIST);
            third.send(MsgType.ROOM_LEAVE, "");
            third.receive(MsgType.ROOM_LIST);
            check(host.receive(MsgType.RESULT).body().equals("WIN:1"), "Remaining player wins on both exits");
            check(host.receive(MsgType.ROOM_INFO).body().equals("1|1:host:true:CRAB|FROST"),
                    "Room keeps the host's character and map after the match");
            guest.send(MsgType.ROOM_JOIN, Integer.toString(roomId));
            sharedRoomInfo(host, guest);
            guest.send(MsgType.CHARACTER_SELECT, "TURTLE");
            check(sharedRoomInfo(host, guest).contains("2:guest:false:TURTLE"), "Leaving releases the character");
            guest.send(MsgType.ROOM_LEAVE, "");
            guest.receive(MsgType.ROOM_LIST);
            host.receive(MsgType.ROOM_INFO);
            host.send(MsgType.ROOM_LEAVE, "");
            host.receive(MsgType.ROOM_LIST);
        }
        System.out.println("Lobby selection tests passed: validation, shared map, 3-player game start and return");
    }

    private static String sharedRoomInfo(Peer first, Peer... others) throws IOException {
        String info = first.receive(MsgType.ROOM_INFO).body();
        for (Peer peer : others) {
            check(peer.receive(MsgType.ROOM_INFO).body().equals(info), "Room members receive identical selections");
        }
        return info;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Peer(Socket socket, int id, DataInputStream in, DataOutputStream out) implements AutoCloseable {
        static Peer open(ServerSocket listener, SessionRegistry sessions, RoomManager rooms, int id, String name)
                throws IOException {
            Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            socket.setSoTimeout(3000);
            Thread handler = new Thread(new ClientHandler(listener.accept(), id, sessions, rooms));
            handler.setDaemon(true);
            handler.start();
            Peer peer = new Peer(socket, id, new DataInputStream(socket.getInputStream()),
                    new DataOutputStream(socket.getOutputStream()));
            try {
                peer.send(MsgType.LOGIN, name);
                check(peer.receive(MsgType.LOGIN_OK).body().equals(Integer.toString(id)), "Login succeeds");
                return peer;
            } catch (IOException | AssertionError error) {
                peer.close();
                throw error;
            }
        }

        void send(MsgType type, String body) throws IOException {
            MessageCodec.write(out, new Message(type, 0, id, body));
        }

        Message receive(MsgType type) throws IOException {
            for (int i = 0; i < 80; i++) {
                Message message = MessageCodec.read(in);
                if (message == null) break;
                if (message.type() == type) return message;
                if (message.type() != MsgType.STATE && message.type() != MsgType.PLAYER_LEFT) {
                    throw new AssertionError("Expected " + type + ", received " + message);
                }
            }
            throw new AssertionError("No " + type + " received by player " + id);
        }

        void expectError(ErrorCode code) throws IOException {
            check(receive(MsgType.ERROR).body().equals(code.body()), "Expected error " + code);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
