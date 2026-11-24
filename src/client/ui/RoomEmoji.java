package client.ui;

import client.resource.Avatar;
import java.awt.Image;
import javax.swing.ImageIcon;

/** 번호만 전송하고 그림은 각 클라이언트에서 찾는다. 목록 순서를 바꿔도 번호는 유지한다. */
enum RoomEmoji {
    HELLO(1, "안녕!", Avatar.MUDSKIPPER),
    NICE(2, "좋아요!", Avatar.CRAB),
    CHEER(3, "파이팅!", Avatar.PLOVER),
    THANKS(4, "고마워요!", Avatar.TURTLE);

    final int number;
    final String label;
    final ImageIcon icon;

    RoomEmoji(int number, String label, Avatar avatar) {
        this.number = number;
        this.label = label;
        this.icon = new ImageIcon(avatar.image.getScaledInstance(32, 32, Image.SCALE_SMOOTH), avatar.label);
    }

    /** 수신한 번호에 해당하는 그림을 찾는다. 목록에 없는 번호는 MainFrame이 텍스트로 표시할 수 있게 null을 돌려준다. */
    static RoomEmoji find(int number) {
        for (RoomEmoji emoji : values()) {
            if (emoji.number == number) return emoji;
        }
        return null;
    }
}
