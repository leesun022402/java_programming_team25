package halligalli.core;

/** Tunable rule values for one Halli Galli Plus+ game. */
public final class GameRules {

    public static final int DEFAULT_FRUIT_BELL_THRESHOLD = 5;
    public static final int DEFAULT_CHIP_BELL_THRESHOLD = 3;
    public static final int DEFAULT_REVIVE_CARDS_PER_PLAYER = 3;

    private final int fruitBellThreshold;
    private final int chipBellThreshold;
    private final int reviveCardsPerPlayer;

    public GameRules(int fruitBellThreshold, int chipBellThreshold, int reviveCardsPerPlayer) {
        this.fruitBellThreshold = Math.max(1, fruitBellThreshold);
        this.chipBellThreshold = Math.max(1, chipBellThreshold);
        this.reviveCardsPerPlayer = Math.max(1, reviveCardsPerPlayer);
    }

    public static GameRules defaults() {
        return new GameRules(DEFAULT_FRUIT_BELL_THRESHOLD, DEFAULT_CHIP_BELL_THRESHOLD,
                DEFAULT_REVIVE_CARDS_PER_PLAYER);
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
}
