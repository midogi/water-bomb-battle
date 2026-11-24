package client.ui;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;    
import java.awt.event.ActionListener;  
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

// 닉네임을 입력받고, 유효한 닉네임을 바깥에 전달하는 화면이다.
public class LoginPanel extends CoastPanel {

    // LoginPanel은 입력 검사까지만 맡는다. MainFrame이 이 리스너를 구현해 실제 접속을 시작한다.
    public interface LoginListener {
        /** 입력 검사를 통과한 닉네임을 MainFrame에 전달하는 약속이다. 버튼 클릭과 Enter 입력이 모두 여기로 이어진다. */
        void loginRequested(String nickname);
    }

    // 입력칸을 필드로 두면 아래의 내부 리스너에서도 입력값을 읽을 수 있다.
    private final JTextField nicknameField = new JTextField(16);
    private final JButton enterButton = new JButton("입장");

    private final LoginListener loginListener;

    /** 로그인 입력칸과 버튼을 만들고 같은 입력 검사 동작을 연결한다. 결과는 전달받은 LoginListener에 알린다. */
    public LoginPanel(LoginListener loginListener) {
        this.loginListener = loginListener;

        // 제목, 안내 문구, 입력칸, 버튼을 가운데에 한 줄씩 배치한다.
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;                        // 모든 요소를 첫 번째 열에 놓는다.
        gbc.insets = new Insets(6, 6, 6, 6);  // 각 요소의 상하좌우 여백

        JLabel titleLabel = new JLabel("물폭탄 대전");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 32));

        gbc.gridy = 0; // 첫 번째 줄: 게임 제목
        add(titleLabel, gbc);

        gbc.gridy = 1; // 두 번째 줄: 안내 문구
        add(new JLabel("사용할 닉네임을 입력하세요"), gbc);

        gbc.gridy = 2; // 세 번째 줄: 닉네임 입력칸
        add(nicknameField, gbc);

        gbc.gridy = 3; // 네 번째 줄: 입장 버튼
        add(enterButton, gbc);

        LoginActionListener listener = new LoginActionListener();

        // 버튼을 클릭하거나 입력칸에서 Enter를 누르면
        // 같은 객체의 actionPerformed()가 실행된다.
        enterButton.addActionListener(listener);
        nicknameField.addActionListener(listener);
    }

    /** MainFrame이 로그인 요청을 시작하거나 접속이 끝났을 때 호출해 입력칸과 버튼의 사용 가능 상태를 바꾼다. */
    public void setConnecting(boolean connecting) {
        nicknameField.setEnabled(!connecting);
        enterButton.setEnabled(!connecting);
        enterButton.setText(connecting ? "접속 중…" : "입장");
    }

    // 내부 클래스 방식으로 이벤트 리스너를 정의한다.
    // LoginPanel 안에서만 사용하므로 private으로 둔다.
    private class LoginActionListener implements ActionListener {

        /** 입장 버튼 또는 Enter를 누르면 닉네임 양끝의 공백을 제거한다. 빈 이름이면 안내하고, 유효하면 로그인을 요청한다. */
        @Override
        public void actionPerformed(ActionEvent e) {
            String nickname = nicknameField.getText().strip();

            // 아무것도 입력하지 않았거나 공백만 입력했다면 다시 입력받는다.
            if (nickname.isEmpty()) {
                // 여기서 this는 LoginActionListener 객체를 가리킨다.
                // 알림 창의 기준이 되는 바깥 화면은 LoginPanel.this로 지정한다.
                JOptionPane.showMessageDialog(
                        LoginPanel.this, "닉네임을 입력해 주세요.");

                nicknameField.requestFocusInWindow();
                return; // 빈 닉네임이 아래로 전달되지 않게 여기서 끝낸다.
            }

            loginListener.loginRequested(nickname);
        }
    }
}
