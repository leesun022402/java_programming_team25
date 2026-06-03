package halligalli.core;

import halligalli.exception.GameOverException;
import halligalli.exception.InvalidBellException;
import halligalli.model.Card;
import halligalli.model.Player;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Orchestrates the game (the GAME MANAGER from the slides).
 * Responsible for dealing the deck, advancing turns, managing the chip (life) pool,
 * bell validation, and life/elimination handling.
 *
 * <p>A pure engine, independent of the input source (console/bot/socket). "Who rang
 * first" is decided by the call order of {@link #ringBell(Player)}; the input layer
 * produces that order.</p>
 */
public class GameManager {

    /** Cards received from each other player when reviving. */
    public static final int REVIVE_CARDS_PER_PLAYER = 3;

    private final List<Player> players;
    private final Table table;
    private int houseChips;
    private int turnIndex = 0;
    private boolean gameOver = false;

    /** Standard constructor: shuffles and deals the 56-card deck. */
    public GameManager(List<Player> players, Random random) {
        this(players, random, true);
    }

    /** No-deal constructor (for tests / custom setups). The caller fills the piles. */
    public GameManager(List<Player> players) {
        this(players, null, false);
    }

    private GameManager(List<Player> players, Random random, boolean deal) {
        if (players == null || players.size() < 3) {
            throw new IllegalArgumentException("At least 3 players are required.");
        }
        this.players = new ArrayList<>(players);
        this.table = new Table(this.players);
        this.houseChips = players.size(); // the house starts with one chip per player
        if (deal) {
            dealDeck(random);
        }
    }

    /** Shuffles the 56 cards and deals them as evenly as possible. */
    private void dealDeck(Random random) {
        List<Card> deck = DeckFactory.newShuffledDeck(random);
        int i = 0;
        for (Card card : deck) {
            players.get(i % players.size()).addToBottom(card);
            i++;
        }
    }

    public Table table() {
        return table;
    }

    public List<Player> players() {
        return players;
    }

    public int houseChips() {
        return houseChips;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    /** The player whose turn it currently is. */
    public Player currentPlayer() {
        return players.get(turnIndex);
    }

    /** The list of active players. */
    public List<Player> activePlayers() {
        List<Player> active = new ArrayList<>();
        for (Player p : players) {
            if (p.isActive()) {
                active.add(p);
            }
        }
        return active;
    }

    // ------------------------------------------------------------ turn flow

    /**
     * The current player flips one card and the turn advances.
     * If that player has no cards, life/elimination is resolved first.
     *
     * @return the flipped card (null if revive failed and nothing was flipped)
     */
    public Card playTurn() {
        if (gameOver) {
            throw new GameOverException("The game is already over.");
        }
        Player current = currentPlayer();

        if (current.hasNoCards()) {
            ensurePlayableOrEliminate(current);
            checkGameOver();
            if (gameOver) {
                return null;
            }
        }

        Card flipped = current.isActive() ? current.flip() : null;
        advanceTurn();
        return flipped;
    }

    /** Moves the turn index to the next active player. */
    private void advanceTurn() {
        for (int step = 0; step < players.size(); step++) {
            turnIndex = (turnIndex + 1) % players.size();
            if (players.get(turnIndex).isActive()) {
                return;
            }
        }
    }

    // ----------------------------------------------------------- life / elimination

    /**
     * Revives a player with no cards using a chip (life), or eliminates them.
     * If a chip is available, spends 1 and pulls min(3, held) cards from each other
     * active player. If still at 0 afterwards, or no chip to begin with, eliminates.
     */
    public void ensurePlayableOrEliminate(Player player) {
        if (!player.hasNoCards()) {
            return;
        }
        if (player.chips() <= 0) {
            player.eliminate();
            return;
        }
        player.consumeChip(); // chips are spent and never return to the house
        for (Player other : players) {
            if (other == player || other.isEliminated()) {
                continue;
            }
            int take = Math.min(REVIVE_CARDS_PER_PLAYER, other.totalCards());
            for (int i = 0; i < take; i++) {
                Card card = removeOneTransferCard(other);
                if (card != null) {
                    player.addToBottom(card);
                }
            }
        }
        if (player.hasNoCards()) {
            player.eliminate(); // there were no cards at all to take
        }
    }

    /**
     * Immediately eliminates a player for an external reason (e.g. network disconnect).
     * If that player was the current one, advances the turn, then checks for game end.
     */
    public void forceEliminate(Player player) {
        if (player == null || player.isEliminated()) {
            return;
        }
        boolean wasCurrent = currentPlayer() == player;
        player.eliminate();
        if (wasCurrent) {
            advanceTurn();
        }
        checkGameOver();
    }

    /** Ends the game once one or fewer players remain active. */
    private void checkGameOver() {
        if (activePlayers().size() <= 1) {
            gameOver = true;
        }
    }

    /** The winner (the sole active player at game end). Null if not over. */
    public Player winner() {
        if (!gameOver) {
            return null;
        }
        List<Player> active = activePlayers();
        return active.isEmpty() ? null : active.get(0);
    }

    // ----------------------------------------------------------- bell validation

    /**
     * Bell validation. The outcome is decided by the table state at the moment of the call.
     * Only the first caller (the first to ring) on a valid table gets the real reward,
     * so the input layer must impose a competition order on these calls.
     */
    public BellResult ringBell(Player ringer) {
        if (gameOver) {
            throw new GameOverException("The game is already over.");
        }
        if (ringer == null || ringer.isEliminated()) {
            throw new InvalidBellException("An eliminated or unknown player cannot ring the bell.");
        }

        boolean fruit = table.isFruitBell();
        boolean chip = table.isChipBell();

        RingOutcome outcome;
        if (fruit && chip) {
            outcome = RingOutcome.BOTH;
        } else if (fruit) {
            outcome = RingOutcome.FRUIT;
        } else if (chip) {
            outcome = RingOutcome.CHIP;
        } else {
            outcome = RingOutcome.INVALID;
        }

        BellResult result;
        switch (outcome) {
            case FRUIT:
                result = resolveFruit(ringer, outcome);
                break;
            case CHIP:
                result = resolveChip(ringer, outcome);
                break;
            case BOTH:
                result = resolveBoth(ringer);
                break;
            case INVALID:
            default:
                result = resolveInvalid(ringer);
                break;
        }

        // The player who won the bell leads the next turn (flips first).
        if (result.isValid() && !gameOver && ringer.isActive()) {
            setTurnTo(ringer);
        }
        return result;
    }

    /** Sets the turn to the given player if they are still active. */
    private void setTurnTo(Player player) {
        int idx = players.indexOf(player);
        if (idx >= 0 && players.get(idx).isActive()) {
            turnIndex = idx;
        }
    }

    /** Fruit total of 5: all face-up cards go to the winner. */
    private BellResult resolveFruit(Player ringer, RingOutcome outcome) {
        int cards = collectAllFaceUp(ringer);
        afterBellMaintenance();
        return new BellResult(ringer, outcome, cards, false,
                String.format("%s rings! Fruit 5 -> won %d cards", ringer.name(), cards));
    }

    /** 3 chip symbols: 1 chip from the house, then reset the table cards. */
    private BellResult resolveChip(Player ringer, RingOutcome outcome) {
        boolean got = grantChip(ringer);
        int cleared = clearAllFaceUp();
        afterBellMaintenance();
        return new BellResult(ringer, outcome, 0, got,
                String.format("%s rings! 3 chips -> %s, cleared %d card(s)", ringer.name(),
                        got ? "won 1 chip" : "house chips depleted (none won)", cleared));
    }

    /** Both met: cards + chip. */
    private BellResult resolveBoth(Player ringer) {
        int cards = collectAllFaceUp(ringer);
        boolean got = grantChip(ringer);
        afterBellMaintenance();
        return new BellResult(ringer, RingOutcome.BOTH, cards, got,
                String.format("%s rings! Fruit 5 + 3 chips -> won %d cards%s", ringer.name(), cards,
                        got ? " + 1 chip" : " (house chips depleted)"));
    }

    /** False bell: pay 1 card to each other active player. */
    private BellResult resolveInvalid(Player ringer) {
        int paid = 0;
        for (Player other : players) {
            if (other == ringer || other.isEliminated()) {
                continue;
            }
            Card penalty = removeOneTransferCard(ringer);
            if (penalty != null) {
                other.addToBottom(penalty);
                paid++;
            }
        }
        afterBellMaintenance();
        return new BellResult(ringer, RingOutcome.INVALID, -paid, false,
                String.format("%s false bell! Pays %d card(s) to other players", ringer.name(), paid));
    }

    /** Collects every player's face-up pile to the bottom of the winner's draw pile. */
    private int collectAllFaceUp(Player ringer) {
        int total = 0;
        for (Player p : players) {
            Deque<Card> taken = p.takeFaceUp();
            total += taken.size();
            ringer.addAllToBottom(taken);
        }
        return total;
    }

    /** Clears every player's face-up pile without awarding those cards. */
    private int clearAllFaceUp() {
        int total = 0;
        for (Player p : players) {
            total += p.takeFaceUp().size();
        }
        return total;
    }

    /** Takes one transferable card: draw pile first, then one face-up card. */
    private Card removeOneTransferCard(Player player) {
        Card card = player.removeFromDrawPile();
        if (card == null) {
            card = player.removeFromFaceUpPile();
        }
        return card;
    }

    /** Grants 1 chip to the winner if the house pool still has any. */
    private boolean grantChip(Player ringer) {
        if (houseChips > 0) {
            houseChips--;
            ringer.addChip();
            return true;
        }
        return false;
    }

    /** After a bell, run life/elimination checks for any player at 0 cards. */
    private void afterBellMaintenance() {
        for (Player p : players) {
            if (p.isActive() && p.hasNoCards()) {
                ensurePlayableOrEliminate(p);
            }
        }
        checkGameOver();
        if (!gameOver && currentPlayer().isEliminated()) {
            advanceTurn();
        }
    }

    /** Table summary for debugging/display. */
    public String tableSummary() {
        StringBuilder sb = new StringBuilder("Table: ");
        Map<halligalli.model.Fruit, Integer> totals = table.fruitTotals();
        if (totals.isEmpty()) {
            sb.append("(no face-up cards)");
        } else {
            totals.forEach((f, c) -> sb.append(f.label()).append("=").append(c).append(" "));
        }
        sb.append("| chips=").append(table.chipSymbolCount());
        sb.append(" | house=").append(houseChips);
        return sb.toString();
    }
}
