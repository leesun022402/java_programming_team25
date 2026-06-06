package halligalli.core;

import halligalli.model.Card;
import halligalli.model.Fruit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Builds a standard Halli Galli deck.
 *
 * <p>14 cards per fruit: count 1=5, 2=3, 3=3, 4=2, 5=1 -> 4 fruits x 14 = 56 cards.
 * Of these, 4 per fruit (16 total) carry a chip symbol.</p>
 */
public final class DeckFactory {

    public static final int TOTAL_CARDS = 56;
    public static final int CHIP_CARDS = 16;

    /** Card count per fruit, indexed by fruit count (1-5). */
    private static final int[] COUNT_DISTRIBUTION = {
            // count: 1  2  3  4  5
            0, 5, 3, 3, 2, 1
    };
    private static final int CHIPS_PER_FRUIT = 4;

    private DeckFactory() {
    }

    /** Builds one shuffled deck (56 cards). */
    public static List<Card> newShuffledDeck(Random random) {
        List<Card> deck = newOrderedDeck();
        Collections.shuffle(deck, random != null ? random : new Random());
        return deck;
    }

    /**
     * Builds one unshuffled deck. Chips are assigned to the first 4 cards of each fruit.
     * (For verification/testing. Real games use {@link #newShuffledDeck}.)
     */
    public static List<Card> newOrderedDeck() {
        List<Card> deck = new ArrayList<>(TOTAL_CARDS);
        for (Fruit fruit : Fruit.values()) {
            int chipsAssigned = 0;
            for (int count = Card.MIN_COUNT; count <= Card.MAX_COUNT; count++) {
                int copies = COUNT_DISTRIBUTION[count];
                for (int i = 0; i < copies; i++) {
                    boolean chip = chipsAssigned < CHIPS_PER_FRUIT;
                    if (chip) {
                        chipsAssigned++;
                    }
                    deck.add(new Card(fruit, count, chip));
                }
            }
        }
        return deck;
    }
}
