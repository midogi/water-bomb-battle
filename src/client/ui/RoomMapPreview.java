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
    private CoastMap selected;
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
        if (map != selected || getIcon() == null) setIcon(icon(map, 7));
        selected = map;
        this.onPick = onPick;
        getAccessibleContext().setAccessibleName(onPick == null ? "맵 도감" : "맵 선택");
        setToolTipText(map == null ? "랜덤 맵 · 경기 시작 시 추첨" : map.title + " · 지형 확대");
    }

    /** 처음부터 전체 맵 크기만큼 자리를 확보해 물음표에서 다른 맵으로 바꿔도 버튼이 밀리지 않는다. */
    private void browse() {
        CoastMap[] maps = CoastMap.values();
        JComboBox<String> list = new JComboBox<>();
        list.addItem("랜덤 맵");
        for (CoastMap map : maps) list.addItem(map.title);
        list.setSelectedIndex(selected == null ? 0 : selected.ordinal() + 1);

        int tileSize = previewTileSize();
        JLabel picture = new JLabel("", SwingConstants.CENTER);
        picture.setPreferredSize(new Dimension(Constants.MAP_COLUMNS * tileSize, Constants.MAP_ROWS * tileSize));
        Runnable refresh = () -> picture.setIcon(icon(list.getSelectedIndex() == 0
                ? null : maps[list.getSelectedIndex() - 1], tileSize));
        list.addActionListener(event -> refresh.run());
        refresh.run();

        JPanel contents = new JPanel(new BorderLayout(0, 6));
        contents.add(list, BorderLayout.NORTH);
        contents.add(picture, BorderLayout.CENTER);
        boolean host = onPick != null;
        String[] buttons = host ? new String[] {"선택", "취소"} : new String[] {"닫기"};
        int answer = JOptionPane.showOptionDialog(this, contents, host ? "맵 선택" : "맵 도감",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[0]);
        if (host && answer == 0 && onPick != null) {
            onPick.accept(list.getSelectedIndex() == 0 ? null : maps[list.getSelectedIndex() - 1]);
        }
    }

    /** 현재 모니터의 작업 표시줄과 창 장식, 목록, 버튼에 필요한 여백을 남기고 타일 크기를 정한다. */
    private int previewTileSize() {
        GraphicsConfiguration screen = getGraphicsConfiguration();
        Rectangle bounds = screen.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(screen);
        int width = bounds.width - insets.left - insets.right - 100;
        int height = bounds.height - insets.top - insets.bottom - 180;
        return Math.max(1, Math.min(22, Math.min(width / Constants.MAP_COLUMNS, height / Constants.MAP_ROWS)));
    }

    /** 대기실과 도감 모두 경기 시작 시 보이는 원래 배치를 사용한다. */
    private static Icon icon(CoastMap map, int size) {
        if (map == null) return WaitingRoomPanel.unknownIcon(size * 8);
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
                    g.drawImage(art.tile(tile, x, y),
                            x * size, y * size, size, size, null);
                }
            }
        } finally {
            g.dispose();
        }
        return new ImageIcon(result);
    }
}
