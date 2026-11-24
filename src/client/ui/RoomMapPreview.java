package client.ui;

import client.game.CoastMap;
import client.game.MapTiles;
import common.Constants;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.*;

/** 대기실의 작은 맵 버튼과 확대 도감. */
final class RoomMapPreview extends JButton {
    private CoastMap selected = CoastMap.TIDE;
    private Consumer<CoastMap> onPick;

    RoomMapPreview() {
        setHorizontalTextPosition(CENTER);
        setVerticalTextPosition(BOTTOM);
        setMargin(new Insets(0, 0, 0, 0));
        setMap(selected, null);
        addActionListener(event -> browse());
    }

    /** 방장에게는 선택 버튼을, 다른 참가자에게는 도감 버튼을 보여 준다. */
    void setMap(CoastMap map, Consumer<CoastMap> onPick) {
        if (map != selected || getIcon() == null) setIcon(icon(map, 7, false));
        selected = map;
        this.onPick = onPick;
        setText(onPick == null ? "맵 도감" : "맵 선택");
        setToolTipText(map.title + " · 지형 확대");
    }

    /** 파괴 후 보기를 선택하면 상자만 바닥으로 그려, 고정 벽 사이에 남는 길을 비교할 수 있다. */
    private void browse() {
        JComboBox<CoastMap> list = new JComboBox<>(CoastMap.values());
        list.setSelectedItem(selected);
        JCheckBox cleared = new JCheckBox("상자를 모두 부순 뒤");
        JLabel picture = new JLabel();
        JLabel detail = new JLabel();
        Runnable refresh = () -> {
            CoastMap map = (CoastMap) list.getSelectedItem();
            picture.setIcon(icon(map, 22, cleared.isSelected()));
            detail.setText(map.description);
        };
        list.addActionListener(event -> refresh.run());
        cleared.addActionListener(event -> refresh.run());
        refresh.run();
        JPanel contents = new JPanel(new BorderLayout(4, 4));
        contents.add(list, BorderLayout.NORTH);
        contents.add(picture, BorderLayout.CENTER);
        JPanel notes = new JPanel(new GridLayout(2, 1));
        notes.add(detail);
        notes.add(cleared);
        contents.add(notes, BorderLayout.SOUTH);
        if (onPick == null) {
            JOptionPane.showMessageDialog(this, contents, "맵 도감", JOptionPane.PLAIN_MESSAGE);
        } else if (JOptionPane.showConfirmDialog(this, contents, "대기실 맵 선택",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION && onPick != null) {
            onPick.accept((CoastMap) list.getSelectedItem());
        }
    }

    /** 실제 게임과 같은 타일을 축소해서 표시한다. 도감에서 상자를 숨겨도 원본 배치는 그대로다. */
    private static ImageIcon icon(CoastMap map, int size, boolean cleared) {
        BufferedImage result = new BufferedImage(Constants.MAP_COLUMNS * size, Constants.MAP_ROWS * size,
                BufferedImage.TYPE_INT_RGB);
        MapTiles art = MapTiles.forMap(map);
        String[] rows = map.rows();
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            for (int y = 0; y < rows.length; y++) {
                for (int x = 0; x < rows[y].length(); x++) {
                    char tile = rows[y].charAt(x);
                    g.drawImage(art.tile(cleared && tile == 'B' ? '.' : tile, x, y),
                            x * size, y * size, size, size, null);
                }
            }
        } finally {
            g.dispose();
        }
        return new ImageIcon(result);
    }
}
