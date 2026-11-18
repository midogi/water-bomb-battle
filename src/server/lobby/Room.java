package server.lobby;

import common.Constants;
import common.protocol.Message;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.ArrayList;
import java.util.List;
import common.protocol.MsgType;
import server.game.GameLoop;
import server.game.GameState;

import server.net.UserSession;

public final class Room{
    private static final int MOVE_PIXELS_PER_TICK = 4;
    private final int id;
    private final String title;
    private final Map<Integer, UserSession> members = new LinkedHashMap<>();
    private final Set<Integer> ready = new LinkedHashSet<>();
    private int hostId;
    private boolean playing;
    private GameLoop game;
    
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
            throw new LobbyException("ROOM_PLAYING", "게임 중인 방입니다.");
        }
        if (members.size() >= Constants.MAX_PLAYERS) {
            throw new LobbyException("ROOM_FULL", "방이 가득 찼습니다.");
        }
        members.put(session.userId(), session);
        session.setRoomId(id);
    }
    //퇴장
    public synchronized void remove(UserSession session) {
        if( members.remove(session.userId()) == null){ return; }
        ready.remove(session.userId());
        session.setRoomId(Constants.LOBBY_ROOM_ID);
        
        //방장이 나가면 방장 교체( 기준은 들어온 순서 )
        if(session.userId() == hostId && !members.isEmpty()) {
            hostId = members.keySet().iterator().next();
            ready.remove(hostId);
        }
    }

    //준비
    public synchronized void setReady(int userId, boolean value) throws LobbyException{
        if(playing) {
            throw new LobbyException("ROOM_PLAYING", "게임 중에는 준비 상태를 바꿀 수 없습니다.");
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
            throw new LobbyException("NOT_HOST", "방장만 시작할 수 있습니다.");
        }
        if(playing){
            throw new LobbyException("ALREADY_PLAYING", "이미 게임 중 입니다.");
        }
        if(members.size() < Constants.MIN_PLAYERS) {
            throw new LobbyException("NOT_ENOUGH_PLAYERS", "최소 인원은 2명입니다.");
        }
        for( int memberId : members.keySet()) {
            if(memberId != hostId && !ready.contains(memberId)) {
                throw new LobbyException("NOT_ALL_READY","모두 준비되지 않았습니다.");
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

    public synchronized List<Integer> startGame(int userId) throws LobbyException{
        checkStart(userId);
        playing = true;
       return new ArrayList<>(members.keySet());
    }
}