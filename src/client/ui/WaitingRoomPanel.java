package client.ui;

import client.resource.Avatar;
import client.resource.GameImages;
import java.awt.*;
import java.util.EnumMap;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.swing.text.*;

final class WaitingRoomPanel extends CoastPanel {
    interface ReadyListener {
        /** 준비 버튼을 누르면 방 번호와 원하는 준비 상태를 알려 준다. 실제 창은 서버 요청에, 미리보기는 로컬 상태 변경에 연결한다. */
        void readyRequested(int roomId, boolean ready);
    }

    private final CardLayout actionLayout = new CardLayout();
    private final JPanel actionPanel = new JPanel(actionLayout);
    private final JLabel heading = new JLabel("", SwingConstants.CENTER);
    private final JPanel cards = new JPanel(new GridLayout(2, 2, 8, 8));
    private final EnumMap<Avatar, JButton> choices = new EnumMap<>(Avatar.class);
    private final RoomMapPreview mapPreview = new RoomMapPreview();
    private final JButton readyButton = new JButton("준비");
    private final JButton startButton = new JButton("게임 시작");
    private final JButton leaveButton = new JButton("방 나가기");
    private final JButton sendButton = new JButton("전송");
    private boolean leaving;
    private RoomInfo room;
    private RoomInfo.Member me;
    // 내가 요청한 선택을 기억해 두었다가 새 방 정보에 반영되면 버튼 잠금을 푼다.
    private Avatar awaitingAvatar;
    // null이면 대기 중인 요청이 없다. false는 준비 취소 응답을 기다리는 중이라는 뜻이다.
    private Boolean awaitingReady;
    private boolean awaitingStart;

    private static final Color INK = new Color(25, 61, 71);
    private static final Color CREAM = new Color(255, 249, 230);
    private static final Color GOLD = new Color(255, 207, 67);
    /** 캐릭터를 고르지 않은 참가자와 선택 버튼에 사용할 물음표 그림을 지정한 크기로 만든다. */
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
    private final JTextPane chatLog = new JTextPane();
    private final JButton emojiButton = new JButton("이모티콘");
    private final JTextField chatInput = new JTextField();

    /** 참가자, 선택, 채팅, 맵 영역을 배치한다. 소켓 처리는 생성할 때 받은 함수에 맡겨 미리보기에서도 같은 화면을 쓴다. */
    WaitingRoomPanel(ReadyListener ready, IntConsumer start, BiConsumer<Integer, Avatar> select,
            BiConsumer<Integer, String> chat, BiConsumer<Integer, RoomEmoji> emoji, IntConsumer leave) {
        super(GameImages.load("ready-room.png"));
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.add(heading, BorderLayout.CENTER);
        header.add(leaveButton, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        leaveButton.addActionListener(e -> {
            // 응답을 기다리는 동안에는 같은 방에 퇴장이나 채팅을 한 번 더 보내지 않는다.
            leaving = true;
            updateButtons();
            leave.accept(room.roomId());
        });

        cards.setOpaque(false);
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        put(grid, section("참가자", cards), 0, 0);
        put(grid, createCharacterSection(select), 1, 0);
        put(grid, createChatSection(chat, emoji), 0, 1);
        put(grid, createMapSection(ready, start), 1, 1);
        add(grid, BorderLayout.CENTER);

        updateButtons();
    }

    /** 캐릭터별 버튼을 만들고 클릭하면 방 번호와 선택값을 전달한다. 카드 그림은 새 방 정보가 올 때 바꾼다. */
    private JPanel createCharacterSection(BiConsumer<Integer, Avatar> select) {
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
                // 카드 그림은 showRoom()에서 바꾼다. 여기서 바로 바꾸면 거절된 선택도 성공처럼 보인다.
                awaitingAvatar = avatar;
                updateButtons();
                select.accept(room.roomId(), avatar);
            });
        }
        return section("캐릭터 선택", picker);
    }

    /** 채팅 기록과 입력칸을 만든다. Enter와 전송 버튼이 같은 검사를 거쳐 입력한 메시지를 보낸다. */
    private JPanel createChatSection(BiConsumer<Integer, String> chat, BiConsumer<Integer, RoomEmoji> sendEmoji) {
        chatLog.setEditable(false);
        chatLog.setBackground(CREAM);
        chatLog.getAccessibleContext().setAccessibleName("방 채팅 기록");
        chatInput.getAccessibleContext().setAccessibleName("보낼 메시지");
        JPanel composer = new JPanel(new BorderLayout(3, 0));
        // 선택 메뉴만 열기 때문에 작성 중인 채팅은 그대로 남는다.
        JPopupMenu picker = new JPopupMenu();
        for (RoomEmoji emoji : RoomEmoji.values()) {
            JMenuItem item = new JMenuItem(emoji.label, emoji.icon);
            item.addActionListener(e -> {
                if (room != null && !leaving) sendEmoji.accept(room.roomId(), emoji);
            });
            picker.add(item);
        }
        emojiButton.addActionListener(e -> picker.show(emojiButton, 0, emojiButton.getHeight()));
        composer.add(emojiButton, BorderLayout.WEST);
        composer.add(chatInput, BorderLayout.CENTER);
        composer.add(sendButton, BorderLayout.EAST);
        chatInput.addActionListener(e -> sendButton.doClick());
        sendButton.addActionListener(e -> {
            String message = chatInput.getText().strip();
            if (room == null || message.isEmpty()) return;
            chatInput.setText("");
            // 실제 채팅 기록에는 수신한 메시지만 추가해 내가 보낸 말이 두 번 표시되지 않게 한다.
            chat.accept(room.roomId(), message);
        });
        JPanel chatBox = section("방 채팅", new JScrollPane(chatLog));
        chatBox.add(composer, BorderLayout.SOUTH);
        return chatBox;
    }

    /** 두 창 미리보기에서 같은 선택을 표시하고 방장에게만 변경 동작을 연결한다. */
    void showPreviewMap(client.game.CoastMap map, java.util.function.Consumer<client.game.CoastMap> onPick) {
        mapPreview.setMap(map, onPick);
    }

    /** 대기실에 축소 맵과 역할별 동작 버튼을 만든다. 준비·시작을 누르면 등록한 함수에 요청을 전달한다. */
    private JPanel createMapSection(ReadyListener ready, IntConsumer start) {
        JPanel map = section("맵 미리보기", mapPreview);
        actionPanel.add(readyButton, "ready");
        actionPanel.add(startButton, "start");
        startButton.setBackground(GOLD);
        map.add(actionPanel, BorderLayout.SOUTH);
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
        return map;
    }

    /** 네 화면 영역에서 공통으로 쓸 제목, 테두리, 내용 배치를 갖춘 패널을 만든다. */
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

    /** 생성자가 각 영역을 지정한 행과 열에 배치할 때 쓴다. 왼쪽 참가자·채팅 영역을 오른쪽보다 넓게 둔다. */
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

    /** 새 방에 들어가거나 연결이 끝나면 이전 방의 대화와 입력 중이던 문장을 지운다. */
    private void clearChat() {
        chatLog.setText("");
        chatInput.setText("");
    }

    /** MainFrame이 ROOM_LIST를 받았을 때 단순 목록 갱신과 내가 요청한 퇴장을 구분하는 데 쓴다. */
    boolean isLeavePending() {
        return leaving;
    }

    /** 퇴장 확인 또는 연결 종료 때 호출한다. 같은 방에 다시 들어가도 이전 채팅과 요청 상태가 남지 않게 한다. */
    void resetRoom() {
        room = null;
        me = null;
        leaving = false;
        clearChat();
        requestFinished();
    }

    /** 같은 방에서 받은 대화 한 줄을 추가하고 가장 최근 메시지가 보이도록 스크롤 위치를 옮긴다. */
    void appendChat(String message) {
        appendPart(message + "\n", null);
        chatLog.setCaretPosition(chatLog.getDocument().getLength());
    }

    /** 발신자, 그림, 짧은 문구를 같은 채팅 기록에 차례로 추가한다. */
    void appendEmoji(String nickname, RoomEmoji emoji) {
        appendPart(nickname + "  ·  ", null);
        SimpleAttributeSet picture = new SimpleAttributeSet();
        StyleConstants.setIcon(picture, emoji.icon);
        appendPart(" ", picture);
        appendChat(" " + emoji.label);
    }

    /** 기존 기록 끝에 일반 글자나 아이콘을 삽입한다. 닉네임과 채팅은 일반 텍스트로 유지한다. */
    private void appendPart(String text, AttributeSet style) {
        StyledDocument log = chatLog.getStyledDocument();
        try {
            log.insertString(log.getLength(), text, style);
        } catch (BadLocationException invalidOffset) {
            throw new IllegalStateException("채팅 기록을 추가할 위치가 잘못되었습니다.", invalidOffset);
        }
    }

    /** 실제 창은 ROOM_INFO를 받을 때, 미리보기는 참가자 상태가 바뀔 때 호출한다. 같은 방 정보라도 내 번호에 따라 버튼이 달라진다. */
    void showRoom(RoomInfo info, int myId, String title) {
        if (room == null || room.roomId() != info.roomId()) {
            clearChat();
            requestFinished();
        }
        room = info;
        me = info.member(myId);
        // ROOM_INFO는 다른 참가자가 준비해도 도착한다. 내 카드에 요청한 값이 들어왔을 때만 잠금을 푼다.
        if (me != null && awaitingAvatar == me.avatar()) awaitingAvatar = null;
        if (me != null && awaitingReady != null && awaitingReady == me.ready()) awaitingReady = null;
        String roomNumber = info.roomId() + "번 방";
        heading.setText(title == null || title.isBlank() ? roomNumber + " 대기실" : roomNumber + " · " + title);
        heading.setToolTipText(heading.getText());
        cards.removeAll();
        for (RoomInfo.Member member : info.members()) {
            cards.add(card(member, myId));
        }
        while (cards.getComponentCount() < 4) cards.add(card(null, myId));
        cards.revalidate();
        cards.repaint();
        updateButtons();
    }

    /** showRoom()이 참가자 한 명의 그림·이름·준비 상태를 카드로 만들 때 쓴다. 참가자가 없으면 빈 자리로 표시한다. */
    private JPanel card(RoomInfo.Member member, int myId) {
        JPanel card = new JPanel(new BorderLayout()) {
            /** Swing이 참가자 카드를 그릴 때 호출한다. 캐릭터와 글씨 뒤에 보일 둥근 카드 바탕을 칠한다. */
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

    /** 요청 거절이나 방 전환 등으로 이전 요청을 더 기다릴 필요가 없어지면 잠금을 풀고 버튼 상태를 다시 계산한다. */
    void requestFinished() {
        awaitingAvatar = null;
        awaitingReady = null;
        awaitingStart = false;
        updateButtons();
    }

    /** 선택·준비 상태가 바뀔 때 호출한다. 남이 사용 중인 캐릭터를 막고 방장에게는 시작, 일반 참가자에게는 준비 버튼을 보여 준다. */
    private void updateButtons() {
        boolean inRoom = me != null && !leaving;
        chatInput.setEnabled(inRoom);
        emojiButton.setEnabled(inRoom);
        sendButton.setEnabled(inRoom);
        boolean idle = inRoom && awaitingAvatar == null && awaitingReady == null && !awaitingStart;
        leaveButton.setEnabled(inRoom);
        for (Avatar avatar : choices.keySet()) {
            JButton button = choices.get(avatar);
            RoomInfo.Member owner = room == null ? null : room.owner(avatar);
            boolean taken = owner != null && me != null && owner.id() != me.id();
            if (avatar != Avatar.NONE) button.setText(taken ? "사용 중" : avatar.label);
            button.setBorder(BorderFactory.createLineBorder(me != null && avatar == me.avatar() ? INK : new Color(170, 192, 185), 2));
            button.setToolTipText((taken ? owner.nickname() + " 사용 중 · " : "") + avatar.statHint());
            button.setEnabled(idle && (me.id() == room.hostId() || !me.ready()) && avatar != me.avatar() && !taken);
        }
        readyButton.setText(me != null && me.ready() ? "준비 취소" : "준비");
        readyButton.setEnabled(idle && (me.ready() || me.avatar() != Avatar.NONE));
        boolean host = me != null && room.hostId() == me.id();
        actionLayout.show(actionPanel, host ? "start" : "ready");
        startButton.setEnabled(idle && host && room.canStart());
    }
}
