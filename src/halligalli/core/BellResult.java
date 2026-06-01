package halligalli.core;

import halligalli.model.Player;

/** The result of a single bell ring, with its reward/penalty details. */
public final class BellResult {

    private final Player ringer;
    private final RingOutcome outcome;
    private final int cardsWon;
    private final boolean chipWon;
    private final String description;

    public BellResult(Player ringer, RingOutcome outcome, int cardsWon,
                      boolean chipWon, String description) {
        this.ringer = ringer;
        this.outcome = outcome;
        this.cardsWon = cardsWon;
        this.chipWon = chipWon;
        this.description = description;
    }

    public Player ringer() {
        return ringer;
    }

    public RingOutcome outcome() {
        return outcome;
    }

    public int cardsWon() {
        return cardsWon;
    }

    public boolean chipWon() {
        return chipWon;
    }

    public boolean isValid() {
        return outcome != RingOutcome.INVALID;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
