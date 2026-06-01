package halligalli.model;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

/**
 * A player. Each owns a face-down draw pile, a face-up pile, and life tokens (chips).
 *
 * <p>As in standard Halli Galli, the most recently flipped card is on top of the
 * face-up pile (the visible card). Only each player's single top card is used for
 * bell evaluation.</p>
 */
public class Player {

    private final String name;
    private final Deque<Card> faceDown = new ArrayDeque<>();
    private final Deque<Card> faceUp = new ArrayDeque<>();
    private int chips = 0;
    private boolean eliminated = false;

    public Player(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A player name is required.");
        }
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** Moves the top card of the draw pile onto the face-up pile. Returns null if empty. */
    public Card flip() {
        Card card = faceDown.pollFirst();
        if (card != null) {
            faceUp.push(card); // addFirst -> becomes the top
        }
        return card;
    }

    /** The currently visible card (top of the face-up pile). Null if none. */
    public Card visibleCard() {
        return faceUp.peekFirst();
    }

    /** Removes and returns the whole face-up pile (used when a winner collects it). The pile is cleared. */
    public Deque<Card> takeFaceUp() {
        Deque<Card> taken = new ArrayDeque<>(faceUp);
        faceUp.clear();
        return taken;
    }

    /** Removes one card from the top of the draw pile (used for penalty/transfer). Null if empty. */
    public Card removeFromDrawPile() {
        return faceDown.pollFirst();
    }

    /** Adds one card to the bottom of the draw pile. */
    public void addToBottom(Card card) {
        if (card != null) {
            faceDown.addLast(card);
        }
    }

    /** Adds several cards to the bottom of the draw pile. */
    public void addAllToBottom(Collection<Card> cards) {
        for (Card c : cards) {
            addToBottom(c);
        }
    }

    public int chips() {
        return chips;
    }

    public void addChip() {
        chips++;
    }

    /** Spends one chip as a life. */
    public void consumeChip() {
        if (chips <= 0) {
            throw new IllegalStateException(name + ": no chips to spend.");
        }
        chips--;
    }

    public int faceDownCount() {
        return faceDown.size();
    }

    public int faceUpCount() {
        return faceUp.size();
    }

    /** Total cards held (draw pile + face-up pile). */
    public int totalCards() {
        return faceDown.size() + faceUp.size();
    }

    public boolean hasNoCards() {
        return totalCards() == 0;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void eliminate() {
        eliminated = true;
    }

    /** Whether the player is still alive in the game. */
    public boolean isActive() {
        return !eliminated;
    }

    @Override
    public String toString() {
        return String.format("%s[cards %d (draw %d/up %d), chips %d%s]",
                name, totalCards(), faceDown.size(), faceUp.size(), chips,
                eliminated ? ", OUT" : "");
    }
}
