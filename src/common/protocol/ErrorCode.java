package common.protocol;

public enum ErrorCode {
    //로그인
    NOT_LOGGED_IN("Log in first."),
    ALREADY_LOGGED_IN("Already logged in."),
    INVALID_NICK("Nickname must be 1 to 12 characters and cannot contain |, : or comma."),
    DUPLICATE_NICK("Nickname already in use."),

    //방
    INVALID_TITLE("Room title must be 1 to 20 characters and cannot contain |, : or comma."),
    INVALID_ROOM_ID("Invalid room ID."),
    ROOM_NOT_FOUND("Room does not exist."),
    ROOM_FULL("Room is full."),
    ROOM_PLAYING("Action unavailable during a match."),
    ALREADY_IN_ROOM("Already in a room."),
    NOT_IN_ROOM("Not in a room."),
    INVALID_CHARACTER("Invalid character code."),
    CHARACTER_TAKEN("Character already selected by another player."),
    ALREADY_READY("Cancel ready status before changing characters."),
    CHARACTER_REQUIRED("Select a character first."),
    NOT_ALL_SELECTED("All players must select a character."),
    INVALID_MAP("Invalid map code."),

    //준비,시작
    INVALID_READY("Ready must be true or false."),
    NOT_HOST("Only the host can do that."),
    ALREADY_PLAYING("Match already in progress."),
    NOT_ENOUGH_PLAYERS("At least two players are required."),
    NOT_ALL_READY("Not all players are ready."),

    //게임
    NOT_PLAYING("No match in progress."),
    INVALID_INPUT("Invalid input."),
    INVALID_ITEM("Invalid item code."),
    GAME_INPUT_BUSY("Input queue busy. Try again."),

    //채팅
    EMPTY_CHAT("Chat message is empty."),
    CHAT_TOO_LONG("Chat messages are limited to 100 characters."),
    INVALID_EMOJI("Invalid emoji number."),

    //공통
    INVALID_REQUEST("Invalid request."),
    SERVER_ERROR("The server could not process the request.");

    private final String message;

    ErrorCode(String message){
        this.message = message;
    }

    public String message() {return message;}

    public String body() {return name() + ":" + message;}
} 
