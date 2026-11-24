package client.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.*;

public class LobbyPanel extends CoastPanel {
    interface RoomListener {
        /** 사용자가 입력한 방 제목을 검사한 뒤 호출한다. MainFrame이 이 요청을 받아 서버로 보낸다. */
        void createRequested(String title);
        /** 선택한 방의 번호와 제목을 함께 전달한다. 같은 제목의 방도 번호로 구분한다. */
        void joinRequested(RoomSummary room);
    }

    private final JLabel nicknameLabel = new JLabel();
    private final JLabel roomStatusLabel = new JLabel("", SwingConstants.CENTER);
    private final DefaultListModel<RoomSummary> roomModel = new DefaultListModel<>();
    private final JList<RoomSummary> roomList = new JList<>(roomModel);
    private final JButton createButton = new JButton("방 만들기");
    private final JButton joinButton = new JButton("입장");

    /** MainFrame이 로비를 만들 때 방 목록과 버튼을 준비한다. 버튼의 결과는 RoomListener에 전달한다. */
    LobbyPanel(RoomListener listener) {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("게임 로비");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        header.add(title, BorderLayout.WEST);
        header.add(nicknameLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scroll = new JScrollPane(roomList);
        // 목록의 빈 공간과 뷰포트에서 바다 배경이 비치게 한다.
        roomList.setOpaque(false);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createTitledBorder("방 목록"));
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(createButton);
        buttons.add(joinButton);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(roomStatusLabel, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        createButton.addActionListener(event -> requestCreate(listener));
        joinButton.addActionListener(event -> {
            RoomSummary room = roomList.getSelectedValue();
            if (room == null) {
                JOptionPane.showMessageDialog(this, "입장할 방을 먼저 선택해 주세요.");
                return;
            }
            listener.joinRequested(room);
        });
        setRooms(List.of());
    }

    /** 방 만들기 버튼에서 호출한다. 입력 취소와 사용할 수 없는 제목을 걸러낸 다음 생성 요청을 넘긴다. */
    private void requestCreate(RoomListener listener) {
        String input = JOptionPane.showInputDialog(this, "방 제목을 입력해 주세요.");
        if (input == null) {
            return;
        }
        String title = input.strip();
        // 콜론과 쉼표는 방 목록에서 필드와 방을 구분하는 문자다.
        if (title.isEmpty() || title.contains(":") || title.contains(",")) {
            JOptionPane.showMessageDialog(this, "방 제목을 입력하고 콜론과 쉼표는 제외해 주세요.");
            return;
        }
        listener.createRequested(title);
    }

    /** 로그인이 승인되면 MainFrame이 호출해 이 로비를 사용하는 사람의 닉네임을 표시한다. */
    public void setNickname(String nickname) {
        nicknameLabel.setText("닉네임: " + nickname);
    }

    /** 로그인 직후 방 목록 응답을 기다릴 때 호출한다. 응답을 받기 전과 방이 실제로 없는 경우를 구분한다. */
    public void showLoading() {
        roomModel.clear();
        roomStatusLabel.setText("방 목록을 불러오는 중입니다.");
    }

    /** MainFrame이 방 요청을 보내거나 거절을 받았을 때 호출해 생성·입장 버튼을 함께 잠그거나 푼다. */
    void setRequestPending(boolean pending) {
        createButton.setEnabled(!pending);
        joinButton.setEnabled(!pending);
    }

    /** ROOM_LIST에서 읽은 방으로 목록을 갱신한다. 이전에 선택한 방이 남아 있으면 같은 번호를 다시 선택한다. */
    void setRooms(List<RoomSummary> rooms) {
        RoomSummary selected = roomList.getSelectedValue();
        roomModel.clear();
        for (RoomSummary room : rooms) {
            roomModel.addElement(room);
            // 새 목록에서 인원이나 순서가 바뀌어도 같은 방을 계속 선택한다.
            if (selected != null && selected.id() == room.id()) {
                roomList.setSelectedIndex(roomModel.size() - 1);
            }
        }
        roomStatusLabel.setText(rooms.isEmpty() ? "현재 개설된 방이 없습니다."
                : "현재 개설된 방: " + rooms.size() + "개");
    }
}
