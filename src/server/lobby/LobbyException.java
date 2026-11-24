package server.lobby;

import common.protocol.ErrorCode;

public final class LobbyException extends Exception{
    private final ErrorCode code;

    public LobbyException(ErrorCode code){
        super(code.message());
        this.code = code;
    }

    public ErrorCode code() {return code; }
}
