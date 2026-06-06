package halligalli.net;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Writes one socket game as a replay-friendly JSON event stream. */
public final class GameLogRecorder implements AutoCloseable {

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneId.systemDefault());

    private final Path path;
    private final PrintWriter out;
    private boolean firstEvent = true;
    private boolean closed = false;

    private GameLogRecorder(Path path, int port, int expectedPlayers,
                            long seed, String botDifficulty) throws IOException {
        this.path = path;
        this.out = new PrintWriter(new BufferedWriter(Files.newBufferedWriter(path)));
        out.println("{");
        out.println("  \"version\": 1,");
        out.println("  \"startedAt\": \"" + escape(Instant.now().toString()) + "\",");
        out.println("  \"port\": " + port + ",");
        out.println("  \"expectedPlayers\": " + expectedPlayers + ",");
        out.println("  \"seed\": " + seed + ",");
        out.println("  \"botDifficulty\": "
                + (botDifficulty == null || botDifficulty.isBlank()
                ? "null" : "\"" + escape(botDifficulty.trim()) + "\"") + ",");
        out.println("  \"events\": [");
        out.flush();
    }

    public static GameLogRecorder createIfEnabled(GameSettings settings, int port,
                                                  int expectedPlayers, long seed) {
        return createIfEnabled(settings, port, expectedPlayers, seed,
                settings != null ? settings.botDifficulty() : null);
    }

    public static GameLogRecorder createIfEnabled(GameSettings settings, int port,
                                                  int expectedPlayers, long seed,
                                                  String botDifficulty) {
        if (settings == null || !settings.enableJsonLogs()) {
            return null;
        }
        try {
            Path directory = Path.of(settings.logDirectory());
            Files.createDirectories(directory);
            Path path = directory.resolve("game-" + FILE_TIME.format(Instant.now()) + ".json");
            GameLogRecorder recorder = new GameLogRecorder(path, port, expectedPlayers, seed,
                    botDifficulty);
            recorder.record("LOG_START", "JSON logging started", null);
            return recorder;
        } catch (IOException e) {
            System.err.println("[log] could not start JSON log: " + e.getMessage());
            return null;
        }
    }

    public Path path() {
        return path;
    }

    public synchronized void record(String type, String message, String state) {
        if (closed) {
            return;
        }
        if (!firstEvent) {
            out.println(",");
        }
        firstEvent = false;
        out.print("    {\"time\":\"" + escape(Instant.now().toString())
                + "\",\"type\":\"" + escape(type) + "\"");
        if (message != null) {
            out.print(",\"message\":\"" + escape(message) + "\"");
        }
        if (state != null) {
            out.print(",\"state\":\"" + escape(state) + "\"");
        }
        out.print("}");
        out.flush();
    }

    public synchronized void finish(String status, String winner) {
        if (closed) {
            return;
        }
        if (!firstEvent) {
            out.println();
        }
        out.println("  ],");
        out.println("  \"endedAt\": \"" + escape(Instant.now().toString()) + "\",");
        out.println("  \"status\": \"" + escape(status != null ? status : "closed") + "\",");
        out.println("  \"winner\": " + (winner == null ? "null" : "\"" + escape(winner) + "\""));
        out.println("}");
        closed = true;
        out.close();
    }

    @Override
    public void close() {
        finish("closed", null);
    }

    private static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
