package halligalli.stats;

import halligalli.net.StateView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed statistics from one JSON game log. */
public final class GameLogStats {

    private final Path logPath;
    private final Map<String, PlayerStats> players = new LinkedHashMap<>();

    private String startedAt;
    private String endedAt;
    private String status;
    private String winner;
    private String botDifficulty;
    private int port;
    private int expectedPlayers;
    private long seed;
    private int turnCount;
    private int bellSuccessCount;
    private int falseBellCount;

    private GameLogStats(Path logPath) {
        this.logPath = logPath;
    }

    public static GameLogStats parse(Path logPath) throws IOException {
        GameLogStats stats = new GameLogStats(logPath);
        for (String line : Files.readAllLines(logPath)) {
            stats.parseLine(line);
        }
        if (stats.winner == null || stats.winner.isBlank()) {
            stats.winner = "none";
        }
        PlayerStats winnerStats = stats.players.get(stats.winner);
        if (winnerStats != null) {
            winnerStats.won = true;
        }
        return stats;
    }

    private void parseLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("\"startedAt\"")) {
            startedAt = stringField(line, "startedAt");
        } else if (trimmed.startsWith("\"endedAt\"")) {
            endedAt = stringField(line, "endedAt");
        } else if (trimmed.startsWith("\"status\"")) {
            status = stringField(line, "status");
        } else if (trimmed.startsWith("\"winner\"")) {
            winner = stringField(line, "winner");
        } else if (trimmed.startsWith("\"botDifficulty\"")) {
            botDifficulty = stringField(line, "botDifficulty");
        } else if (trimmed.startsWith("\"port\"")) {
            port = intField(line, "port");
        } else if (trimmed.startsWith("\"expectedPlayers\"")) {
            expectedPlayers = intField(line, "expectedPlayers");
        } else if (trimmed.startsWith("\"seed\"")) {
            seed = longField(line, "seed");
        }

        String state = stringField(line, "state");
        if (state != null) {
            observeState(state);
        }

        String type = stringField(line, "type");
        String message = stringField(line, "message");
        if (type == null || message == null) {
            return;
        }
        if ("COMMAND".equals(type)) {
            observeCommand(message);
        } else if ("EVENT".equals(type)) {
            observeEvent(message);
        } else if ("GAMEOVER".equals(type) && message.startsWith("Winner:")) {
            winner = message.substring("Winner:".length()).trim();
        }
    }

    private void observeState(String state) {
        StateView view = StateView.parse(state);
        for (StateView.PlayerInfo p : view.players) {
            PlayerStats ps = player(p.name);
            ps.bot = isBotName(p.name);
            ps.finalCards = p.cards;
            ps.finalChips = p.chips;
        }
    }

    private void observeCommand(String message) {
        int arrow = message.indexOf(" -> ");
        if (arrow < 0) {
            return;
        }
        String name = message.substring(0, arrow).trim();
        String command = message.substring(arrow + 4).trim();
        if (name.isBlank() || name.startsWith("(")) {
            return;
        }
        PlayerStats ps = player(name);
        if ("FLIP".equals(command)) {
            ps.flips++;
            turnCount++;
        }
    }

    private void observeEvent(String message) {
        int success = message.indexOf(" rings!");
        if (success > 0) {
            PlayerStats ps = player(message.substring(0, success).trim());
            ps.bellSuccess++;
            bellSuccessCount++;
            return;
        }

        int falseBell = message.indexOf(" false bell!");
        if (falseBell > 0) {
            PlayerStats ps = player(message.substring(0, falseBell).trim());
            ps.falseBells++;
            falseBellCount++;
        }
    }

    private PlayerStats player(String name) {
        return players.computeIfAbsent(name, PlayerStats::new);
    }

    public String effectiveBotDifficulty(String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        if (botDifficulty != null && !botDifficulty.isBlank()) {
            return botDifficulty.trim().toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }

    public Path logPath() {
        return logPath;
    }

    public Collection<PlayerStats> players() {
        return players.values();
    }

    public String startedAt() {
        return startedAt;
    }

    public String endedAt() {
        return endedAt;
    }

    public String status() {
        return status;
    }

    public String winner() {
        return winner;
    }

    public int port() {
        return port;
    }

    public int expectedPlayers() {
        return expectedPlayers;
    }

    public long seed() {
        return seed;
    }

    public int turnCount() {
        return turnCount;
    }

    public int bellSuccessCount() {
        return bellSuccessCount;
    }

    public int falseBellCount() {
        return falseBellCount;
    }

    private static boolean isBotName(String name) {
        return name != null && name.toUpperCase(Locale.ROOT).startsWith("BOT");
    }

    private static String stringField(String line, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(line);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static int intField(String line, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(-?\\d+)").matcher(line);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static long longField(String line, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(-?\\d+)").matcher(line);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    private static String unescape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (escaped) {
                switch (ch) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(ch); break;
                }
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static final class PlayerStats {
        public final String name;
        public boolean bot;
        public boolean won;
        public int flips;
        public int bellSuccess;
        public int falseBells;
        public int finalCards = -1;
        public int finalChips = -1;

        private PlayerStats(String name) {
            this.name = name;
            this.bot = isBotName(name);
        }
    }
}
