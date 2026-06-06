package halligalli.net;

import halligalli.core.BellResult;
import halligalli.core.GameManager;
import halligalli.core.GameRules;
import halligalli.exception.HalliGalliException;
import halligalli.exception.InvalidGameSetupException;
import halligalli.model.Card;
import halligalli.model.Fruit;
import halligalli.model.Player;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * Authoritative server. Holds the game state exclusively and serializes every command
 * under a single lock.
 *
 * <p>FLIP is allowed only on your turn; BELL is allowed anytime. Commands are processed
 * in arrival order (lock-acquisition order), so "the first to ring wins" arises naturally
 * -- network latency and reaction speed decide the race (as in real Halli Galli).</p>
 */
public class GameServer {

    private final int port;
    private final int expectedPlayers;
    private final Random random;
    private final boolean verbose;
    private final GameLogRecorder logRecorder;
    private final GameRules rules;

    private final Object lock = new Object();
    private final List<ClientHandler> handlers = new CopyOnWriteArrayList<>();
    private final Map<ClientHandler, Player> playerOf = new LinkedHashMap<>();
    private final List<Player> joinOrder = new ArrayList<>();

    private GameManager game;
    private boolean started = false;
    private boolean finished = false;
    private final CountDownLatch gameOverLatch = new CountDownLatch(1);

    public GameServer(int port, int expectedPlayers, Random random) {
        this(port, expectedPlayers, random, false);
    }

    /** When {@code verbose} is true, the server also echoes game events to its own console. */
    public GameServer(int port, int expectedPlayers, Random random, boolean verbose) {
        this(port, expectedPlayers, random, verbose, null);
    }

    public GameServer(int port, int expectedPlayers, Random random,
                      boolean verbose, GameLogRecorder logRecorder) {
        this(port, expectedPlayers, random, verbose, logRecorder, GameRules.defaults());
    }

    public GameServer(int port, int expectedPlayers, Random random,
                      boolean verbose, GameLogRecorder logRecorder, GameRules rules) {
        if (expectedPlayers < 2) {
            throw new InvalidGameSetupException("At least 2 players are required.");
        }
        this.port = port;
        this.expectedPlayers = expectedPlayers;
        this.random = random != null ? random : new Random();
        this.verbose = verbose;
        this.logRecorder = logRecorder;
        this.rules = rules != null ? rules : GameRules.defaults();
    }

    /** Starts the server and blocks until the game ends. */
    public void start() throws IOException, InterruptedException {
        Thread logShutdownHook = null;
        if (logRecorder != null) {
            logShutdownHook = new Thread(() -> closeLog("shutdown", currentWinnerName()),
                    "hgp-log-shutdown");
            Runtime.getRuntime().addShutdownHook(logShutdownHook);
            System.out.println("[server] JSON log: " + logRecorder.path());
        }
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(500);
            System.out.println("[server] waiting for " + expectedPlayers + " players on port " + port + "...");
            while (!hasStarted()) {
                try {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(this, socket);
                    handlers.add(handler);
                    handler.start();
                    System.out.println("[server] connected " + handlers.size() + " socket(s), "
                            + joinedCount() + "/" + expectedPlayers + " joined");
                } catch (SocketTimeoutException ignored) {
                    // Re-check whether a handler thread started the game after receiving JOINs.
                }
            }
            gameOverLatch.await(); // wait until the game is over
            System.out.println("[server] game over. shutting down.");
        } finally {
            if (logShutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(logShutdownHook);
                } catch (IllegalStateException ignored) {
                    // JVM is already shutting down, so the hook is running or has run.
                }
            }
            closeLog(finished ? "finished" : "stopped", currentWinnerName());
        }
    }

    private boolean hasStarted() {
        synchronized (lock) {
            return started;
        }
    }

    private int joinedCount() {
        synchronized (lock) {
            return joinOrder.size();
        }
    }

    // -------------------------------------------------------------- command handling

    void handleCommand(ClientHandler handler, String line) {
        if (line.isEmpty()) {
            return;
        }
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1] : "";

        synchronized (lock) {
            recordCommand(handler, cmd, arg);
            switch (cmd) {
                case Protocol.CMD_JOIN:
                    handleJoin(handler, arg);
                    break;
                case Protocol.CMD_FLIP:
                    handleFlip(handler);
                    break;
                case Protocol.CMD_BELL:
                    handleBell(handler);
                    break;
                case Protocol.CMD_QUIT:
                    handler.send(Protocol.MSG_INFO + " Closing connection.");
                    handler.close();
                    break;
                default:
                    handler.send(Protocol.MSG_ERROR + " Unknown command: " + cmd);
            }
        }
    }

    private void handleJoin(ClientHandler handler, String name) {
        if (playerOf.containsKey(handler)) {
            handler.send(Protocol.MSG_ERROR + " Already joined.");
            return;
        }
        if (started || finished) {
            handler.send(Protocol.MSG_ERROR + " The game has already started.");
            handler.close();
            return;
        }
        name = sanitizeName(name);
        if (name.isBlank()) {
            name = "P" + (joinOrder.size() + 1);
        }
        name = uniqueName(name);
        Player player = new Player(name);
        playerOf.put(handler, player);
        joinOrder.add(player);
        handler.send(Protocol.MSG_WELCOME + " " + name);
        broadcast(Protocol.MSG_INFO + " " + name + " joined (" + joinOrder.size() + "/" + expectedPlayers + ")");
        record("JOIN", name + " joined (" + joinOrder.size() + "/" + expectedPlayers + ")", null);

        if (joinOrder.size() == expectedPlayers && !started) {
            startGame();
        }
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replace('|', ' ').replace(',', ' ').trim().replaceAll("\\s+", " ");
    }

    private String uniqueName(String base) {
        String candidate = base;
        int suffix = 2;
        while (isNameTaken(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean isNameTaken(String name) {
        for (Player p : joinOrder) {
            if (p.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void startGame() {
        game = new GameManager(joinOrder, random, rules);
        started = true;
        broadcast(Protocol.MSG_START + " Game start! FLIP on your turn, BELL anytime.");
        record("START", "Game start! FLIP on your turn, BELL anytime.", buildState());
        broadcastEvent("Starting with " + game.houseChips() + " house chips");
        broadcastState();
        announceTurn();
    }

    private void handleFlip(ClientHandler handler) {
        if (!ensurePlayable(handler)) {
            return;
        }
        Player p = playerOf.get(handler);
        if (game.currentPlayer() != p) {
            handler.send(Protocol.MSG_ERROR + " Not your turn. (current: "
                    + game.currentPlayer().name() + ")");
            record("ERROR", p.name() + " tried FLIP out of turn", buildState());
            return;
        }
        Player who = game.currentPlayer();
        Card flipped = game.playTurn();
        broadcastEvent(who.name() + " flips: " + (flipped != null ? flipped : "(no cards - life/elimination)"));
        afterAction();
    }

    private void handleBell(ClientHandler handler) {
        if (!ensurePlayable(handler)) {
            return;
        }
        Player p = playerOf.get(handler);
        try {
            BellResult result = game.ringBell(p);
            broadcastEvent(result.description());
        } catch (HalliGalliException e) {
            handler.send(Protocol.MSG_ERROR + " " + e.getMessage());
            record("ERROR", p.name() + " BELL failed: " + e.getMessage(), buildState());
            return;
        }
        afterAction();
    }

    /** Checks the game is playable; otherwise sends an ERROR to the client. */
    private boolean ensurePlayable(ClientHandler handler) {
        if (!started) {
            handler.send(Protocol.MSG_ERROR + " The game has not started yet.");
            return false;
        }
        if (game.isGameOver()) {
            handler.send(Protocol.MSG_ERROR + " The game is already over.");
            return false;
        }
        if (!playerOf.containsKey(handler)) {
            handler.send(Protocol.MSG_ERROR + " This client has not joined.");
            return false;
        }
        return true;
    }

    /** After processing one action, broadcast state and check for game end. */
    private void afterAction() {
        broadcastState();
        if (game.isGameOver()) {
            finishGame();
        } else {
            announceTurn();
        }
    }

    private void announceTurn() {
        String text = "> " + game.currentPlayer().name() + "'s turn (FLIP)";
        broadcast(Protocol.MSG_INFO + " " + text);
        record("TURN", text, buildState());
    }

    private void finishGame() {
        if (finished) {
            return;
        }
        finished = true;
        Player winner = game.winner();
        broadcastState();
        broadcast(Protocol.MSG_GAMEOVER + " " + (winner != null ? winner.name() : "none"));
        record("GAMEOVER", "Winner: " + (winner != null ? winner.name() : "none"), buildState());
        closeLog("finished", winner != null ? winner.name() : null);
        for (ClientHandler h : handlers) {
            h.close();
        }
        gameOverLatch.countDown();
    }

    // ------------------------------------------------------------- disconnect

    void onDisconnect(ClientHandler handler) {
        synchronized (lock) {
            Player p = playerOf.remove(handler);
            handlers.remove(handler);
            if (p == null || finished) {
                return;
            }
            if (!started) {
                joinOrder.remove(p);
                broadcast(Protocol.MSG_INFO + " " + p.name() + " left before start ("
                        + joinOrder.size() + "/" + expectedPlayers + ")");
                record("LEAVE", p.name() + " left before start", null);
                return;
            }
            if (p.isActive()) {
                broadcastEvent(p.name() + " disconnected -> eliminated");
                game.forceEliminate(p);
                afterAction();
            }
        }
    }

    // ----------------------------------------------------------- broadcast

    private void broadcast(String line) {
        for (ClientHandler h : handlers) {
            h.send(line);
        }
        // echo key lines (events / turn / start / game over) to the server console
        if (verbose && (line.startsWith(Protocol.MSG_EVENT)
                || line.startsWith(Protocol.MSG_START)
                || line.startsWith(Protocol.MSG_GAMEOVER)
                || line.startsWith(Protocol.MSG_INFO + " >"))) {
            int sp = line.indexOf(' ');
            System.out.println("[game] " + (sp < 0 ? line : line.substring(sp + 1)));
        }
    }

    private void broadcastEvent(String text) {
        broadcast(Protocol.MSG_EVENT + " " + text);
        record("EVENT", text, game != null ? buildState() : null);
    }

    private void broadcastState() {
        String state = buildState();
        broadcast(state);
        record("STATE", null, state);
    }

    /** Builds the current game state as a parseable STATE string. */
    private String buildState() {
        StringBuilder sb = new StringBuilder(Protocol.MSG_STATE);
        sb.append("|turn=").append(game.currentPlayer().name());
        sb.append("|bellable=").append(game.table().isBellable());
        sb.append("|house=").append(game.houseChips());
        sb.append("|chipsym=").append(game.table().chipSymbolCount());
        sb.append("|fruittarget=").append(game.rules().fruitBellThreshold());
        sb.append("|chiptarget=").append(game.table().chipBellThreshold());

        sb.append("|fruits=");
        Map<Fruit, Integer> totals = game.table().fruitTotals();
        boolean first = true;
        for (Map.Entry<Fruit, Integer> e : totals.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(e.getKey().name()).append(":").append(e.getValue());
            first = false;
        }

        for (Player p : game.players()) {
            sb.append("|p=").append(p.name()).append(",")
                    .append(p.totalCards()).append(",")
                    .append(p.chips()).append(",")
                    .append(p.isActive() ? Protocol.STATUS_ACTIVE : Protocol.STATUS_OUT).append(",")
                    .append(encodeVisible(p.visibleCard()));
        }
        return sb.toString();
    }

    /** Encodes a visible card as {@code FRUIT:count:chipFlag}, or {@code -} if none. */
    private String encodeVisible(Card card) {
        if (card == null) {
            return "-";
        }
        return card.fruit().name() + ":" + card.count() + ":" + (card.hasChip() ? "1" : "0");
    }

    private void recordCommand(ClientHandler handler, String cmd, String arg) {
        Player actor = playerOf.get(handler);
        String name = actor != null ? actor.name() : "(not joined)";
        String details = arg.isBlank() ? "" : " " + sanitizeName(arg);
        record("COMMAND", name + " -> " + cmd + details,
                started && game != null ? buildState() : null);
    }

    private void record(String type, String message, String state) {
        if (logRecorder != null) {
            logRecorder.record(type, message, state);
        }
    }

    private String currentWinnerName() {
        synchronized (lock) {
            Player winner = game != null ? game.winner() : null;
            return winner != null ? winner.name() : null;
        }
    }

    private void closeLog(String status, String winner) {
        if (logRecorder != null) {
            logRecorder.finish(status, winner);
        }
    }

    // --------------------------------------------------------------------- main

    public static void main(String[] args) throws Exception {
        GameSettings settings = GameSettings.loadDefault();
        int port = args.length > 0 ? Integer.parseInt(args[0]) : settings.defaultPort();
        int players = args.length > 1 ? Integer.parseInt(args[1]) : settings.totalPlayers();
        long seed = args.length > 2 ? Long.parseLong(args[2]) : settings.seed();
        String botDifficulty = args.length > 3 ? args[3] : settings.botDifficulty();
        GameLogRecorder recorder = GameLogRecorder.createIfEnabled(settings, port, players, seed,
                botDifficulty);
        new GameServer(port, players, new Random(seed), true, recorder, settings.toGameRules()).start();
    }
}
