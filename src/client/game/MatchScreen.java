package client.game;

import client.resource.GameImages;
import common.model.PlayerState;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/** 실제 접속과 두 창 프리뷰가 공유하는 경기 화면. 맵 그리기는 GamePanel에 맡긴다. */
public final class MatchScreen extends JPanel {
    private static final ImageIcon WIN = new ImageIcon(GameImages.load("result-win.png"));
    private static final ImageIcon LOSE = new ImageIcon(GameImages.load("result-lose.png"));
    private final GamePanel board;
    private final JLabel result = new JLabel("", SwingConstants.CENTER);
    private final JPanel members = new JPanel();
    private final Map<Integer, JLabel> statuses = new LinkedHashMap<>();
    private final JButton leave = new JButton("나가기");
    private boolean finished;
    private int myId;

    /** 맵 위에는 투명한 결과 배지를, 오른쪽에는 명단과 나가기 버튼을 배치한다. 버튼으로 돌아갈 화면은 호출자가 onLeave로 정한다. */
    public MatchScreen(GamePanel board, Runnable onLeave) {
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
        side.add(leave, BorderLayout.SOUTH);
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
    }

    private static String stateName(PlayerState state) {
        return switch (state) {
            case ALIVE -> "생존";
            case TRAPPED -> "물방울에 갇힘";
            case DEAD -> "탈락";
        };
    }

    /** 서버가 확정한 참가자 상태를 명단과 게임판에 함께 반영한다. 종료 후에는 최종 결과를 유지한다. */
    public void updateState(GameBoard.State state) {
        if (finished) return;
        for (GameBoard.Player player : state.players()) {
            JLabel label = statuses.get(player.id());
            if (label != null) label.setText(stateName(player.state()));
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
        result.setIcon(draw ? null : won ? WIN : LOSE);
        result.setText(draw ? "무승부" : "");
        result.getAccessibleContext().setAccessibleName(draw ? "무승부" : won ? "승리" : "패배");
        result.setVisible(true);
        leave.setEnabled(true);
        leave.requestFocusInWindow();
    }

    /** 실제 통신에는 경기만 포기하는 요청이 없으므로 결과 수신 후 대기실 복귀를 허용한다. */
    public void allowReturnAfterResult() {
        leave.setEnabled(false);
        leave.setToolTipText("경기가 끝나면 기존 대기실로 돌아갑니다.");
    }
}
