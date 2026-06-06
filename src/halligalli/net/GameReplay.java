package halligalli.net;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Simple terminal viewer for JSON logs written by GameServer. */
public final class GameReplay {

    private static final Pattern STRING_FIELD =
            Pattern.compile("\"%s\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    private GameReplay() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Usage: ./run.sh replay logs/game-YYYYMMDD-HHMMSS-SSS.json");
            return;
        }

        Path path = Path.of(args[0]);
        System.out.println("=== Replay: " + path + " ===");
        for (String line : Files.readAllLines(path)) {
            String type = field(line, "type");
            if (type == null) {
                continue;
            }
            String message = field(line, "message");
            String state = field(line, "state");
            if (message != null && !message.isBlank()) {
                System.out.println("[" + type + "] " + message);
            }
            if ("STATE".equals(type) && state != null) {
                System.out.println(StateView.parse(state).render());
            }
        }
    }

    private static String field(String line, String key) {
        Matcher matcher = Pattern.compile(String.format(STRING_FIELD.pattern(), Pattern.quote(key)))
                .matcher(line);
        return matcher.find() ? unescape(matcher.group(1)) : null;
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
}
