package client;

import client.net.Connection;
import client.net.ReceiverThread;
import common.Constants;
import common.protocol.Message;
import common.protocol.MsgType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 네트워크·로비 기능을 시험하는 콘솔 클라이언트. 명령어를 입력하면 서버로 메시지를 보낸다. */
public final class ClientMain {
    private static final Set<String> DIRECTIONS = Set.of("UP", "DOWN", "LEFT", "RIGHT", "STOP");

    private static final CountDownLatch loginDone = new CountDownLatch(1); // 로그인 응답(성공·실패)이 오면 0이 됨
    private static volatile int myUserId = 0;          // 서버가 배정한 ID. 수신 스레드가 쓰고 메인 스레드가 읽으므로 volatile
    private static volatile boolean closing = false;   // 사용자가 직접 종료하는 중인지 표시
    private static volatile boolean showState = false; // STATE는 50ms마다 오므로 기본으로 숨김

    private ClientMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        String host = args.length > 0 ? args[0] : Constants.DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Constants.PORT;
        String nickname = args.length > 2 ? args[2] : "player" + new Random().nextInt(10000);

        try (Connection connection = new Connection(host, port)) {
            ReceiverThread receiver = new ReceiverThread(connection,
                    ClientMain::handleMessage,
                    () -> {
                        if (!closing) {
                            System.out.println("서버가 연결을 종료했습니다. Enter를 누르면 끝납니다.");
                        }
                    },
                    error -> {
                        if (!closing) { // 내가 종료한 경우의 "Socket closed"는 출력하지 않음
                            System.err.println("연결 오류: " + error.getMessage());
                        }
                    });
            receiver.setDaemon(true);
            receiver.start();

            // 1. 로그인 후 응답을 기다린다 (3초 안에 응답이 없으면 종료)
            connection.send(new Message(MsgType.LOGIN, Constants.LOBBY_ROOM_ID, 0, nickname));
            if (!loginDone.await(3, TimeUnit.SECONDS) || myUserId == 0) {
                System.out.println("로그인하지 못했습니다.");
                closing = true;
                return;
            }

            // 2. 로그인에 성공하면 방 목록을 요청하고 명령어를 받는다
            connection.send(new Message(MsgType.ROOM_LIST, Constants.LOBBY_ROOM_ID, myUserId, ""));
            printHelp();

            BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.equals("/quit")) {
                    break;
                }
                if (line.equals("/help")) {
                    printHelp();
                    continue;
                }
                if (line.equals("/state")) {
                    showState = !showState;
                    System.out.println("STATE 출력: " + (showState ? "켬" : "끔"));
                    continue;
                }
                Message request = parseCommand(line);
                if (request == null) {
                    System.out.println("명령어 형식이 올바르지 않습니다. /help로 확인하세요.");
                    continue;
                }
                try {
                    connection.send(request);
                } catch (IOException error) {
                    System.err.println("전송 실패: " + error.getMessage());
                    break;
                }
            }
            closing = true; // try-with-resources가 소켓을 닫기 전에 표시해야 함
        }
    }

    /** 수신 스레드에서 호출된다. 여기서 예외가 나면 수신 스레드가 죽으므로 형식 오류는 직접 처리한다. */
    private static void handleMessage(Message message) {
        try {
            switch (message.type()) {
                case LOGIN_OK -> {
                    myUserId = Integer.parseInt(message.body());
                    System.out.println("로그인 성공 (내 ID: " + myUserId + ")");
                    loginDone.countDown();
                }
                case ERROR -> {
                    String[] parts = message.body().split(":", 2); // 코드:메시지
                    System.out.println("[에러] " + parts[0] + (parts.length > 1 ? " - " + parts[1] : ""));
                    loginDone.countDown(); // 로그인 전 에러라면 main의 대기를 풀어 줌 (이후에는 영향 없음)
                }
                case ROOM_LIST -> printRoomList(message.body());
                case ROOM_INFO -> printRoomInfo(message.roomId(), message.body());
                case CHAT -> {
                    String[] parts = message.body().split("[|]", 2); // userId:닉네임 | 메시지
                    String[] sender = parts[0].split(":", 2);
                    String name = sender.length > 1 ? sender[1] : parts[0];
                    System.out.println("[채팅] " + name + ": " + (parts.length > 1 ? parts[1] : ""));
                }
                case EMOJI -> {
                    String[] parts = message.body().split(":", 2); // userId:이모티콘번호
                    System.out.println("[이모티콘] " + parts[0] + "번 플레이어 → " + parts[1] + "번");
                }
                case PLAYER_LEFT -> System.out.println("[퇴장] " + message.body() + "번 플레이어가 나갔습니다.");
                case RESULT -> System.out.println("[결과] " + message.body());
                case STATE -> {
                    if (showState) {
                        System.out.println("< " + message.toWire());
                    }
                }
                default -> System.out.println("< " + message.toWire()); // GAME_INIT, EVENT 등은 그대로 출력
            }
        } catch (RuntimeException error) {
            System.out.println("[형식 오류] " + message.toWire());
        }
    }

    /** 본문 형식: roomId:제목:인원:상태,... */
    private static void printRoomList(String body) {
        if (body.isEmpty()) {
            System.out.println("[방 목록] 만들어진 방이 없습니다.");
            return;
        }
        System.out.println("[방 목록]");
        for (String room : body.split(",")) {
            String[] f = room.split(":", -1);
            if (f.length != 4) {
                System.out.println("  (형식 오류) " + room);
                continue;
            }
            System.out.printf("  #%s %s (%s명, %s)%n", f[0], f[1], f[2], f[3]);
        }
    }

    /** 본문 형식: 방장ID|userId:닉네임:준비,... */
    private static void printRoomInfo(int roomId, String body) {
        String[] parts = body.split("[|]", 2);
        System.out.println("[방 " + roomId + " 정보] 방장: " + parts[0]);
        if (parts.length < 2 || parts[1].isEmpty()) {
            return;
        }
        for (String player : parts[1].split(",")) {
            String[] f = player.split(":", -1);
            if (f.length == 3) {
                System.out.printf("  %s번 %s%s%n", f[0], f[1], Boolean.parseBoolean(f[2]) ? " (준비)" : "");
            }
        }
    }

    /** 입력 한 줄을 서버로 보낼 Message로 바꾼다. 형식이 틀리면 null. */
    private static Message parseCommand(String line) {
        // roomId·userId는 서버가 소켓의 세션 정보로 판단하므로 참고용 값만 채운다
        int userId = myUserId;
        int roomId = Constants.LOBBY_ROOM_ID;

        if (!line.startsWith("/")) { // 일반 문장은 채팅
            return new Message(MsgType.CHAT, roomId, userId, line);
        }

        String[] parts = line.split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].strip() : "";

        try {
            return switch (command) {
                case "/list" -> new Message(MsgType.ROOM_LIST, roomId, userId, "");
                case "/create" -> {
                    // 제목의 : 와 , 는 ROOM_LIST 형식을 깨뜨리므로 막는다 (처리 방법은 팀 합의 필요)
                    if (arg.isEmpty() || arg.contains(":") || arg.contains(",")) {
                        yield null;
                    }
                    yield new Message(MsgType.ROOM_CREATE, roomId, userId, arg);
                }
                case "/join" -> {
                    int target = Integer.parseInt(arg);
                    yield target > 0 ? new Message(MsgType.ROOM_JOIN, roomId, userId, Integer.toString(target)) : null;
                }
                case "/leave" -> new Message(MsgType.ROOM_LEAVE, roomId, userId, "");
                case "/ready" -> {
                    String value = arg.toLowerCase(Locale.ROOT);
                    yield value.equals("true") || value.equals("false")
                            ? new Message(MsgType.READY, roomId, userId, value) : null;
                }
                case "/start" -> new Message(MsgType.START, roomId, userId, "");
                case "/move" -> {
                    String direction = arg.toUpperCase(Locale.ROOT);
                    yield DIRECTIONS.contains(direction)
                            ? new Message(MsgType.INPUT, roomId, userId, direction) : null;
                }
                case "/bomb" -> new Message(MsgType.BOMB, roomId, userId, "");
                case "/emoji" -> {
                    int number = Integer.parseInt(arg);
                    yield number >= 0 ? new Message(MsgType.EMOJI, roomId, userId, Integer.toString(number)) : null;
                }
                default -> null;
            };
        } catch (NumberFormatException error) {
            return null; // /join abc 같은 입력
        }
    }

    private static void printHelp() {
        System.out.println("""
                ---- 명령어 ----
                /list                 방 목록 요청
                /create 제목          방 만들기
                /join 방번호          방 입장
                /leave                방 나가기
                /ready true|false     준비 / 준비 해제
                /start                게임 시작 (방장)
                /move UP|DOWN|LEFT|RIGHT|STOP
                /bomb                 물폭탄 설치
                /emoji 번호           이모티콘
                /state                STATE 출력 켜기/끄기
                /help                 도움말
                /quit                 종료
                그 외 문장            채팅
                ---------------""");
    }
}
