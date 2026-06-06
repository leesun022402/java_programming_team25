package halligalli.stats;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;

/** JDBC facade for the SQLite Halli Galli Plus+ statistics database. */
public final class StatsDatabase implements AutoCloseable {

    private final Path dbPath;
    private final Connection conn;

    private StatsDatabase(Path dbPath, Connection conn) {
        this.dbPath = dbPath;
        this.conn = conn;
    }

    public static StatsDatabase open(Path dbPath) throws SQLException, IOException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found. "
                    + "Place sqlite-jdbc-*.jar under lib/ and run again.", e);
        }

        Path parent = dbPath.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        StatsDatabase db = new StatsDatabase(dbPath, conn);
        db.initialize();
        return db;
    }

    private void initialize() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("PRAGMA foreign_keys = ON");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS players ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT NOT NULL UNIQUE,"
                    + "is_bot INTEGER NOT NULL DEFAULT 0"
                    + ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS games ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "log_file TEXT NOT NULL UNIQUE,"
                    + "started_at TEXT,"
                    + "ended_at TEXT,"
                    + "status TEXT,"
                    + "seed INTEGER,"
                    + "port INTEGER,"
                    + "expected_players INTEGER,"
                    + "bot_difficulty TEXT,"
                    + "turn_count INTEGER NOT NULL,"
                    + "winner_player_id INTEGER,"
                    + "bell_success_count INTEGER NOT NULL,"
                    + "false_bell_count INTEGER NOT NULL,"
                    + "imported_at TEXT NOT NULL,"
                    + "FOREIGN KEY(winner_player_id) REFERENCES players(id)"
                    + ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS player_game_stats ("
                    + "game_id INTEGER NOT NULL,"
                    + "player_id INTEGER NOT NULL,"
                    + "is_bot INTEGER NOT NULL,"
                    + "bot_difficulty TEXT,"
                    + "won INTEGER NOT NULL,"
                    + "flips INTEGER NOT NULL,"
                    + "bell_success INTEGER NOT NULL,"
                    + "false_bells INTEGER NOT NULL,"
                    + "final_cards INTEGER,"
                    + "final_chips INTEGER,"
                    + "PRIMARY KEY(game_id, player_id),"
                    + "FOREIGN KEY(game_id) REFERENCES games(id) ON DELETE CASCADE,"
                    + "FOREIGN KEY(player_id) REFERENCES players(id)"
                    + ")");
        }
    }

    public long importGame(GameLogStats stats, String botDifficultyOverride) throws SQLException {
        String logFile = stats.logPath().toAbsolutePath().normalize().toString();
        String botDifficulty = stats.effectiveBotDifficulty(botDifficultyOverride);

        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Long existingGameId = findGameId(logFile);
            if (existingGameId != null) {
                deleteGame(existingGameId);
            }

            Long winnerId = null;
            if (stats.winner() != null && !"none".equals(stats.winner())) {
                GameLogStats.PlayerStats winnerStats = findPlayerStats(stats, stats.winner());
                winnerId = upsertPlayer(stats.winner(), winnerStats != null && winnerStats.bot);
            }

            long gameId = insertGame(stats, logFile, botDifficulty, winnerId);
            for (GameLogStats.PlayerStats ps : stats.players()) {
                long playerId = upsertPlayer(ps.name, ps.bot);
                insertPlayerGameStats(gameId, playerId, ps, botDifficulty);
            }

            conn.commit();
            return gameId;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    private GameLogStats.PlayerStats findPlayerStats(GameLogStats stats, String name) {
        for (GameLogStats.PlayerStats ps : stats.players()) {
            if (ps.name.equals(name)) {
                return ps;
            }
        }
        return null;
    }

    private Long findGameId(String logFile) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM games WHERE log_file = ?")) {
            ps.setString(1, logFile);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private void deleteGame(long gameId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM player_game_stats WHERE game_id = ?")) {
            ps.setLong(1, gameId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM games WHERE id = ?")) {
            ps.setLong(1, gameId);
            ps.executeUpdate();
        }
    }

    private long upsertPlayer(String name, boolean bot) throws SQLException {
        try (PreparedStatement find = conn.prepareStatement("SELECT id, is_bot FROM players WHERE name = ?")) {
            find.setString(1, name);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    if (bot && rs.getInt("is_bot") == 0) {
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE players SET is_bot = 1 WHERE id = ?")) {
                            update.setLong(1, id);
                            update.executeUpdate();
                        }
                    }
                    return id;
                }
            }
        }

        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO players(name, is_bot) VALUES(?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setInt(2, bot ? 1 : 0);
            insert.executeUpdate();
            return generatedId(insert);
        }
    }

    private long insertGame(GameLogStats stats, String logFile,
                            String botDifficulty, Long winnerId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO games(log_file, started_at, ended_at, status, seed, port,"
                        + " expected_players, bot_difficulty, turn_count, winner_player_id,"
                        + " bell_success_count, false_bell_count, imported_at)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, logFile);
            setNullableString(ps, 2, stats.startedAt());
            setNullableString(ps, 3, stats.endedAt());
            setNullableString(ps, 4, stats.status());
            ps.setLong(5, stats.seed());
            ps.setInt(6, stats.port());
            ps.setInt(7, stats.expectedPlayers());
            setNullableString(ps, 8, botDifficulty);
            ps.setInt(9, stats.turnCount());
            if (winnerId == null) {
                ps.setNull(10, Types.INTEGER);
            } else {
                ps.setLong(10, winnerId);
            }
            ps.setInt(11, stats.bellSuccessCount());
            ps.setInt(12, stats.falseBellCount());
            ps.setString(13, Instant.now().toString());
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    private void insertPlayerGameStats(long gameId, long playerId,
                                       GameLogStats.PlayerStats ps,
                                       String botDifficulty) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO player_game_stats(game_id, player_id, is_bot, bot_difficulty,"
                        + " won, flips, bell_success, false_bells, final_cards, final_chips)"
                        + " VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, gameId);
            stmt.setLong(2, playerId);
            stmt.setInt(3, ps.bot ? 1 : 0);
            setNullableString(stmt, 4, ps.bot ? botDifficulty : null);
            stmt.setInt(5, ps.won ? 1 : 0);
            stmt.setInt(6, ps.flips);
            stmt.setInt(7, ps.bellSuccess);
            stmt.setInt(8, ps.falseBells);
            setNullableInt(stmt, 9, ps.finalCards);
            setNullableInt(stmt, 10, ps.finalChips);
            stmt.executeUpdate();
        }
    }

    public void printSummary(PrintStream out) throws SQLException {
        out.println("=== Halli Galli Plus+ Player Stats ===");
        out.println("DB: " + dbPath);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT p.name AS name,"
                             + " COUNT(*) AS games,"
                             + " SUM(s.won) AS wins,"
                             + " ROUND(100.0 * SUM(s.won) / COUNT(*), 1) AS win_rate,"
                             + " ROUND(AVG(g.turn_count), 1) AS avg_turns,"
                             + " SUM(s.flips) AS flips,"
                             + " SUM(s.bell_success) AS bell_success,"
                             + " SUM(s.false_bells) AS false_bells"
                             + " FROM player_game_stats s"
                             + " JOIN players p ON p.id = s.player_id"
                             + " JOIN games g ON g.id = s.game_id"
                             + " GROUP BY p.id, p.name"
                             + " ORDER BY wins DESC, games DESC, p.name")) {
            boolean any = false;
            out.printf("%-16s %5s %5s %7s %10s %7s %8s %8s%n",
                    "Player", "Games", "Wins", "Win%", "AvgTurns", "Flips", "BellOK", "False");
            while (rs.next()) {
                any = true;
                out.printf("%-16s %5d %5d %6.1f%% %10.1f %7d %8d %8d%n",
                        rs.getString("name"),
                        rs.getInt("games"),
                        rs.getInt("wins"),
                        rs.getDouble("win_rate"),
                        rs.getDouble("avg_turns"),
                        rs.getInt("flips"),
                        rs.getInt("bell_success"),
                        rs.getInt("false_bells"));
            }
            if (!any) {
                out.println("(no imported games yet)");
            }
        }

        out.println();
        out.println("=== Bot Difficulty Win Rate ===");
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COALESCE(NULLIF(s.bot_difficulty, ''), 'unknown') AS difficulty,"
                             + " COUNT(*) AS games,"
                             + " SUM(s.won) AS wins,"
                             + " ROUND(100.0 * SUM(s.won) / COUNT(*), 1) AS win_rate"
                             + " FROM player_game_stats s"
                             + " WHERE s.is_bot = 1"
                             + " GROUP BY COALESCE(NULLIF(s.bot_difficulty, ''), 'unknown')"
                             + " ORDER BY wins DESC, games DESC")) {
            boolean any = false;
            out.printf("%-12s %5s %5s %7s%n", "Difficulty", "Games", "Wins", "Win%");
            while (rs.next()) {
                any = true;
                out.printf("%-12s %5d %5d %6.1f%%%n",
                        rs.getString("difficulty"),
                        rs.getInt("games"),
                        rs.getInt("wins"),
                        rs.getDouble("win_rate"));
            }
            if (!any) {
                out.println("(no bot games yet)");
            }
        }
    }

    public void printGames(PrintStream out, int limit) throws SQLException {
        out.println("=== Imported Games ===");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT g.id, g.started_at, g.turn_count, g.bot_difficulty,"
                        + " p.name AS winner, g.log_file"
                        + " FROM games g"
                        + " LEFT JOIN players p ON p.id = g.winner_player_id"
                        + " ORDER BY g.id DESC LIMIT ?")) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                boolean any = false;
                out.printf("%4s %-24s %7s %-12s %-16s %s%n",
                        "ID", "Started", "Turns", "Difficulty", "Winner", "Log");
                while (rs.next()) {
                    any = true;
                    out.printf("%4d %-24s %7d %-12s %-16s %s%n",
                            rs.getLong("id"),
                            safe(rs.getString("started_at")),
                            rs.getInt("turn_count"),
                            safe(rs.getString("bot_difficulty")),
                            safe(rs.getString("winner")),
                            Path.of(rs.getString("log_file")).getFileName());
                }
                if (!any) {
                    out.println("(no imported games yet)");
                }
            }
        }
    }

    private static long generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) {
                throw new SQLException("No generated key returned.");
            }
            return rs.getLong(1);
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value)
            throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int index, int value)
            throws SQLException {
        if (value < 0) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String safe(String value) {
        return value == null ? "-" : value;
    }

    @Override
    public void close() throws SQLException {
        conn.close();
    }
}
