package client.ui;

import client.resource.Avatar;
import client.resource.GameImages;
import java.awt.*;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javax.swing.*;

final class WaitingRoomPanel extends CoastPanel {
    interface ReadyListener {
        void readyRequested(int roomId, boolean ready);
    }

    private final JLabel heading = new JLabel("", SwingConstants.CENTER);
    private final JPanel cards = new JPanel(new GridLayout(2, 2, 8, 8));
    private final EnumMap<Avatar, JButton> choices = new EnumMap<>(Avatar.class);
    private final JButton readyButton = new JButton("준비");
    private final JButton startButton = new JButton("게임 시작");
    private RoomInfo room;
    private RoomInfo.Member me;
    private Avatar awaitingAvatar;
    private Boolean awaitingReady;
    private boolean awaitingStart;

    private static final Color INK = new Color(25, 61, 71);
    private static final Color CREAM = new Color(255, 249, 230);
    private static final Color GOLD = new Color(255, 207, 67);
    // 물방울 색의 미선택 배지. 참가자가 선택하면 같은 자리에 캐릭터가 들어간다.
    private static Icon unknownIcon(int size) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(INK);
            g.fillOval(1, 1, size - 2, size - 2);
            g.setColor(new Color(123, 212, 204));
            g.fillOval(3, 3, size - 6, size - 6);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size * 2 / 3));
            FontMetrics metrics = g.getFontMetrics();
            g.setColor(INK);
            g.drawString("?", (size - metrics.stringWidth("?")) / 2,
                    (size - metrics.getHeight()) / 2 + metrics.getAscent());
        } finally {
            g.dispose();
        }
        return new ImageIcon(image);
    }
    private static final Icon UNKNOWN = unknownIcon(48);
    private final JTextArea chatLog = new JTextArea();
    private final JTextField chatInput = new JTextField();

    WaitingRoomPanel(ReadyListener ready, IntConsumer start, BiConsumer<Integer, Avatar> select,
            BiConsumer<Integer, String> chat) {
        super(GameImages.load("ready-room.png"));
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        add(heading, BorderLayout.NORTH);

        cards.setOpaque(false);
        JPanel picker = new JPanel(new GridLayout(3, 2, 4, 4));
        picker.setOpaque(false);
        for (Avatar avatar : Avatar.values()) {
            JButton button = new JButton(avatar.label);
            if (avatar.image != null) {
                button.setIcon(new ImageIcon(avatar.image.getScaledInstance(34, 34, Image.SCALE_SMOOTH)));
            } else {
                button.setIcon(unknownIcon(34));
            }
            button.setVerticalTextPosition(SwingConstants.BOTTOM);
            button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setBackground(CREAM);
            button.setMargin(new Insets(1, 1, 1, 1));
            choices.put(avatar, button);
            picker.add(button);
            button.addActionListener(e -> {
                awaitingAvatar = avatar;
                updateButtons();
                select.accept(room.roomId(), avatar);
            });
        }

        chatLog.setEditable(false);
        chatLog.setLineWrap(true);
        chatLog.setWrapStyleWord(true);
        chatLog.setBackground(CREAM);
        chatLog.getAccessibleContext().setAccessibleName("방 채팅 기록");
        chatInput.getAccessibleContext().setAccessibleName("보낼 메시지");
        JPanel composer = new JPanel(new BorderLayout(3, 0));
        JButton send = new JButton("전송");
        composer.add(chatInput, BorderLayout.CENTER);
        composer.add(send, BorderLayout.EAST);
        chatInput.addActionListener(e -> send.doClick());
        send.addActionListener(e -> {
            String message = chatInput.getText().strip();
            if (room == null || message.isEmpty()) return;
            chatInput.setText("");
            chat.accept(room.roomId(), message);
        });
        JPanel chatBox = section("방 채팅", new JScrollPane(chatLog));
        chatBox.add(composer, BorderLayout.SOUTH);

        JPanel map = section("맵 미리보기", new JLabel(RoomMapPreview.icon(), SwingConstants.CENTER));
        JPanel actions = new JPanel(new CardLayout());
        actions.add(readyButton, "ready");
        actions.add(startButton, "start");
        startButton.setBackground(GOLD);
        map.add(actions, BorderLayout.SOUTH);
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        put(grid, section("참가자", cards), 0, 0);
        put(grid, section("캐릭터 선택", picker), 1, 0);
        put(grid, chatBox, 0, 1);
        put(grid, map, 1, 1);
        add(grid, BorderLayout.CENTER);

        readyButton.addActionListener(e -> {
            awaitingReady = !me.ready();
            updateButtons();
            ready.readyRequested(room.roomId(), awaitingReady);
        });
        startButton.addActionListener(e -> {
            awaitingStart = true;
            updateButtons();
            start.accept(room.roomId());
        });
        updateButtons();
    }

    private static JPanel section(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(CREAM);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(INK),
                BorderFactory.createEmptyBorder(5, 7, 6, 7)));
        JLabel label = new JLabel(title);
        label.setForeground(INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        panel.setMinimumSize(new Dimension(0, 0));
        panel.setPreferredSize(new Dimension(0, 0));
        return panel;
    }

    private static void put(JPanel grid, JPanel section, int col, int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = col;
        c.gridy = row;
        c.weightx = col == 0 ? 0.62 : 0.38;
        c.weighty = row == 0 ? 0.60 : 0.40;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(3, 3, 3, 3);
        grid.add(section, c);
    }

    void clearChat() {
        chatLog.setText("");
        chatInput.setText("");
    }

    void appendChat(String message) {
        chatLog.append(message + "\n");
        chatLog.setCaretPosition(chatLog.getDocument().getLength());
    }

    void showRoom(RoomInfo info, int myId) {
        if (room == null || room.roomId() != info.roomId()) {
            clearChat();
            requestFinished();
        }
        room = info;
        me = info.member(myId);
        // 다른 사람의 갱신으로 내 요청 잠금이 먼저 풀리지 않게 한다.
        if (me != null && awaitingAvatar == me.avatar()) awaitingAvatar = null;
        if (me != null && awaitingReady != null && awaitingReady == me.ready()) awaitingReady = null;
        heading.setText(info.roomId() + "번 방 대기실");
        cards.removeAll();
        for (RoomInfo.Member member : info.members()) {
            cards.add(card(member, myId));
        }
        while (cards.getComponentCount() < 4) cards.add(card(null, myId));
        cards.revalidate();
        cards.repaint();
        updateButtons();
    }

    private JPanel card(RoomInfo.Member member, int myId) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(216, 238, 230));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(162, 128));
        JLabel portrait = new JLabel("", SwingConstants.CENTER);
        card.add(portrait, BorderLayout.CENTER);
        if (member == null) {
            portrait.setText("빈 자리");
            return card;
        }
        Avatar avatar = member.avatar();
        if (avatar.image == null) {
            portrait.setIcon(UNKNOWN);
            portrait.setToolTipText(avatar.label);
        }
        else portrait.setIcon(new ImageIcon(avatar.image.getScaledInstance(54, 54, Image.SCALE_SMOOTH)));
        JPanel caption = new JPanel(new GridLayout(2, 1));
        caption.setOpaque(false);
        String roles = (member.id() == room.hostId() ? " [방장]" : "") + (member.id() == myId ? " (나)" : "");
        JLabel name = new JLabel("", SwingConstants.CENTER);
        name.putClientProperty("html.disable", true);
        name.setText(member.nickname() + roles);
        name.setToolTipText(name.getText());
        caption.add(name);
        boolean host = member.id() == room.hostId();
        JLabel status = new JLabel(host ? "방장" : "준비", SwingConstants.CENTER);
        status.setOpaque(true);
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        if (host) {
            // 방장: 파란 바탕에 흰 글씨
            status.setBackground(new Color(35, 105, 170));
            status.setForeground(Color.WHITE);
        } else if (member.ready()) {
            // 준비한 참가자: 노란 바탕에 진한 글씨
            status.setBackground(GOLD);
            status.setForeground(INK);
        } else {
            // 준비 전 참가자: 흐린 바탕과 글씨
            status.setBackground(new Color(214, 228, 223));
            status.setForeground(new Color(102, 121, 117));
        }
        status.setToolTipText(host ? "방장" : member.ready() ? "준비 완료" : "준비 전");
        caption.add(status);
        card.add(caption, BorderLayout.SOUTH);
        return card;
    }

    void requestFinished() {
        awaitingAvatar = null;
        awaitingReady = null;
        awaitingStart = false;
        updateButtons();
    }

    private void updateButtons() {
        boolean idle = me != null && awaitingAvatar == null && awaitingReady == null && !awaitingStart;
        for (Avatar avatar : choices.keySet()) {
            JButton button = choices.get(avatar);
            RoomInfo.Member owner = room == null ? null : room.owner(avatar);
            boolean taken = owner != null && me != null && owner.id() != me.id();
            if (avatar != Avatar.NONE) button.setText(taken ? "사용 중" : avatar.label);
            button.setBorder(BorderFactory.createLineBorder(me != null && avatar == me.avatar() ? INK : new Color(170, 192, 185), 2));
            button.setToolTipText(taken ? owner.nickname() + " 사용 중" : avatar.label);
            button.setEnabled(idle && (me.id() == room.hostId() || !me.ready()) && avatar != me.avatar() && !taken);
        }
        readyButton.setText(me != null && me.ready() ? "준비 취소" : "준비");
        readyButton.setEnabled(idle && (me.ready() || me.avatar() != Avatar.NONE));
        boolean host = me != null && room.hostId() == me.id();
        JPanel actions = (JPanel) startButton.getParent();
        ((CardLayout) actions.getLayout()).show(actions, host ? "start" : "ready");
        startButton.setEnabled(idle && host && room.canStart());
    }
}
