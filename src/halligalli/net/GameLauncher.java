package halligalli.net;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/** GUI launcher for hosting or joining a socket game without typing every terminal command. */
public class GameLauncher extends JFrame {

    private final GameSettings settings;
    private final JTextField hostName;
    private final JSpinner hostPort;
    private final JSpinner totalPlayers;
    private final JSpinner botPlayers;
    private final JComboBox<String> botLevel;
    private final JTextField seed;
    private final JLabel waitingHumans = new JLabel();
    private final JButton hostButton = new JButton("Host and Play");

    private final JTextField joinName;
    private final JTextField joinHost = new JTextField("localhost", 14);
    private final JSpinner joinPort;
    private final JButton joinButton = new JButton("Join Game");

    private final JTextArea log = new JTextArea(7, 48);

    public GameLauncher() {
        this(GameSettings.loadDefault());
    }

    GameLauncher(GameSettings settings) {
        super("Halli Galli Plus+ Launcher");
        this.settings = settings != null ? settings : GameSettings.defaults();

        int configuredTotal = Math.max(2, Math.min(8, this.settings.totalPlayers()));
        int configuredBots = Math.max(0, Math.min(configuredTotal - 1, this.settings.botPlayers()));
        this.hostName = new JTextField(this.settings.defaultPlayerName(), 14);
        this.hostPort = new JSpinner(new SpinnerNumberModel(this.settings.defaultPort(), 1024, 65535, 1));
        this.totalPlayers = new JSpinner(new SpinnerNumberModel(configuredTotal, 2, 8, 1));
        this.botPlayers = new JSpinner(new SpinnerNumberModel(configuredBots, 0, configuredTotal - 1, 1));
        this.botLevel = new JComboBox<>(new String[]{"easy", "normal", "hard", "fast"});
        this.botLevel.setSelectedItem(this.settings.botDifficulty());
        this.seed = new JTextField(String.valueOf(this.settings.seed()), 8);

        this.joinName = new JTextField(this.settings.defaultPlayerName(), 14);
        this.joinPort = new JSpinner(new SpinnerNumberModel(this.settings.defaultPort(), 1024, 65535, 1));

        buildUi();
        appendLog("Loaded config/game_settings.json");
        appendLog("JSON logs: " + (this.settings.enableJsonLogs()
                ? this.settings.logDirectory() : "disabled"));
    }

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(true);
        setLayout(new BorderLayout(8, 8));

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Host", buildHostPanel());
        tabs.addTab("Join", buildJoinPanel());

        log.setEditable(false);
        JScrollPane logScroll = new JScrollPane(log);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tabs, logScroll);
        split.setContinuousLayout(true);
        split.setResizeWeight(0.72);
        split.setOneTouchExpandable(true);
        add(split, BorderLayout.CENTER);

        ChangeListener update = e -> updateBotLimits();
        totalPlayers.addChangeListener(update);
        botPlayers.addChangeListener(update);
        updateBotLimits();

        hostButton.addActionListener(e -> hostGame());
        joinButton.addActionListener(e -> joinGame());

        setPreferredSize(new Dimension(560, 460));
        pack();
        split.setDividerLocation(0.72);
        setMinimumSize(new Dimension(340, 260));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        setLocationByPlatform(true);
    }

    private JPanel buildHostPanel() {
        JPanel panel = formPanel();
        int y = 0;
        addRow(panel, y++, "Your name", hostName);
        addRow(panel, y++, "Port", hostPort);
        addRow(panel, y++, "Total players", totalPlayers);
        addRow(panel, y++, "Bot players", botPlayers);
        addRow(panel, y++, "Bot difficulty", botLevel);
        addRow(panel, y++, "Seed", seed);
        addRow(panel, y++, "Waiting humans", waitingHumans);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(hostButton);
        addRow(panel, y, "", buttons);
        return panel;
    }

    private JPanel buildJoinPanel() {
        JPanel panel = formPanel();
        int y = 0;
        addRow(panel, y++, "Your name", joinName);
        addRow(panel, y++, "Host", joinHost);
        addRow(panel, y++, "Port", joinPort);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(joinButton);
        addRow(panel, y, "", buttons);
        return panel;
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return panel;
    }

    private void addRow(JPanel panel, int y, String label, java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.gridy = y;
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private void updateBotLimits() {
        int total = intValue(totalPlayers);
        SpinnerNumberModel model = (SpinnerNumberModel) botPlayers.getModel();
        int maxBots = total - 1;
        model.setMaximum(maxBots);
        if (intValue(botPlayers) > maxBots) {
            botPlayers.setValue(maxBots);
        }
        int humansWaiting = total - intValue(botPlayers) - 1;
        waitingHumans.setText(humansWaiting == 0 ? "none" : String.valueOf(humansWaiting));
    }

    private void hostGame() {
        int port = intValue(hostPort);
        int total = intValue(totalPlayers);
        int bots = intValue(botPlayers);
        String name = textOrDefault(hostName, "Player");
        String level = (String) botLevel.getSelectedItem();
        long seedValue = seedValue();

        hostButton.setEnabled(false);
        appendLog("Starting local server on port " + port + " for " + total + " player(s)...");

        Thread launcher = new Thread(() -> {
            AtomicReference<Exception> serverFailure = new AtomicReference<>();
            Thread serverThread = new Thread(() -> {
                GameLogRecorder recorder = GameLogRecorder.createIfEnabled(settings, port, total,
                        seedValue, level);
                if (recorder != null) {
                    appendLog("JSON log: " + recorder.path());
                }
                try {
                    new GameServer(port, total, new Random(seedValue), true,
                            recorder, settings.toGameRules()).start();
                } catch (Exception e) {
                    serverFailure.set(e);
                    appendLog("Server stopped: " + e.getMessage());
                }
            }, "hgp-server");
            serverThread.setDaemon(true);
            serverThread.start();

            sleep(600);
            if (serverFailure.get() != null) {
                showError("Could not start server", serverFailure.get());
                SwingUtilities.invokeLater(() -> hostButton.setEnabled(true));
                return;
            }

            launchSwingClient("localhost", port, name);

            for (int i = 1; i <= bots; i++) {
                int botNo = i;
                sleep(180);
                launchBot("localhost", port, "BOT" + botNo, seedValue + botNo, level);
            }

            int humansWaiting = total - bots - 1;
            if (humansWaiting > 0) {
                appendLog("Waiting for " + humansWaiting + " more human client(s) to join.");
            }
        }, "hgp-launcher");
        launcher.setDaemon(true);
        launcher.start();
    }

    private void joinGame() {
        launchSwingClient(textOrDefault(joinHost, "localhost"), intValue(joinPort),
                textOrDefault(joinName, "Player"));
    }

    private void launchSwingClient(String host, int port, String name) {
        SwingUtilities.invokeLater(() -> {
            SwingClient client = new SwingClient(name);
            client.setVisible(true);

            Thread connector = new Thread(() -> {
                Exception last = null;
                for (int i = 0; i < 20; i++) {
                    try {
                        client.connect(host, port);
                        appendLog("Connected " + name + " to " + host + ":" + port);
                        return;
                    } catch (Exception e) {
                        last = e;
                        sleep(150);
                    }
                }
                client.dispose();
                showError("Could not connect GUI client", last);
            }, "hgp-gui-connect");
            connector.setDaemon(true);
            connector.start();
        });
    }

    private void launchBot(String host, int port, String name, long botSeed, String level) {
        Thread botThread = new Thread(() -> {
            Exception last = null;
            for (int i = 0; i < 20; i++) {
                try {
                    appendLog("Starting " + name + " (" + level + ")");
                    new BotClient(name, botSeed, level, false).connect(host, port);
                    return;
                } catch (Exception e) {
                    last = e;
                    sleep(150);
                }
            }
            showError("Could not connect " + name, last);
        }, "hgp-" + name);
        botThread.setDaemon(true);
        botThread.start();
    }

    private int intValue(JSpinner spinner) {
        return ((Number) spinner.getValue()).intValue();
    }

    private long seedValue() {
        String raw = seed.getText().trim();
        if (raw.isEmpty()) {
            return System.currentTimeMillis();
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw.hashCode();
        }
    }

    private String textOrDefault(JTextField field, String fallback) {
        String value = field.getText().trim();
        return value.isEmpty() ? fallback : value;
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    private void showError(String title, Exception e) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                e != null ? e.getMessage() : "Unknown error", title, JOptionPane.ERROR_MESSAGE));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameLauncher().setVisible(true));
    }
}
