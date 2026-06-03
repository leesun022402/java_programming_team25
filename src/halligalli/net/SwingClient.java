package halligalli.net;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Swing GUI player client. Connects to the {@link GameServer} with the same protocol as
 * {@link GameClient}, renders the board graphically (each player's visible card, fruit
 * totals, chip pool), and plays via FLIP / BELL buttons.
 *
 * <p>Dependency-free: cards are drawn with custom Swing painting (no image assets).</p>
 */
public class SwingClient extends JFrame {

    private volatile String myName;
    private PrintWriter out;

    private volatile StateView state;
    private volatile boolean gameActive = false;

    private final JPanel playersPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 10));
    private final TablePanel tablePanel = new TablePanel();
    private final JLabel statusLabel = new JLabel("Connecting...", SwingConstants.CENTER);
    private final JButton flipButton = new JButton("FLIP (my turn)");
    private final JButton bellButton = new JButton("BELL!");
    private final JTextArea log = new JTextArea(7, 40);

    public SwingClient(String name) {
        super("Halli Galli Plus+ - " + name);
        this.myName = name;
        buildUi();
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(statusLabel, BorderLayout.NORTH);

        playersPanel.setBackground(new Color(0xF2, 0xEC, 0xE3));
        add(playersPanel, BorderLayout.CENTER);
        add(tablePanel, BorderLayout.EAST);

        // bottom: buttons + log
        JPanel south = new JPanel(new BorderLayout(6, 6));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 8));
        flipButton.setFont(flipButton.getFont().deriveFont(Font.BOLD, 15f));
        bellButton.setFont(bellButton.getFont().deriveFont(Font.BOLD, 18f));
        bellButton.setBackground(new Color(0xE7, 0x4C, 0x3C));
        bellButton.setForeground(Color.WHITE);
        flipButton.addActionListener(e -> send(Protocol.CMD_FLIP));
        bellButton.addActionListener(e -> send(Protocol.CMD_BELL));
        flipButton.setEnabled(false);
        bellButton.setEnabled(false);
        buttons.add(flipButton);
        buttons.add(bellButton);
        south.add(buttons, BorderLayout.NORTH);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        south.add(new JScrollPane(log), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setSize(820, 620);
        setLocationByPlatform(true);
    }

    // ------------------------------------------------------------ networking

    public void connect(String host, int port) throws Exception {
        Socket socket = new Socket(host, port);
        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(
                new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        out.println(Protocol.CMD_JOIN + " " + myName);

        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    handle(line);
                }
            } catch (Exception ignored) {
            }
            SwingUtilities.invokeLater(() -> statusLabel.setText("Disconnected"));
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void send(String cmd) {
        if (out != null) {
            out.println(cmd);
        }
    }

    private void handle(String line) {
        if (line.startsWith(Protocol.MSG_STATE)) {
            StateView v = StateView.parse(line);
            gameActive = true;
            SwingUtilities.invokeLater(() -> applyState(v));
        } else if (line.startsWith(Protocol.MSG_GAMEOVER)) {
            String winner = line.substring(Protocol.MSG_GAMEOVER.length()).trim();
            gameActive = false;
            SwingUtilities.invokeLater(() -> onGameOver(winner));
        } else if (line.startsWith(Protocol.MSG_WELCOME)) {
            String assignedName = stripType(line).trim();
            if (!assignedName.isEmpty()) {
                myName = assignedName;
                SwingUtilities.invokeLater(() -> setTitle("Halli Galli Plus+ - " + assignedName));
            }
            SwingUtilities.invokeLater(() -> appendLog("Welcome " + myName));
        } else {
            String body = stripType(line);
            SwingUtilities.invokeLater(() -> appendLog(body));
        }
    }

    private static String stripType(String line) {
        int sp = line.indexOf(' ');
        return sp < 0 ? line : line.substring(sp + 1);
    }

    // ------------------------------------------------------------ UI updates

    private void applyState(StateView v) {
        this.state = v;
        boolean myTurn = myName.equals(v.turn);

        statusLabel.setText(String.format("Turn: %s%s    House chips: %d    Chip symbols: %d/3",
                v.turn, v.bellable ? "    *** BELL AVAILABLE ***" : "", v.house, v.chipSymbols));
        statusLabel.setForeground(v.bellable ? new Color(0xC0, 0x39, 0x2B) : Color.DARK_GRAY);

        playersPanel.removeAll();
        for (StateView.PlayerInfo p : v.players) {
            playersPanel.add(new CardPanel(p, p.name.equals(v.turn), p.name.equals(myName)));
        }
        playersPanel.revalidate();
        playersPanel.repaint();

        tablePanel.setState(v);

        flipButton.setEnabled(gameActive && myTurn);
        bellButton.setEnabled(gameActive);
    }

    private void onGameOver(String winner) {
        statusLabel.setText("Game over! Winner: " + winner);
        statusLabel.setForeground(new Color(0x27, 0x7A, 0x27));
        flipButton.setEnabled(false);
        bellButton.setEnabled(false);
        appendLog("*** Game over! Winner: " + winner + " ***");
        JOptionPane.showMessageDialog(this, "Winner: " + winner, "Game Over",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void appendLog(String text) {
        log.append(text + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    // ------------------------------------------------------------ rendering helpers

    static Color fruitColor(String enumName) {
        if (enumName == null) {
            return Color.LIGHT_GRAY;
        }
        switch (enumName) {
            case "BANANA": return new Color(0xF1, 0xC4, 0x0F);
            case "STRAWBERRY": return new Color(0xE7, 0x4C, 0x3C);
            case "LIME": return new Color(0x2E, 0xCC, 0x71);
            case "PLUM": return new Color(0x8E, 0x44, 0xAD);
            default: return Color.GRAY;
        }
    }

    /** A single player's card + status. */
    static final class CardPanel extends JComponent {
        private final StateView.PlayerInfo p;
        private final boolean isTurn;
        private final boolean isSelf;

        CardPanel(StateView.PlayerInfo p, boolean isTurn, boolean isSelf) {
            this.p = p;
            this.isTurn = isTurn;
            this.isSelf = isSelf;
            setPreferredSize(new Dimension(150, 210));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // card body
            RoundRectangle2D body = new RoundRectangle2D.Float(4, 4, w - 8, h - 8, 18, 18);
            g2.setColor(Color.WHITE);
            g2.fill(body);
            g2.setStroke(new BasicStroke(isTurn ? 4f : 2f));
            g2.setColor(isTurn ? new Color(0xE6, 0x7E, 0x22) : new Color(0xBD, 0xC3, 0xC7));
            g2.draw(body);

            // visible card area
            int cardTop = 12;
            int cardH = h - 70;
            if (p.hasVisible()) {
                g2.setColor(fruitColor(p.visFruit));
                g2.fillRoundRect(16, cardTop, w - 32, cardH, 14, 14);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 40f));
                drawCentered(g2, "x" + p.visCount, w, cardTop + cardH / 2);
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                drawCenteredAt(g2, StateView.prettyFruit(p.visFruit), w / 2, cardTop + cardH - 14);
                if (p.visChip) {
                    g2.setColor(new Color(0x2C, 0x3E, 0x50));
                    g2.fillOval(w - 46, cardTop + 8, 26, 26);
                    g2.setColor(Color.WHITE);
                    g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
                    drawCenteredAt(g2, "C", w - 33, cardTop + 26);
                }
            } else {
                g2.setColor(new Color(0xEC, 0xF0, 0xF1));
                g2.fillRoundRect(16, cardTop, w - 32, cardH, 14, 14);
                g2.setColor(Color.GRAY);
                g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
                drawCenteredAt(g2, "no card", w / 2, cardTop + cardH / 2);
            }

            // name + counts
            g2.setColor(isSelf ? new Color(0x21, 0x60, 0xC0) : Color.DARK_GRAY);
            g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
            drawCenteredAt(g2, p.name + (isSelf ? " (you)" : ""), w / 2, h - 40);
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 13f));
            drawCenteredAt(g2, "cards " + p.cards + "   chips " + p.chips, w / 2, h - 20);

            // eliminated overlay
            if (!p.active) {
                g2.setColor(new Color(120, 120, 120, 150));
                g2.fill(body);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.BOLD, 30f));
                drawCentered(g2, "OUT", w, h / 2);
            }
            g2.dispose();
        }

        private void drawCentered(Graphics2D g2, String s, int w, int yCenter) {
            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(s)) / 2;
            g2.drawString(s, x, yCenter + fm.getAscent() / 2 - 2);
        }

        private void drawCenteredAt(Graphics2D g2, String s, int xCenter, int yBaseline) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, xCenter - fm.stringWidth(s) / 2, yBaseline);
        }
    }

    /** Side panel showing the aggregated table (fruit totals, chip pool, bell state). */
    static final class TablePanel extends JComponent {
        private StateView state;

        TablePanel() {
            setPreferredSize(new Dimension(220, 0));
        }

        void setState(StateView s) {
            this.state = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            g2.setColor(new Color(0xFA, 0xFA, 0xFA));
            g2.fillRect(0, 0, w, getHeight());

            g2.setColor(Color.DARK_GRAY);
            g2.setFont(getFont().deriveFont(Font.BOLD, 16f));
            g2.drawString("TABLE", 16, 28);

            int y = 60;
            if (state != null) {
                g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
                for (Map_Entry e : entries(state)) {
                    g2.setColor(fruitColor(e.key));
                    g2.fillRoundRect(16, y - 12, 16, 16, 4, 4);
                    g2.setColor(Color.DARK_GRAY);
                    g2.drawString(StateView.prettyFruit(e.key) + ": " + e.val
                            + (e.val == 5 ? "  (5!)" : ""), 40, y);
                    y += 26;
                }
                y += 10;
                g2.setColor(Color.DARK_GRAY);
                g2.drawString("Chip symbols: " + state.chipSymbols + "/3", 16, y);
                y += 26;
                g2.drawString("House chips: " + state.house, 16, y);
                y += 40;

                if (state.bellable) {
                    g2.setColor(new Color(0xE7, 0x4C, 0x3C));
                    g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
                    g2.drawString("BELL!", 16, y);
                }
            }
            g2.dispose();
        }

        // tiny helpers to iterate the fruit map in a typed way
        private static final class Map_Entry {
            final String key;
            final int val;
            Map_Entry(String k, int v) { key = k; val = v; }
        }

        private java.util.List<Map_Entry> entries(StateView s) {
            java.util.List<Map_Entry> list = new java.util.ArrayList<>();
            s.fruits.forEach((k, v) -> list.add(new Map_Entry(k, v)));
            return list;
        }
    }

    // --------------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        String name = args.length > 2 ? args[2] : "Player";

        SwingClient client = new SwingClient(name);
        SwingUtilities.invokeLater(() -> client.setVisible(true));
        client.connect(host, port);
    }
}
