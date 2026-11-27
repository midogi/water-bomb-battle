package client.resource;

import common.Constants;
import java.awt.image.BufferedImage;
import java.awt.Color;

// 대기실 카드와 게임판이 같은 캐릭터 코드·그림을 사용한다.
public enum Avatar {
    // 미선택은 전용 그림 파일이 없어 대기실에서 물음표 아이콘으로 표시한다.
    NONE("선택 안 함", null, Color.WHITE),
    MUDSKIPPER("망둑어", "mudskipper.png", new Color(112, 199, 249)),
    CRAB("소라게", "crab.png", new Color(255, 163, 96)),
    PLOVER("물떼새", "plover.png", new Color(255, 217, 95)),
    TURTLE("거북이", "turtle.png", new Color(138, 224, 154));

    /** 현재 캐릭터별 능력치는 없어서 서버와 같은 공통값을 보여 준다. */
    public String statHint() {
        if (this == NONE) return label;
        return "공통 능력치 · 물폭탄 " + Constants.DEFAULT_BOMB_LIMIT + "개 · 물줄기 방향당 "
                + Constants.DEFAULT_BOMB_RANGE + "칸 · 속도 " + Constants.DEFAULT_MOVE_PIXELS_PER_TICK + "px/틱";
    }

    public final String label;
    public final BufferedImage image;
    public final Color nicknameColor;
    private final BufferedImage side;
    private final BufferedImage back;

    /** 좌우는 같은 옆모습을 사용하고, 왼쪽 반전은 그리는 쪽에서 처리한다. */
    public BufferedImage facing(common.model.Direction direction) {
        return switch (direction) {
            case LEFT, RIGHT -> side;
            case UP -> back;
            default -> image;
        };
    }

    /** 캐릭터 종류마다 대기실에 표시할 이름, 게임에서 쓸 그림, 닉네임 색을 한 번 정한다. 미선택은 그림 파일이 없다. */
    Avatar(String label, String file, Color nicknameColor) {
        this.label = label;
        this.nicknameColor = nicknameColor;
        this.image = file == null ? null : GameImages.load(file);
        String stem = file == null ? "" : file.substring(0, file.length() - 4);
        side = file == null ? null : GameImages.load(stem + "-side.png");
        back = file == null ? null : GameImages.load(stem + "-back.png");
    }
}
