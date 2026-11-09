package client.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

// 접속한 사용자의 닉네임과 현재 방 목록을 보여 주는 화면이다.
public class LobbyPanel extends JPanel {

    private final JLabel nicknameLabel = new JLabel();

    // 방 이름들을 보관한다. 여기에 항목을 추가하면 화면 목록도 갱신된다.
    private final DefaultListModel<String> roomModel = new DefaultListModel<>();

    // 방이 없을 때는 안내 문구, 방이 있으면 방 개수를 표시한다.
    private final JLabel roomStatusLabel = new JLabel();

    public LobbyPanel() {
        // 화면을 위쪽, 가운데, 아래쪽으로 나누고 영역 사이에 여백을 둔다.
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 위쪽에는 로비 제목과 사용자 닉네임을 나란히 배치한다.
        JPanel headerPanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("게임 로비");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(nicknameLabel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 가운데에는 방 목록을 배치한다. 방이 많아지면 스크롤할 수 있다.
        JList<String> roomList = new JList<>(roomModel);
        JScrollPane scrollPane = new JScrollPane(roomList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("방 목록"));
        add(scrollPane, BorderLayout.CENTER);

        // 안내 문구는 목록 밖에 둔다. 안내 문구 자체가 방으로 선택되지 않게 한다.
        roomStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(roomStatusLabel, BorderLayout.SOUTH);

        // 처음 로비를 만들 때는 빈 목록을 표시한다.
        setRoomNames(List.of());
    }

    // MainFrame이 로그인한 사용자의 닉네임을 전달할 때 호출한다.
    public void setNickname(String nickname) {
        nicknameLabel.setText("닉네임: " + nickname);
    }

    // 전달받은 방 목록으로 화면을 갱신한다.
    // 기존 항목을 먼저 지워야 같은 목록을 두 번 받아도 방이 중복되지 않는다.
    public void setRoomNames(List<String> roomNames) {
        roomModel.clear();

        for (String roomName : roomNames) {
            roomModel.addElement(roomName);
        }

        if (roomModel.isEmpty()) {
            roomStatusLabel.setText("현재 개설된 방이 없습니다.");
        } else {
            roomStatusLabel.setText("현재 개설된 방: " + roomModel.size() + "개");
        }
    }
}