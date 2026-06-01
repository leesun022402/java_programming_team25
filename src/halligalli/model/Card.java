package halligalli.model;

import java.util.Objects;

/**
 * A single card. Immutable.
 * Composed of {fruit kind, fruit count (1-5), whether it carries a chip symbol}.
 */
public final class Card {

    public static final int MIN_COUNT = 1;
    public static final int MAX_COUNT = 5;

    private final Fruit fruit;
    private final int count;
    private final boolean hasChip;

    public Card(Fruit fruit, int count, boolean hasChip) {
        if (fruit == null) {
            throw new IllegalArgumentException("fruit must not be null.");
        }
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "count must be within " + MIN_COUNT + ".." + MAX_COUNT + ": " + count);
        }
        this.fruit = fruit;
        this.count = count;
        this.hasChip = hasChip;
    }

    public Fruit fruit() {
        return fruit;
    }

    public int count() {
        return count;
    }

    public boolean hasChip() {
        return hasChip;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Card)) {
            return false;
        }
        Card card = (Card) o;
        return count == card.count && hasChip == card.hasChip && fruit == card.fruit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fruit, count, hasChip);
    }

    @Override
    public String toString() {
        return String.format("%s x%d%s", fruit.label(), count, hasChip ? "[chip]" : "");
    }
}
