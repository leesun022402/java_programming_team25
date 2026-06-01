package halligalli.core;

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

    public static final int FRUIT_BELL_THRESHOLD = 5;
    public static final int CHIP_BELL_THRESHOLD = 3;

    private final List<Player> players;

    public Table(List<Player> players) {
        this.players = players;
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
            if (total == FRUIT_BELL_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /** Whether 3 visible chip symbols are showing. */
    public boolean isChipBell() {
        return chipSymbolCount() == CHIP_BELL_THRESHOLD;
    }

    /** Whether the bell may be rung (fruit or chip condition met). */
    public boolean isBellable() {
        return isFruitBell() || isChipBell();
    }
}
