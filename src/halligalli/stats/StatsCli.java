package halligalli.stats;

import halligalli.net.GameSettings;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Command-line entry point for importing JSON logs into SQLite and printing standings. */
public final class StatsCli {

    private StatsCli() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        String command = args.length == 0 ? "summary" : args[0];
        GameSettings settings = GameSettings.loadDefault();
        Path dbPath = Path.of(settings.databasePath());

        try (StatsDatabase db = StatsDatabase.open(dbPath)) {
            switch (command) {
                case "import":
                    importLogs(db, args);
                    break;
                case "summary":
                    db.printSummary(System.out);
                    break;
                case "games":
                    db.printGames(System.out, args.length > 1 ? Integer.parseInt(args[1]) : 20);
                    break;
                default:
                    usage();
                    break;
            }
        }
    }

    private static void importLogs(StatsDatabase db, String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            return;
        }

        String botDifficulty = null;
        List<Path> logs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if ("--difficulty".equals(arg) || "-d".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--difficulty requires a value");
                }
                botDifficulty = args[++i];
            } else if (i == args.length - 1 && !arg.endsWith(".json") && botDifficulty == null) {
                botDifficulty = arg;
            } else {
                logs.add(Path.of(arg));
            }
        }

        if (logs.isEmpty()) {
            usage();
            return;
        }

        for (Path log : logs) {
            GameLogStats stats = GameLogStats.parse(log);
            long gameId = db.importGame(stats, botDifficulty);
            System.out.println("Imported game #" + gameId + " from " + log
                    + " (winner=" + stats.winner()
                    + ", turns=" + stats.turnCount()
                    + ", botDifficulty=" + stats.effectiveBotDifficulty(botDifficulty) + ")");
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  ./run.sh stats-import <log.json> [--difficulty fast]");
        System.out.println("  ./run.sh stats");
        System.out.println("  ./run.sh stats games [limit]");
    }
}
