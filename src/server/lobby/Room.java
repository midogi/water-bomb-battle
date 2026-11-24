package server.lobby;

import common.Constants;
import common.protocol.Message;
import common.protocol.MsgType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.ArrayList;
import server.game.GameLoop;
import server.game.GameState;
import server.net.UserSession;
import common.protocol.ErrorCode;


public final class Room{
    private final int id;
    private final String title;
    private final Map<Integer, UserSession> members = new LinkedHashMap<>();
    private final Set<Integer> ready = new LinkedHashSet<>();
    private int hostId;
    private boolean playing;
    private GameLoop game;

    private static final int MOVE_PIXELS_PER_TICK = 4;

    Room(int id, String title, UserSession host){
        this.id = id;
        this.title = title;
        this.hostId = host.userId();
        members.put(host.userId(), host);
        host.setRoomId(id);
    }

    public int id() { return id; }
    public synchronized boolean isEmpty() { return members.isEmpty(); }
    
    //입장
    public synchronized void add(UserSession session) throws LobbyException{
        if ( playing ) {
            throw new LobbyException(ErrorCode.ROOM_PLAYING);
        }
        if (members.size() >= Constants.MAX_PLAYERS) {
            throw new LobbyException(ErrorCode.ROOM_FULL);
        }
        members.put(session.userId(), session);
        session.setRoomId(id);
    }
    //퇴장
    public synchronized void remove(UserSession session) {
        int leaverId = session.userId();
        if(members.remove(leaverId) == null) {return;}
        ready.remove(leaverId);
        session.setRoomId(Constants.LOBBY_ROOM_ID);

        if(leaverId == hostId && !members.isEmpty()) {
            hostId = members.keySet().iterator().next();
            ready.remove(hostId);
        }

        if(playing && game != null && game.playerLeft(leaverId)){
            return;
        }
        broadcast(new Message(MsgType.PLAYER_LEFT, id, 0, Integer.toString(leaverId)));
        broadcast(new Message(MsgType.ROOM_INFO, id, 0, infoBody()));
    }

    //준비
    public synchronized void setReady(int userId, boolean value) throws LobbyException{
        if(playing) {
            throw new LobbyException(ErrorCode.ROOM_PLAYING);
        }
        if(!members.containsKey(userId)) {
            return;
        }
        if(value){
            ready.add(userId);
        } else {
            ready.remove(userId);
        }
    }
    
    //시작
    public synchronized void checkStart(int userId) throws LobbyException {
        if (userId != hostId) {
            throw new LobbyException(ErrorCode.NOT_HOST);
        }
        if(playing){
            throw new LobbyException(ErrorCode.ALREADY_PLAYING);
        }
        if(members.size() < Constants.MIN_PLAYERS) {
            throw new LobbyException(ErrorCode.NOT_ENOUGH_PLAYERS);
        }
        for( int memberId : members.keySet()) {
            if(memberId != hostId && !ready.contains(memberId)) {
                throw new LobbyException(ErrorCode.NOT_ALL_READY);
            }
        }
    }

    //메시지 본문 만들기
    synchronized String listEntry() {
        return id + ":" + title + ":" + members.size() + ":" + (playing ? "PLAYING" : "WAIT");
    }
    public synchronized String infoBody() {
        StringJoiner list = new StringJoiner(",");
        for(UserSession member : members.values()) {
            boolean isReady = member.userId() == hostId || ready.contains(member.userId());
            list.add(member.userId() + ":" + member.nickname() + ":" + isReady);
        }  
        return hostId + "|" + list;
    }

    public synchronized void broadcast(Message message){
        for ( UserSession member : members.values()) {
            member.send(message);
        }
    }

    public synchronized void start() {
        playing = true;
    }

    public synchronized void finish(){
        playing = false;
        ready.clear();
    }

    //게임 시작 : 검사 -> GameState/GameLoop 생성 -> GAME_INIT전송
    public synchronized void startGame(int userId) throws LobbyException{
        checkStart(userId);
        GameState state = GameState.arena(id, new ArrayList<>(members.keySet()), MOVE_PIXELS_PER_TICK);
        GameLoop loop = new GameLoop(state, this::onGameMessage);
        game = loop;
        playing = true;
        loop.start();
    }

    //게임 입력 (INPUT, BOMB)을 게임루프로 전달, userId는 소켓 세션의 ID
    public synchronized void submitGameInput(int userId, Message request) throws LobbyException{
        if(!playing || game == null){
            throw new LobbyException(ErrorCode.NOT_PLAYING);
        }
        try {
            game.submit(userId, request);
        } catch (IllegalArgumentException error) {
            throw new LobbyException(ErrorCode.INVALID_INPUT);
        }
    }

    private synchronized void onGameMessage(Message message){
        broadcast(message);
            if(message.type() == MsgType.RESULT) {
                finishGame();
            }
    }

    private void finishGame() {
        if(game != null) {
            game.close();
            game = null;
        }
        playing = false;
        ready.clear();
        broadcast(new Message(MsgType.ROOM_INFO, id, 0, infoBody()));
    }

    synchronized void close(){
        if(game != null) {
            game.close();
            game = null;
        }
         playing = false;
    }
}
