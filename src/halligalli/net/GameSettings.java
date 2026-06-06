package halligalli.net;

import halligalli.core.GameRules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime defaults loaded from config/game_settings.json. */
public final class GameSettings {

    private static final Path DEFAULT_PATH = Path.of("config", "game_settings.json");

    private final int defaultPort;
    private final String defaultPlayerName;
    private final int totalPlayers;
    private final int botPlayers;
    private final String botDifficulty;
    private final long seed;
    private final boolean enableJsonLogs;
    private final String logDirectory;
    private final String databasePath;
    private final int fruitBellThreshold;
    private final int chipBellThreshold;
    private final int reviveCardsPerPlayer;

    public GameSettings(int defaultPort, String defaultPlayerName, int totalPlayers,
                        int botPlayers, String botDifficulty, long seed,
                        boolean enableJsonLogs, String logDirectory,
                        String databasePath,
                        int fruitBellThreshold, int chipBellThreshold,
                        int reviveCardsPerPlayer) {
        this.defaultPort = clamp(defaultPort, 1024, 65535);
        this.defaultPlayerName = blankToDefault(defaultPlayerName, "Player");
        this.totalPlayers = Math.max(2, totalPlayers);
        this.botPlayers = Math.max(0, Math.min(botPlayers, this.totalPlayers - 1));
        this.botDifficulty = normalizeDifficulty(botDifficulty);
        this.seed = seed;
        this.enableJsonLogs = enableJsonLogs;
        this.logDirectory = blankToDefault(logDirectory, "logs");
        this.databasePath = blankToDefault(databasePath, "data/halligalli_stats.db");
        this.fruitBellThreshold = Math.max(1, fruitBellThreshold);
        this.chipBellThreshold = Math.max(1, chipBellThreshold);
        this.reviveCardsPerPlayer = Math.max(1, reviveCardsPerPlayer);
    }

    public static GameSettings loadDefault() {
        return load(DEFAULT_PATH);
    }

    public static GameSettings load(Path path) {
        GameSettings defaults = defaults();
        if (path == null || !Files.exists(path)) {
            return defaults;
        }
        try {
            return fromJson(Files.readString(path), defaults);
        } catch (IOException e) {
            System.err.println("[settings] could not read " + path + ": " + e.getMessage());
            return defaults;
        }
    }

    public static GameSettings fromJson(String json, GameSettings defaults) {
        if (json == null || json.isBlank()) {
            return defaults;
        }
        return new GameSettings(
                intValue(json, "defaultPort", defaults.defaultPort),
                stringValue(json, "defaultPlayerName", defaults.defaultPlayerName),
                intValue(json, "totalPlayers", defaults.totalPlayers),
                intValue(json, "botPlayers", defaults.botPlayers),
                stringValue(json, "botDifficulty", defaults.botDifficulty),
                longValue(json, "seed", defaults.seed),
                booleanValue(json, "enableJsonLogs", defaults.enableJsonLogs),
                stringValue(json, "logDirectory", defaults.logDirectory),
                stringValue(json, "databasePath", defaults.databasePath),
                intValue(json, "fruitBellThreshold", defaults.fruitBellThreshold),
                intValue(json, "chipBellThreshold", defaults.chipBellThreshold),
                intValue(json, "reviveCardsPerPlayer", defaults.reviveCardsPerPlayer)
        );
    }

    public static GameSettings defaults() {
        return new GameSettings(Protocol.DEFAULT_PORT, "Player", 2, 1, "easy",
                25L, true, "logs", "data/halligalli_stats.db", 5, 3, 3);
    }

    public int defaultPort() {
        return defaultPort;
    }

    public String defaultPlayerName() {
        return defaultPlayerName;
    }

    public int totalPlayers() {
        return totalPlayers;
    }

    public int botPlayers() {
        return botPlayers;
    }

    public String botDifficulty() {
        return botDifficulty;
    }

    public long seed() {
        return seed;
    }

    public boolean enableJsonLogs() {
        return enableJsonLogs;
    }

    public String logDirectory() {
        return logDirectory;
    }

    public String databasePath() {
        return databasePath;
    }

    public int fruitBellThreshold() {
        return fruitBellThreshold;
    }

    public int chipBellThreshold() {
        return chipBellThreshold;
    }

    public int reviveCardsPerPlayer() {
        return reviveCardsPerPlayer;
    }

    public GameRules toGameRules() {
        return new GameRules(fruitBellThreshold, chipBellThreshold, reviveCardsPerPlayer);
    }

    private static String stringValue(String json, String key, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : fallback;
    }

    private static int intValue(String json, String key, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long longValue(String json, String key, long fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean booleanValue(String json, String key, boolean fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE).matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
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

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeDifficulty(String value) {
        String normalized = blankToDefault(value, "normal").toLowerCase(Locale.ROOT);
        return normalized.equals("easy") || normalized.equals("normal")
                || normalized.equals("hard") || normalized.equals("fast")
                ? normalized : "normal";
    }
}
