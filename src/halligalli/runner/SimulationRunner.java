package halligalli.runner;

import halligalli.core.BellResult;
import halligalli.core.GameManager;
import halligalli.model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Bot-based simulation driver. Validates the engine ({@link GameManager}) while staying
 * separate from any input layer.
 *
 * <p>Each turn flips one card; once the table becomes bellable, bots compete by "reaction
 * time" and the fastest rings. A small chance of a false bell exercises every RingOutcome
 * path. Seeded for reproducibility.</p>
 */
public class SimulationRunner {

    private static final int MAX_TURNS = 100_000; // infinite-loop guard
    private static final double FALSE_BELL_PROB = 0.02; // false-bell chance when not bellable
    private static final double NOTICE_PROB = 0.85; // chance a bot notices a valid condition

    private final GameManager game;
    private final Random random;
    private final boolean verbose;

    public SimulationRunner(GameManager game, Random random, boolean verbose) {
        this.game = game;
        this.random = random;
        this.verbose = verbose;
    }

    /** Plays the game to the end and returns the winner. */
    public Player run() {
        int turn = 0;
        while (!game.isGameOver() && turn < MAX_TURNS) {
            turn++;
            Player current = game.currentPlayer();
            var flipped = game.playTurn();
            if (game.isGameOver()) {
                break;
            }
            if (flipped != null && verbose) {
                System.out.printf("[turn %d] %s flips: %-16s | %s%n",
                        turn, current.name(), flipped, game.tableSummary());
            }
            attemptBells(turn);
        }

        Player winner = game.winner();
        if (verbose) {
            System.out.println("------------------------------------------");
            System.out.println("Game over! Winner: " + (winner != null ? winner.name() : "none"));
            for (Player p : game.players()) {
                System.out.println("  " + p);
            }
        }
        return winner;
    }

    /** Runs one round of the bots' bell competition for the current state. */
    private void attemptBells(int turn) {
        boolean bellable = game.table().isBellable();
        Player ringer;
        if (bellable) {
            ringer = pickFastestNoticer();
        } else {
            ringer = maybeFalseRinger();
        }
        if (ringer == null) {
            return;
        }
        BellResult result = game.ringBell(ringer);
        if (verbose) {
            System.out.printf("    >> %s%n", result.description());
        }
    }

    /** Among bots that noticed the valid condition, picks the one with the fastest reaction. */
    private Player pickFastestNoticer() {
        Player fastest = null;
        double best = Double.MAX_VALUE;
        for (Player p : game.activePlayers()) {
            if (random.nextDouble() > NOTICE_PROB) {
                continue; // did not notice
            }
            double reaction = random.nextDouble();
            if (reaction < best) {
                best = reaction;
                fastest = p;
            }
        }
        return fastest;
    }

    /** With a small probability, one bot rings a false bell. */
    private Player maybeFalseRinger() {
        if (random.nextDouble() < FALSE_BELL_PROB) {
            List<Player> active = game.activePlayers();
            if (!active.isEmpty()) {
                return active.get(random.nextInt(active.size()));
            }
        }
        return null;
    }

    // --------------------------------------------------------------------- main

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 25L;
        int playerCount = args.length > 1 ? Integer.parseInt(args[1]) : 3;

        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            players.add(new Player("P" + i));
        }

        Random random = new Random(seed);
        GameManager game = new GameManager(players, random);

        System.out.println("=== Halli Galli Plus+ Simulation (seed=" + seed
                + ", " + playerCount + " players) ===");
        for (Player p : game.players()) {
            System.out.println("  start: " + p);
        }
        System.out.println("  house chips: " + game.houseChips());
        System.out.println("------------------------------------------");

        Player winner = new SimulationRunner(game, random, true).run();
        System.out.println();
        System.out.println(">>> Final winner: " + (winner != null ? winner.name() : "none"));
    }
}
