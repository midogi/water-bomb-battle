package client.ui;

import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class MainFrame extends JFrame implements LoginPanel.LoginListener {

    // CardLayout에서 각 화면을 구분하는 이름이다.
    private static final String LOGIN = "login";
    private static final String LOBBY = "lobby";

    private final CardLayout layout = new CardLayout();
    private final JPanel screens = new JPanel(layout);

    // [변경] 환영 문구 하나 대신, 방 목록을 가진 로비 패널을 보관한다.
    private final LobbyPanel lobbyPanel = new LobbyPanel();

    public MainFrame() {
        super("연결고리 | 물폭탄 대전");

        // 사용자가 닉네임을 제출하면 이 창의 loginRequested()가 호출된다.
        LoginPanel loginPanel = new LoginPanel(this);

        screens.add(loginPanel, LOGIN);
        screens.add(lobbyPanel, LOBBY); // [변경] 로비 화면을 카드로 등록한다.

        screens.setPreferredSize(new Dimension(600, 520));
        setContentPane(screens);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);

        // 프로그램을 처음 열면 로그인 화면부터 보여 준다.
        layout.show(screens, LOGIN);
    }

    @Override
    public void loginRequested(String nickname) {
        // 현재는 화면만 확인하는 단계라서 바로 로비로 이동한다.
        // 서버 연결 후에는 LOGIN_OK를 받았을 때 showLobby()를 호출한다.
        showLobby(nickname);
    }

    private void showLobby(String nickname) {
        // [변경] 로비 안의 닉네임 표시는 LobbyPanel이 담당한다.
        lobbyPanel.setNickname(nickname);

        // MainFrame은 보여 줄 화면을 선택한다.
        layout.show(screens, LOBBY);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}