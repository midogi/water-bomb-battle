package client.ui;

import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.text.*;

/** 로비와 방에서 같은 입력 방식과 이모티콘을 사용하되 대화 기록은 각 패널이 따로 보관한다. */
final class ChatPane extends JPanel {
    private final JButton sendButton = new JButton("전송");
    private final JTextPane chatLog = new JTextPane();
    private final JButton emojiButton = new JButton("이모티콘");
    private final JTextField chatInput = new JTextField();

    ChatPane(String title, Consumer<String> chat, Consumer<RoomEmoji> sendEmoji) {
        super(new BorderLayout(4, 4));
        setOpaque(false);
        chatLog.setEditable(false);
        chatLog.setBackground(WaitingRoomPanel.CREAM);
        chatLog.getAccessibleContext().setAccessibleName(title + " 기록");
        chatInput.getAccessibleContext().setAccessibleName("보낼 메시지");
        JPanel composer = new JPanel(new BorderLayout(3, 0));
        // 선택 메뉴만 열기 때문에 작성 중인 채팅은 그대로 남는다.
        JPopupMenu picker = new JPopupMenu();
        for (RoomEmoji emoji : RoomEmoji.values()) {
            JMenuItem item = new JMenuItem(emoji.label, emoji.icon);
            item.addActionListener(e -> {
                sendEmoji.accept(emoji);
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
            if (message.isEmpty()) return;
            chatInput.setText("");
            // 실제 채팅 기록에는 수신한 메시지만 추가해 내가 보낸 말이 두 번 표시되지 않게 한다.
            chat.accept(message);
        });
        add(new JScrollPane(chatLog), BorderLayout.CENTER);
        add(composer, BorderLayout.SOUTH);
    }

    void clear() {
        chatLog.setText("");
        chatInput.setText("");
    }

    void setInputEnabled(boolean enabled) {
        chatInput.setEnabled(enabled);
        emojiButton.setEnabled(enabled);
        sendButton.setEnabled(enabled);
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

}
