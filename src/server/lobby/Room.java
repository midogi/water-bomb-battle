package server.lobby;

import common.Constants;
import common.model.ArenaMap;
import common.model.CharacterType;
import common.model.ItemType;
import common.protocol.ItemState;
import common.protocol.Message;
import common.protocol.MsgType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.ArrayList;
import java.util.Comparator;
import server.game.GameLoop;
import server.game.GameState;
import server.net.UserSession;
import common.protocol.ErrorCode;


public final class Room{
    private final int id;
    private final String title;
    private final Map<Integer, UserSession> members = new LinkedHashMap<>();
    private final Map<Integer, CharacterType> characters = new LinkedHashMap<>();
    private final Set<Integer> ready = new LinkedHashSet<>();
    private final Map<Integer, ItemState> lastItemStates = new LinkedHashMap<>();
    private ArenaMap selectedMap; // null은 경기 시작 때 추첨하는 랜덤 선택이다.
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
        characters.remove(leaverId);
        lastItemStates.remove(leaverId);
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
        broadcastInfo();
    }

    public synchronized void selectCharacter(int userId, CharacterType choice) throws LobbyException {
        checkWaitingMember(userId);
        CharacterType previous = characters.getOrDefault(userId, CharacterType.NONE);
        if (choice != previous) {
            if (userId != hostId && ready.contains(userId)) {
                throw new LobbyException(ErrorCode.ALREADY_READY);
            }
            // 점유 여부를 확인한 뒤에만 바꿔서, 거절돼도 기존 캐릭터는 남는다.
            if (choice != CharacterType.NONE && characters.containsValue(choice)) {
                throw new LobbyException(ErrorCode.CHARACTER_TAKEN);
            }
            if (choice == CharacterType.NONE) {
                characters.remove(userId);
            } else {
                characters.put(userId, choice);
            }
        }
        broadcastInfo();
    }

    public synchronized void selectMap(int userId, ArenaMap map) throws LobbyException {
        checkWaitingMember(userId);
        if (userId != hostId) {
            throw new LobbyException(ErrorCode.NOT_HOST);
        }
        selectedMap = map;
        broadcastInfo();
    }

    //준비
    public synchronized void setReady(int userId, boolean value) throws LobbyException{
        checkWaitingMember(userId);
        if(value){
            if (!characters.containsKey(userId)) {
                throw new LobbyException(ErrorCode.CHARACTER_REQUIRED);
            }
            ready.add(userId);
        } else {
            ready.remove(userId);
        }
        broadcastInfo();
    }

    private void checkWaitingMember(int userId) throws LobbyException {
        if (!members.containsKey(userId)) {
            throw new LobbyException(ErrorCode.NOT_IN_ROOM);
        }
        if (playing) {
            throw new LobbyException(ErrorCode.ROOM_PLAYING);
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
        if (characters.size() != members.size()) {
            throw new LobbyException(ErrorCode.NOT_ALL_SELECTED);
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
            CharacterType character = characters.getOrDefault(member.userId(), CharacterType.NONE);
            list.add(member.userId() + ":" + member.nickname() + ":" + isReady + ":" + character.name());
        }  
        return hostId + "|" + list + "|" + ArenaMap.selectionCode(selectedMap);
    }

    public synchronized void broadcastInfo() {
        broadcast(new Message(MsgType.ROOM_INFO, id, 0, infoBody()));
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
    public void startGame(int userId) throws LobbyException {
        startGame(userId, () -> {});
    }

    public synchronized void startGame(int userId, Runnable afterResult) throws LobbyException{
        checkStart(userId);
        GameState state = GameState.arena(id, ArenaMap.forMatch(selectedMap), new ArrayList<>(members.keySet()), MOVE_PIXELS_PER_TICK);
        GameLoop loop = new GameLoop(state, message -> {
            onGameMessage(message);
            // 현재 방의 잠금을 놓은 뒤 전체 방을 조회해야 다른 방과 서로 기다리지 않는다.
            if (message.type() == MsgType.RESULT) afterResult.run();
        });
        game = loop;
        lastItemStates.clear();
        playing = true;
        // 화면이 캐릭터와 맵을 받은 다음 GAME_INIT으로 넘어가게 한다.
        broadcastInfo();
        loop.start();
    }

    //게임 입력 (INPUT, BOMB)을 게임루프로 전달, userId는 소켓 세션의 ID
    public synchronized void submitGameInput(int userId, Message request) throws LobbyException{
        if(!playing || game == null){
            throw new LobbyException(ErrorCode.NOT_PLAYING);
        }
        try {
            if (!game.submit(userId, request)) {
                throw new LobbyException(ErrorCode.GAME_INPUT_BUSY);
            }
        } catch (IllegalArgumentException error) {
            throw new LobbyException(ErrorCode.INVALID_INPUT);
        }
    }

    public synchronized void useItem(int userId, ItemType item) throws LobbyException {
        if (!playing || game == null) {
            throw new LobbyException(ErrorCode.NOT_PLAYING);
        }
        if (!game.useItem(userId, item)) {
            throw new LobbyException(ErrorCode.GAME_INPUT_BUSY);
        }
    }

    private synchronized void onGameMessage(Message message) {
        if (game == null) return;
        if (message.type() == MsgType.STATE) {
            broadcastGameState(game.snapshot());
            return;
        }
        broadcast(message);
        if (message.type() == MsgType.RESULT) {
            finishGame();
        }
    }

    private void broadcastGameState(GameState.Snapshot snapshot) {
        var ground = snapshot.groundItems().entrySet().stream()
                .map(entry -> new ItemState.GroundItem(entry.getKey().col(), entry.getKey().row(), entry.getValue()))
                .sorted(Comparator.comparingInt(ItemState.GroundItem::row).thenComparingInt(ItemState.GroundItem::col))
                .toList();
        for (UserSession member : members.values()) {
            int userId = member.userId();
            GameState.ItemView view = snapshot.playerItems().get(userId);
            ItemState items = new ItemState(ground, view.inventory(), view.invisible(), view.hasLens());
            // STATE는 바뀔 때마다 보내고, 아이템은 바닥이나 자기 인벤토리가 바뀔 때만 보낸다.
            if (!items.equals(lastItemStates.put(userId, items))) {
                member.send(new Message(MsgType.ITEM_STATE, id, userId, items.toWire()));
            }
            member.send(new Message(MsgType.STATE, id, 0, snapshot.stateBodyFor(userId)));
        }
    }

    private void finishGame() {
        if(game != null) {
            game.close();
            game = null;
        }
        playing = false;
        ready.clear();
        lastItemStates.clear();
        broadcastInfo();
    }

    synchronized void close(){
        if(game != null) {
            game.close();
            game = null;
        }
         playing = false;
    }
}
