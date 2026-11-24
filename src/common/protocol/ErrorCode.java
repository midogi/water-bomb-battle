package common.protocol;

public enum ErrorCode {
    //로그인
    NOT_LOGGED_IND("먼저 로그인하세요"),
    ALREADY_LOGGED_IN("이미 로그인 했습니다."),
    INVALID_NICK("닉네임은 1~12자이며, | : , 를 사용할 수 없습니다."),
    DUPLICATE_NICK("이미 사용중인 닉네임입니다."),

    //방
    INVALID_TITLE("방 제목은 1~20자이며, | : ,를 쓸 수 없습니다."),
    INVALID_ROOM_ID("방 번호를 확인하세요."),
    ROOM_NOT_FOUND("존재하지 않는 방입니다."),
    ROOM_FULL("방이 가득 찼습니다."),
    ROOM_PLAYING("게임 중에는 할 수 없는 요청입니다."),
    ALREADY_IN_ROOM("이미 방에 들어가 있습니다."),
    NOT_IN_ROOM("방에 들어가 있지 않습니다."),

    //준비,시작
    INVALID_READY("준비 값은 true 혹은 false 입니다."),
    NOT_HOST("방장만 시작할 수 있습니다."),
    ALREADY_PLAYING("이미 게임 중입니다."),
    NOT_ENOUGH_PLAYERS("최소 인원은 2명입니다."),
    NOT_ALL_READY("모두 준비되지 않았습니다."),

    //게임
    NOT_PLAYING("게임 중이 아닙니다."),
    INVALID_INPUT("입력 값을 확인하세요"),

    //채팅
    EMPTY_CHAT("채팅 내용을 입력하세요"),
    CHAT_TOO_LONG("채팅은 100자까지 보낼 수 있습니다."),
    INVALID_EMOJI("이모티콘 번호를 확인하세요"),

    //공통
    INVALID_REQUEST("처리할 수 없는 요청입니다."),
    SERVER_ERROR("서버에서 요청을 처리하지 못 했습니다.");

    private final String message;

    ErrorCode(String message){
        this.message = message;
    }

    public String message() {return message;}

    public String body() {return name() + ":" + message;}
} 
