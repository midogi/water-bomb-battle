package client.ui;

import java.awt.CardLayout;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class MainFrame extends JFrame implements LoginPanel.LoginListener {

    // CardLayout에서 각 화면을 구분하는 이름이다.
    private static final String LOGIN = "login";
    private static final String LOBBY = "lobby";

    private final CardLayout layout = new CardLayout();
    private final JPanel screens = new JPanel(layout);

    private final LoginPanel loginPanel = new LoginPanel(this);
    private final LobbyPanel lobbyPanel = new LobbyPanel();
    private boolean sessionActive;

    public MainFrame() {
        super("연결고리 | 물폭탄 대전");

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
        if (sessionActive) {
            return;
        }
        sessionActive = true;
        loginPanel.setConnecting(true);

        // 소켓 응답을 기다려도 화면이 멈추지 않게 별도 스레드에서 실행한다.
        LobbySession session = new LobbySession(nickname,
                () -> showLobby(nickname), lobbyPanel::setRoomNames, this::returnToLogin);
        Thread worker = new Thread(session, "lobby-session");
        worker.setDaemon(true);
        worker.start();
    }

    private void returnToLogin(String reason) {
        sessionActive = false;
        loginPanel.setConnecting(false);
        layout.show(screens, LOGIN);
        JOptionPane.showMessageDialog(this, reason);
    }

    private void showLobby(String nickname) {
        // [변경] 로비 안의 닉네임 표시는 LobbyPanel이 담당한다.
        lobbyPanel.setNickname(nickname);
        lobbyPanel.showLoading();

        // MainFrame은 보여 줄 화면을 선택한다.
        layout.show(screens, LOBBY);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
