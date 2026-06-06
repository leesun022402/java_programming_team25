package halligalli.core;

import halligalli.exception.InvalidGameSetupException;
import halligalli.model.Card;
import halligalli.model.Fruit;
import halligalli.model.Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Table aggregator. Collects only each player's top (visible) face-up card and computes
 * the per-fruit totals and the chip-symbol count. These are the basis for deciding
 * whether the bell may be rung.
 */
public class Table {

    public static final int FRUIT_BELL_THRESHOLD = GameRules.DEFAULT_FRUIT_BELL_THRESHOLD;
    public static final int CHIP_BELL_THRESHOLD = GameRules.DEFAULT_CHIP_BELL_THRESHOLD;

    private final List<Player> players;
    private final GameRules rules;

    public Table(List<Player> players) {
        this(players, GameRules.defaults());
    }

    public Table(List<Player> players, GameRules rules) {
        if (players == null) {
            throw new InvalidGameSetupException("Table requires a player list.");
        }
        for (Player p : players) {
            if (p == null) {
                throw new InvalidGameSetupException("Table cannot contain a null player.");
            }
        }
        this.players = List.copyOf(players);
        this.rules = rules != null ? rules : GameRules.defaults();
    }

    /** Per-fruit totals over the currently visible cards. */
    public Map<Fruit, Integer> fruitTotals() {
        Map<Fruit, Integer> totals = new EnumMap<>(Fruit.class);
        for (Player p : players) {
            if (p.isEliminated()) {
                continue;
            }
            Card visible = p.visibleCard();
            if (visible != null) {
                totals.merge(visible.fruit(), visible.count(), Integer::sum);
            }
        }
        return totals;
    }

    /** Number of visible cards that carry a chip symbol. */
    public int chipSymbolCount() {
        int count = 0;
        for (Player p : players) {
            if (p.isEliminated()) {
                continue;
            }
            Card visible = p.visibleCard();
            if (visible != null && visible.hasChip()) {
                count++;
            }
        }
        return count;
    }

    /** Whether some fruit total is exactly 5. */
    public boolean isFruitBell() {
        for (int total : fruitTotals().values()) {
            if (total == rules.fruitBellThreshold()) {
                return true;
            }
        }
        return false;
    }

    /** Whether enough visible chip symbols are showing for the current active player count. */
    public boolean isChipBell() {
        return chipSymbolCount() >= chipBellThreshold();
    }

    /** Whether the bell may be rung (fruit or chip condition met). */
    public boolean isBellable() {
        return isFruitBell() || isChipBell();
    }

    public int fruitBellThreshold() {
        return rules.fruitBellThreshold();
    }

    public int chipBellThreshold() {
        return Math.min(rules.chipBellThreshold(), activePlayerCount());
    }

    private int activePlayerCount() {
        int count = 0;
        for (Player p : players) {
            if (p.isActive()) {
                count++;
            }
        }
        return Math.max(1, count);
    }
}
