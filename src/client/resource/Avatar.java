package client.resource;

import java.awt.image.BufferedImage;
import java.awt.Color;

// 대기실 카드와 게임판이 같은 캐릭터 코드·그림을 사용한다.
public enum Avatar {
    NONE("선택 안 함", null, Color.WHITE),
    MUDSKIPPER("망둑어", "mudskipper.png", new Color(112, 199, 249)),
    CRAB("소라게", "crab.png", new Color(255, 163, 96)),
    PLOVER("물떼새", "plover.png", new Color(255, 217, 95)),
    TURTLE("거북이", "turtle.png", new Color(138, 224, 154));

    public final String label;
    public final BufferedImage image;
    public final Color nicknameColor;

    Avatar(String label, String file, Color nicknameColor) {
        this.label = label;
        this.nicknameColor = nicknameColor;
        this.image = file == null ? null : GameImages.load(file);
    }
}
