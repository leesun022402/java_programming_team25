package halligalli.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * The console client used by a human.
 * Runs a thread that reads and prints server messages, plus a main thread that reads
 * stdin and sends commands.
 *
 * <p>Input: {@code f}/{@code flip} -> FLIP, {@code b}/{@code bell} -> BELL, {@code q} -> quit.</p>
 */
public class GameClient {

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        String name = args.length > 2 ? args[2] : "Player";

        try (Socket socket = new Socket(host, port)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // server-message reader thread
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        printServerMessage(line);
                    }
                } catch (Exception ignored) {
                }
                System.out.println("[disconnected]");
                System.exit(0);
            });
            reader.setDaemon(true);
            reader.start();

            out.println(Protocol.CMD_JOIN + " " + name);
            System.out.println("Connected. Commands: f=FLIP, b=BELL, q=quit");

            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            String input;
            while ((input = stdin.readLine()) != null) {
                String c = input.trim().toLowerCase();
                if (c.equals("f") || c.equals("flip")) {
                    out.println(Protocol.CMD_FLIP);
                } else if (c.equals("b") || c.equals("bell")) {
                    out.println(Protocol.CMD_BELL);
                } else if (c.equals("q") || c.equals("quit")) {
                    out.println(Protocol.CMD_QUIT);
                    break;
                } else if (!c.isEmpty()) {
                    System.out.println("(f=FLIP, b=BELL, q=quit)");
                }
            }
        }
    }

    private static void printServerMessage(String line) {
        int sp = line.indexOf(' ');
        String type = sp < 0 ? line : line.substring(0, sp);
        String body = sp < 0 ? "" : line.substring(sp + 1);

        if (line.startsWith(Protocol.MSG_STATE)) {
            System.out.println(StateView.parse(line).render());
        } else if (type.equals(Protocol.MSG_EVENT)) {
            System.out.println("  ● " + body);
        } else if (type.equals(Protocol.MSG_ERROR)) {
            System.out.println("  ⚠ " + body);
        } else if (type.equals(Protocol.MSG_GAMEOVER)) {
            System.out.println("\n*** Game over! Winner: " + body + " ***");
        } else {
            System.out.println("  " + body);
        }
    }
}
