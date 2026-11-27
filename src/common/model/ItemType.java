package common.model;

public enum ItemType {
    INVISIBILITY, LENS, NEEDLE, DART;

    public boolean canUseIn(PlayerState state) {
        return state == (this == NEEDLE ? PlayerState.TRAPPED : PlayerState.ALIVE);
    }
}
