package client.game;

import client.resource.GameImages;
import common.model.ItemType;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import javax.swing.ImageIcon;

/** 바닥과 소지품 칸이 같은 그림을 쓰도록 이미지, 표시 이름, 단축키를 한 곳에 둔다. */
enum ItemArt {
    MIST(ItemType.INVISIBILITY, 1, "투명화", "item-invisibility.png"),
    GLASS(ItemType.LENS, 2, "렌즈", "item-lens.png"),
    PIN(ItemType.NEEDLE, 3, "바늘", "item-needle.png"),
    FIN(ItemType.DART, 4, "다트", "item-dart.png");

    final ItemType type;
    final int key;
    final String name;
    final BufferedImage image;
    final ImageIcon slotIcon;
    private static final EnumMap<ItemType, ItemArt> byType = new EnumMap<>(ItemType.class);
    static {
        for (ItemArt art : values()) byType.put(art.type, art);
    }

    ItemArt(ItemType type, int key, String name, String file) {
        this.type = type;
        this.key = key;
        this.name = name;
        image = GameImages.load(file);
        slotIcon = new ImageIcon(image.getScaledInstance(23, 23, Image.SCALE_SMOOTH));
    }

    static ItemArt forType(ItemType type) {
        return byType.get(type);
    }
}
