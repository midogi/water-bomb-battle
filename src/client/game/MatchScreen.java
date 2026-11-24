package client.game;

import client.resource.GameImages;
import common.model.ItemType;
import common.model.PlayerState;
import java.awt.*;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;

/** 실제 접속과 두 창 프리뷰가 공유하는 경기 화면. 맵 그리기는 GamePanel에 맡긴다. */
public final class MatchScreen extends JPanel {
    private static final ImageIcon WIN = new ImageIcon(GameImages.load("result-win.png"));
    private static final ImageIcon LOSE = new ImageIcon(GameImages.load("result-lose.png"));
    private final GamePanel board;
    private final JLabel result = new JLabel("", SwingConstants.CENTER);
    private final JPanel members = new JPanel();
    private final Map<Integer, JLabel> statuses = new LinkedHashMap<>();
    private final java.util.Set<Integer> departed = new java.util.HashSet<>();
    // 아이템 목록과 하단 나가기 버튼 사이의 간격. 키우면 목록만 위로 올라간다.
    private static final int ITEM_EXIT_GAP = 32;
    private final Map<ItemType, JLabel> itemSlots = new EnumMap<>(ItemType.class);
    private final JButton leave = new JButton("나가기");
    private boolean dartAvailable;
    private boolean finished;
    private int myId;

    /** 맵 위에는 투명한 결과 배지를, 오른쪽에는 명단과 나가기 버튼을 배치한다. 버튼으로 돌아갈 화면은 호출자가 onLeave로 정한다. */
    public MatchScreen(GamePanel board, Runnable onLeave) {
        this(board, onLeave, null);
    }

    public MatchScreen(GamePanel board, Runnable onLeave, Consumer<ItemType> onItem) {
        super(new BorderLayout());
        this.board = board;
        board.setLayout(new GridBagLayout());
        result.setHorizontalTextPosition(SwingConstants.CENTER);
        result.setVerticalTextPosition(SwingConstants.BOTTOM);
        result.setForeground(Color.WHITE);
        result.setFont(result.getFont().deriveFont(Font.BOLD, 20f));
        result.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        // 배지 PNG의 투명 영역을 통해 아래 게임판이 보인다.
        result.setOpaque(false);
        result.setVisible(false);
        board.add(result);
        add(board, BorderLayout.CENTER);

        JPanel side = new JPanel(new BorderLayout(0, 10));
        side.setPreferredSize(new Dimension(156, 0));
        side.setBackground(new Color(227, 242, 236));
        side.setBorder(BorderFactory.createEmptyBorder(14, 8, 12, 8));
        JLabel title = new JLabel("참가자");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        side.add(title, BorderLayout.NORTH);
        members.setLayout(new BoxLayout(members, BoxLayout.Y_AXIS));
        members.setOpaque(false);
        side.add(members, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        if (onItem != null) {
            JPanel inventory = new JPanel(new GridLayout(0, 1, 0, 3));
            inventory.setOpaque(false);
            inventory.setBorder(BorderFactory.createEmptyBorder(0, 0, ITEM_EXIT_GAP, 0));
            for (ItemArt art : ItemArt.values()) {
                JLabel slot = new JLabel("", art.slotIcon, SwingConstants.LEADING);
                slot.setOpaque(true);
                slot.setBackground(new Color(242, 249, 245));
                slot.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 2));
                itemSlots.put(art.type, slot);
                inventory.add(slot);
                bindItemKey(art, onItem);
            }
            bottom.add(inventory, BorderLayout.CENTER);
        }
        bottom.add(leave, BorderLayout.SOUTH);
        side.add(bottom, BorderLayout.SOUTH);
        add(side, BorderLayout.EAST);
        leave.addActionListener(e -> {
            leave.setEnabled(false);
            board.setEnabled(false);
            onLeave.run();
        });
    }

    /** 새 경기의 시작 명단으로 카드와 입력 상태를 초기화한다. 좌표가 올 때마다 카드를 다시 만들 필요는 없다. */
    public void start(GameBoard initial, int myId, Map<Integer, GamePanel.Appearance> appearances) {
        this.myId = myId;
        finished = false;
        result.setVisible(false);
        leave.setEnabled(true);
        board.setEnabled(true);
        statuses.clear();
        departed.clear();
        members.removeAll();
        for (GameBoard.Player player : initial.players()) {
            GamePanel.Appearance look = appearances.get(player.id());
            String name = look == null ? "참가자 " + player.id() : look.nickname();
            JLabel nickname = new JLabel();
            nickname.putClientProperty("html.disable", true);
            nickname.setText(name);
            nickname.setToolTipText(nickname.getText());
            JPanel card = new JPanel(new BorderLayout(4, 2));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            card.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            if (look != null && look.avatar().image != null) {
                Image portrait = look.avatar().image.getScaledInstance(30, 30, Image.SCALE_SMOOTH);
                card.add(new JLabel(new ImageIcon(portrait)), BorderLayout.WEST);
            }
            card.add(nickname, BorderLayout.CENTER);
            JLabel status = new JLabel(stateName(player.state()));
            statuses.put(player.id(), status);
            card.add(status, BorderLayout.SOUTH);
            members.add(card);
            members.add(Box.createVerticalStrut(4));
        }
        members.revalidate();
        members.repaint();
        board.showBoard(initial, myId, appearances);
        updateInventory(Map.of(), false, false);
    }

    public void updateInventory(Map<ItemType, Integer> inventory, boolean invisible, boolean lensActive) {
        for (var entry : itemSlots.entrySet()) {
            ItemArt art = ItemArt.forType(entry.getKey());
            int count = inventory.getOrDefault(art.type, 0);
            boolean active = (art.type == ItemType.INVISIBILITY && invisible)
                    || (art.type == ItemType.LENS && lensActive);
            JLabel slot = entry.getValue();
            slot.setText(art.key + "  " + art.name + " ×" + count + (active ? " · 사용 중" : ""));
            slot.setEnabled(!finished && (count > 0 || active));
            if (art.type == ItemType.DART) dartAvailable = count > 0;
            else getActionMap().get(art.type).setEnabled(!finished && count > 0);
        }
    }

    /** 숫자를 뗄 때 한 번만 요청해 키를 길게 눌러도 여러 개가 연달아 소모되지 않게 한다. */
    private void bindItemKey(ItemArt art, Consumer<ItemType> onItem) {
        InputMap keys = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        if (art.type == ItemType.DART) {
            bindDartPress(keys, onItem);
            return;
        }
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0 + art.key, 0, true), art.type);
        keys.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_NUMPAD0 + art.key, 0, true), art.type);
        getActionMap().put(art.type, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                if (board.isEnabled() && !finished) onItem.accept(art.type);
            }
        });
    }

    /** 다트는 키를 누르는 순간 발사하고, 손을 떼기 전까지 자동 반복 입력을 무시한다. */
    private void bindDartPress(InputMap keys, Consumer<ItemType> onItem) {
        java.util.Set<Integer> held = new java.util.HashSet<>();
        for (int key : new int[]{java.awt.event.KeyEvent.VK_4, java.awt.event.KeyEvent.VK_NUMPAD4}) {
            String press = "dart-press-" + key;
            String release = "dart-release-" + key;
            keys.put(KeyStroke.getKeyStroke(key, 0, false), press);
            keys.put(KeyStroke.getKeyStroke(key, 0, true), release);
            getActionMap().put(press, new AbstractAction() {
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (held.add(key) && board.isEnabled() && !finished && dartAvailable)
                        onItem.accept(ItemType.DART);
                }
            });
            getActionMap().put(release, new AbstractAction() {
                public void actionPerformed(java.awt.event.ActionEvent e) { held.remove(key); }
            });
        }
        board.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent e) { held.clear(); }
        });
    }

    private static String stateName(PlayerState state) {
        return switch (state) {
            case ALIVE -> "생존";
            case TRAPPED -> "물방울에 갇힘";
            case DEAD -> "탈락";
        };
    }

    /** 퇴장 직후 서버가 같은 사람을 DEAD로 보내도 카드에는 퇴장 사유를 유지한다. */
    public void playerLeft(int playerId) {
        JLabel label = statuses.get(playerId);
        if (finished || label == null) return;
        departed.add(playerId);
        label.setText("퇴장");
    }

    /** 서버가 확정한 참가자 상태를 명단과 게임판에 함께 반영한다. 종료 후에는 최종 결과를 유지한다. */
    public void updateState(GameBoard.State state) {
        if (finished) return;
        for (GameBoard.Player player : state.players()) {
            JLabel label = statuses.get(player.id());
            if (label != null && !departed.contains(player.id())) label.setText(stateName(player.state()));
        }
        board.updateState(state);
    }

    /** WIN은 승자의 사용자 번호를 담는다. 동일한 결과 메시지도 내 번호에 따라 서로 다른 배지가 된다. */
    public void finish(String body) {
        if (finished) return;
        boolean draw = body.matches("DRAW:[0-9]+(,[0-9]+)*");
        if (!draw && !body.matches("WIN:[0-9]+")) return;
        String winner = draw ? "" : body.substring(4);
        if (!draw && statuses.keySet().stream().noneMatch(id -> id.toString().equals(winner))) return;
        boolean won = winner.equals(Integer.toString(myId));
        finished = true;
        board.setEnabled(false);
        itemSlots.forEach((type, slot) -> {
            slot.setEnabled(false);
            if (type != ItemType.DART) getActionMap().get(type).setEnabled(false);
        });
        result.setIcon(draw ? null : won ? WIN : LOSE);
        result.setText(draw ? "무승부" : "");
        result.getAccessibleContext().setAccessibleName(draw ? "무승부" : won ? "승리" : "패배");
        result.setVisible(true);
        leave.setEnabled(true);
        leave.requestFocusInWindow();
    }

    /** 결과가 확정된 뒤에는 포커스 이동으로 생긴 STOP도 전송하지 않는다. 다음 경기 시작 시 초기화된다. */
    public boolean isFinished() {
        return finished;
    }

    /** 실제 통신에는 경기만 포기하는 요청이 없으므로 결과 수신 후 대기실 복귀를 허용한다. */
    public void allowReturnAfterResult() {
        leave.setEnabled(false);
        leave.setToolTipText("경기가 끝나면 기존 대기실로 돌아갑니다.");
    }
}
