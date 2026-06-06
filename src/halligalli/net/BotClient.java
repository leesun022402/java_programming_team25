package halligalli.net;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An auto-playing bot client (for verification/demo). It parses STATE and, when it is
 * its turn, sends FLIP after a short delay; when the table is bellable, it sends BELL
 * after a random reaction delay. The randomized timing creates a bell race over the network.
 */
public class BotClient {

    private String name;
    private final Random rng;
    private PrintWriter out;

    // timing (ms): a flip waits flipBase + rand(flipJitter); a bell reacts after bellBase + rand(bellJitter)
    private final int flipBase;
    private final int flipJitter;
    private final int bellBase;
    private final int bellJitter;
    private final boolean exitOnGameOver;

    private volatile boolean bellable = false;
    private volatile boolean myTurn = false;
    private volatile boolean active = true;
    private final AtomicBoolean flipScheduled = new AtomicBoolean(false);
    private final AtomicBoolean bellScheduled = new AtomicBoolean(false);

    public BotClient(String name, long seed, String level) {
        this(name, seed, level, true);
    }

    public BotClient(String name, long seed, String level, boolean exitOnGameOver) {
        this.name = name;
        this.rng = new Random(seed);
        this.exitOnGameOver = exitOnGameOver;
        // difficulty presets — how fast the bot flips and how quickly it reacts to a bell
        switch (level == null ? "normal" : level.toLowerCase()) {
            case "fast":   // bot-vs-bot demo: near-instant
                flipBase = 35; flipJitter = 45; bellBase = 5; bellJitter = 25; break;
            case "easy":   // human-friendly: slow bot, easy to beat
                flipBase = 1500; flipJitter = 600; bellBase = 700; bellJitter = 700; break;
            case "hard":   // tough but still human-competitive
                flipBase = 600; flipJitter = 300; bellBase = 200; bellJitter = 300; break;
            case "normal": // default: fair fight against a human
            default:
                flipBase = 1000; flipJitter = 500; bellBase = 400; bellJitter = 500; break;
        }
    }

    public void connect(String host, int port) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            out.println(Protocol.CMD_JOIN + " " + name);

            String line;
            while ((line = in.readLine()) != null) {
                onMessage(line);
            }
        }
    }

    private void onMessage(String line) {
        if (line.startsWith(Protocol.MSG_WELCOME)) {
            String assignedName = stripType(line).trim();
            if (!assignedName.isEmpty()) {
                name = assignedName;
            }
            return;
        }
        if (line.startsWith(Protocol.MSG_GAMEOVER)) {
            System.out.println("[" + name + "] " + line);
            active = false;
            if (exitOnGameOver) {
                System.exit(0);
            }
            return;
        }
        if (!line.startsWith(Protocol.MSG_STATE)) {
            return;
        }
        StateView view = StateView.parse(line);

        // update own alive status
        for (StateView.PlayerInfo p : view.players) {
            if (p.name.equals(name)) {
                active = p.active;
            }
        }
        if (!active) {
            return;
        }

        bellable = view.bellable;
        myTurn = name.equals(view.turn);

        // bellable -> schedule once (re-arm when it clears)
        if (bellable) {
            if (bellScheduled.compareAndSet(false, true)) {
                scheduleBell();
            }
        } else {
            bellScheduled.set(false);
        }

        // my turn -> schedule once (re-arm when the turn passes)
        if (myTurn) {
            if (flipScheduled.compareAndSet(false, true)) {
                scheduleFlip();
            }
        } else {
            flipScheduled.set(false);
        }
    }

    private static String stripType(String line) {
        int sp = line.indexOf(' ');
        return sp < 0 ? "" : line.substring(sp + 1);
    }

    private void scheduleBell() {
        int delay = bellBase + rng.nextInt(bellJitter); // reaction time
        spawn(delay, () -> {
            if (active && bellable && out != null) {
                out.println(Protocol.CMD_BELL);
            }
        });
    }

    private void scheduleFlip() {
        int delay = flipBase + rng.nextInt(flipJitter); // leave room for the bell race to settle
        spawn(delay, () -> {
            if (active && myTurn && out != null) {
                out.println(Protocol.CMD_FLIP);
            }
        });
    }

    private void spawn(int delayMs, Runnable action) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
            action.run();
        });
        t.setDaemon(true);
        t.start();
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT;
        String name = args.length > 2 ? args[2] : "BOT";
        long seed = args.length > 3 ? Long.parseLong(args[3]) : name.hashCode();
        String level = args.length > 4 ? args[4] : "normal"; // fast | easy | normal | hard
        new BotClient(name, seed, level).connect(host, port);
    }
}
