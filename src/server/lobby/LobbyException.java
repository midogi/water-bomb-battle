package server.lobby;

public final class LobbyException extends Exception {
    private final String code;

    public LobbyException(String code, String message){
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
