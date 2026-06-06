package halligalli.test;

import halligalli.core.BellResult;
import halligalli.core.DeckFactory;
import halligalli.core.GameManager;
import halligalli.core.GameRules;
import halligalli.core.RingOutcome;
import halligalli.core.Table;
import halligalli.exception.InvalidGameSetupException;
import halligalli.exception.InvalidPlayerException;
import halligalli.exception.NoChipException;
import halligalli.model.Card;
import halligalli.model.Fruit;
import halligalli.model.Player;
import halligalli.net.GameLogRecorder;
import halligalli.net.GameSettings;
import halligalli.stats.GameLogStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Dependency-free self-test runner. (Can be migrated to JUnit in Week 13; only the
 * verification logic lives here.) Each test prints a message on assertion failure and
 * leaves an overall pass/fail summary.
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        deckHas56CardsWith16Chips();
        deckDistributionPerFruit();
        deckCanShuffleWithDefaultRandom();
        invalidGameSetupThrowsDomainException();
        gameSettingsParsesJsonDefaults();
        gameSettingsRulesCanDriveGameManager();
        jsonLogRecorderWritesReplayableFile();
        jsonStatsParserCountsTurnsAndBells();
        twoPlayerGameSupportsFruitBell();
        playersViewIsReadOnly();
        playerConsumesOnlyHeldChips();
        tableAggregatesVisibleTopCards();
        fruitBellAt5();
        chipBellAt2ForTwoPlayers();
        chipBellDropsTo2WhenOnlyTwoPlayersRemain();
        chipBellAt3();
        chipBellAtMoreThan3();
        ringFruitWinsAllFaceUp();
        ringChipGrantsChipAndClearsCards();
        ringBothWinsCardsAndChip();
        invalidBellPenalizesRinger();
        invalidBellPaysFaceUpCardsOneAtATime();
        invalidBellEliminatingCurrentAdvancesTurn();
        ringerLeadsNextTurn();
        houseChipPoolDepletes();
        reviveConsumesChipAndPullsCards();
        revivePullsMultipleFaceUpCardsWithoutDroppingPile();
        reviveSettlesDonorsWhoRunOut();
        eliminationWhenNoChip();
        outsidePlayerCannotTouchGameState();
        fullSimulationProducesWinner();

        System.out.println("------------------------------------------");
        System.out.printf("Test results: %d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ----------------------------------------------------------------- deck

    static void deckHas56CardsWith16Chips() {
        List<Card> deck = DeckFactory.newOrderedDeck();
        check("deck has 56 cards", deck.size() == DeckFactory.TOTAL_CARDS);
        long chips = deck.stream().filter(Card::hasChip).count();
        check("16 chip cards", chips == DeckFactory.CHIP_CARDS);
    }

    static void deckDistributionPerFruit() {
        List<Card> deck = DeckFactory.newOrderedDeck();
        for (Fruit f : Fruit.values()) {
            long total = deck.stream().filter(c -> c.fruit() == f).count();
            check(f.label() + " has 14 cards", total == 14);
            long count5 = deck.stream().filter(c -> c.fruit() == f && c.count() == 5).count();
            check(f.label() + " has one count-5 card", count5 == 1);
        }
    }

    static void deckCanShuffleWithDefaultRandom() {
        List<Card> deck = DeckFactory.newShuffledDeck(null);
        check("null random still creates shuffled deck", deck.size() == DeckFactory.TOTAL_CARDS);
    }

    static void invalidGameSetupThrowsDomainException() {
        expectThrows("game requires at least 2 players", InvalidGameSetupException.class,
                () -> new GameManager(List.of(new Player("A"))));

        Player a = new Player("A");
        expectThrows("same player cannot sit twice", InvalidGameSetupException.class,
                () -> new GameManager(List.of(a, a, new Player("B"))));

        expectThrows("duplicate player names rejected", InvalidGameSetupException.class,
                () -> new GameManager(List.of(new Player("A"), new Player("A"))));

        expectThrows("table rejects null player list", InvalidGameSetupException.class,
                () -> new Table(null));
    }

    static void gameSettingsParsesJsonDefaults() {
        String json = "{"
                + "\"defaultPort\":6001,"
                + "\"defaultPlayerName\":\"Alice\","
                + "\"totalPlayers\":4,"
                + "\"botPlayers\":2,"
                + "\"botDifficulty\":\"hard\","
                + "\"seed\":99,"
                + "\"enableJsonLogs\":true,"
                + "\"logDirectory\":\"demo-logs\","
                + "\"databasePath\":\"data/demo.db\","
                + "\"rules\":{\"fruitBellThreshold\":5,\"chipBellThreshold\":3,\"reviveCardsPerPlayer\":3}"
                + "}";
        GameSettings settings = GameSettings.fromJson(json, GameSettings.defaults());
        check("settings parses port", settings.defaultPort() == 6001);
        check("settings parses player name", settings.defaultPlayerName().equals("Alice"));
        check("settings parses bot difficulty", settings.botDifficulty().equals("hard"));
        check("settings parses log directory", settings.logDirectory().equals("demo-logs"));
        check("settings parses database path", settings.databasePath().equals("data/demo.db"));
        check("settings parses fruit threshold", settings.toGameRules().fruitBellThreshold() == 5);
    }

    static void gameSettingsRulesCanDriveGameManager() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 2, false));
        Player b = visiblePlayer("B", new Card(Fruit.BANANA, 2, true));
        GameRules rules = new GameRules(4, 2, 2);
        GameManager game = new GameManager(List.of(a, b), rules);
        BellResult r = game.ringBell(a);
        check("custom fruit threshold rings at 4", r.outcome() == RingOutcome.FRUIT);
        check("custom rules exposed by game", game.rules().chipBellThreshold() == 2);
    }

    static void jsonLogRecorderWritesReplayableFile() {
        try {
            Path dir = Files.createTempDirectory("hgp-log-test");
            GameSettings settings = new GameSettings(5555, "Player", 2, 1, "easy",
                    25L, true, dir.toString(), "data/test.db", 5, 3, 3);
            GameLogRecorder recorder = GameLogRecorder.createIfEnabled(settings, 5555, 2, 25L);
            check("json recorder created", recorder != null);
            if (recorder == null) {
                return;
            }
            recorder.record("EVENT", "Alice flips",
                    "STATE|turn=Alice|bellable=false|house=2|chipsym=0|fruits=|p=Alice,1,0,active,-|p=Bob,1,0,active,-");
            Path path = recorder.path();
            recorder.finish("finished", "Alice");
            String log = Files.readString(path);
            check("json log contains event type", log.contains("\"type\":\"EVENT\""));
            check("json log contains state", log.contains("STATE|turn=Alice"));
            check("json log contains winner", log.contains("\"winner\": \"Alice\""));
        } catch (IOException e) {
            check("json log writes without IOException", false);
        }
    }

    static void jsonStatsParserCountsTurnsAndBells() {
        try {
            Path log = Files.createTempFile("hgp-stats", ".json");
            String stateStart = "STATE|turn=BOT1|bellable=false|house=2|chipsym=0"
                    + "|fruittarget=5|chiptarget=3|fruits="
                    + "|p=BOT1,10,0,active,-|p=BOT2,10,0,active,-";
            String stateEnd = "STATE|turn=BOT1|bellable=false|house=2|chipsym=0"
                    + "|fruittarget=5|chiptarget=3|fruits="
                    + "|p=BOT1,20,0,active,-|p=BOT2,0,0,out,-";
            Files.writeString(log, "{\n"
                    + "  \"startedAt\": \"2026-06-06T00:00:00Z\",\n"
                    + "  \"port\": 5555,\n"
                    + "  \"expectedPlayers\": 2,\n"
                    + "  \"seed\": 25,\n"
                    + "  \"botDifficulty\": \"fast\",\n"
                    + "  \"events\": [\n"
                    + "    {\"type\":\"START\",\"state\":\"" + stateStart + "\"},\n"
                    + "    {\"type\":\"COMMAND\",\"message\":\"BOT1 -> FLIP\",\"state\":\"" + stateStart + "\"},\n"
                    + "    {\"type\":\"EVENT\",\"message\":\"BOT1 flips: Banana x5\",\"state\":\"" + stateStart + "\"},\n"
                    + "    {\"type\":\"EVENT\",\"message\":\"BOT2 false bell! Pays 1 card(s) to other players\",\"state\":\"" + stateStart + "\"},\n"
                    + "    {\"type\":\"EVENT\",\"message\":\"BOT1 rings! Fruit 5 -> won 2 cards\",\"state\":\"" + stateEnd + "\"},\n"
                    + "    {\"type\":\"GAMEOVER\",\"message\":\"Winner: BOT1\",\"state\":\"" + stateEnd + "\"}\n"
                    + "  ],\n"
                    + "  \"endedAt\": \"2026-06-06T00:01:00Z\",\n"
                    + "  \"status\": \"finished\",\n"
                    + "  \"winner\": \"BOT1\"\n"
                    + "}\n");
            GameLogStats stats = GameLogStats.parse(log);
            check("stats parser counts turns", stats.turnCount() == 1);
            check("stats parser counts valid bells", stats.bellSuccessCount() == 1);
            check("stats parser counts false bells", stats.falseBellCount() == 1);
            check("stats parser detects winner", stats.winner().equals("BOT1"));
            check("stats parser reads difficulty", stats.effectiveBotDifficulty(null).equals("fast"));
        } catch (IOException e) {
            check("stats parser reads temp log", false);
        }
    }

    static void twoPlayerGameSupportsFruitBell() {
        Player a = visiblePlayer("A", new Card(Fruit.STRAWBERRY, 2, false));
        Player b = visiblePlayer("B", new Card(Fruit.STRAWBERRY, 3, false));
        GameManager game = new GameManager(List.of(a, b));
        BellResult r = game.ringBell(a);
        check("2-player fruit bell works", r.outcome() == RingOutcome.FRUIT);
        check("2-player winner collects both visible cards", a.totalCards() == 2);
        check("2-player game can end after one player loses all cards", game.isGameOver());
    }

    static void playersViewIsReadOnly() {
        GameManager game = new GameManager(List.of(new Player("A"), new Player("B"), new Player("C")));
        expectThrows("players view is read-only", UnsupportedOperationException.class,
                () -> game.players().add(new Player("D")));
    }

    static void playerConsumesOnlyHeldChips() {
        Player a = new Player("A");
        expectThrows("consume chip without chip", NoChipException.class, a::consumeChip);
    }

    // --------------------------------------------------------------- Table

    static void tableAggregatesVisibleTopCards() {
        Player a = new Player("A");
        Player b = new Player("B");
        Player c = new Player("C");
        // a card buried in the pile (must NOT be counted) plus the top card
        a.addToBottom(new Card(Fruit.BANANA, 1, false));
        a.addToBottom(new Card(Fruit.BANANA, 2, false));
        a.flip(); // visible: banana 1
        a.flip(); // visible: banana 2 (previous card moves underneath)
        b.addToBottom(new Card(Fruit.BANANA, 3, false));
        b.flip();
        c.addToBottom(new Card(Fruit.LIME, 4, false));
        c.flip();

        Table table = new Table(List.of(a, b, c));
        Map<Fruit, Integer> totals = table.fruitTotals();
        check("counts visible cards only: Banana=5", totals.getOrDefault(Fruit.BANANA, 0) == 5);
        check("Lime=4", totals.getOrDefault(Fruit.LIME, 0) == 4);
        check("fruit 5 -> bellable", table.isFruitBell());
    }

    static void fruitBellAt5() {
        Player a = visiblePlayer("A", new Card(Fruit.STRAWBERRY, 2, false));
        Player b = visiblePlayer("B", new Card(Fruit.STRAWBERRY, 3, false));
        Player c = visiblePlayer("C", new Card(Fruit.LIME, 1, false));
        Table table = new Table(List.of(a, b, c));
        check("Strawberry 2+3=5 bellable", table.isFruitBell());
        check("not a chip bell", !table.isChipBell());
    }

    static void chipBellAt2ForTwoPlayers() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, true));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 2, true));
        Table table = new Table(List.of(a, b));
        check("2-player chip target is 2", table.chipBellThreshold() == 2);
        check("2 chip symbols bellable in 2-player game", table.isChipBell());

        a.addToBottom(new Card(Fruit.BANANA, 4, false));
        b.addToBottom(new Card(Fruit.LIME, 4, false));
        GameManager game = new GameManager(List.of(a, b));
        BellResult r = game.ringBell(a);
        check("2-player chip bell resolves as CHIP", r.outcome() == RingOutcome.CHIP);
        check("2-player chip bell grants chip", a.chips() == 1);
    }

    static void chipBellDropsTo2WhenOnlyTwoPlayersRemain() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, true));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 2, true));
        Player c = visiblePlayer("C", new Card(Fruit.PLUM, 1, true));
        c.eliminate();
        Table table = new Table(List.of(a, b, c));
        check("active 2-player chip target is 2", table.chipBellThreshold() == 2);
        check("2 chip symbols bellable after one player is out", table.isChipBell());
    }

    static void chipBellAt3() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, true));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 2, true));
        Player c = visiblePlayer("C", new Card(Fruit.PLUM, 1, true));
        Table table = new Table(List.of(a, b, c));
        check("3 chip symbols bellable", table.isChipBell());
        check("not a fruit bell", !table.isFruitBell());
    }

    static void chipBellAtMoreThan3() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, true));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 1, true));
        Player c = visiblePlayer("C", new Card(Fruit.PLUM, 1, true));
        Player d = visiblePlayer("D", new Card(Fruit.STRAWBERRY, 1, true));
        Table table = new Table(List.of(a, b, c, d));
        check("4 chip symbols is also bellable", table.isChipBell());
        check("4 chip symbols count", table.chipSymbolCount() == 4);
    }

    // --------------------------------------------------------- ringBell results

    static void ringFruitWinsAllFaceUp() {
        Player a = visiblePlayer("A", new Card(Fruit.STRAWBERRY, 2, false));
        Player b = visiblePlayer("B", new Card(Fruit.STRAWBERRY, 3, false));
        Player c = visiblePlayer("C", new Card(Fruit.LIME, 1, false));
        GameManager game = new GameManager(List.of(a, b, c));
        BellResult r = game.ringBell(a);
        check("FRUIT outcome", r.outcome() == RingOutcome.FRUIT);
        check("A wins 3 face-up cards", a.totalCards() == 3);
        check("no chip won", a.chips() == 0);
        check("table cleared", !game.table().isFruitBell());
    }

    static void ringChipGrantsChipAndClearsCards() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, true));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 2, true));
        Player c = visiblePlayer("C", new Card(Fruit.PLUM, 1, true));
        a.addToBottom(new Card(Fruit.BANANA, 4, false));
        b.addToBottom(new Card(Fruit.LIME, 4, false));
        c.addToBottom(new Card(Fruit.PLUM, 4, false));
        GameManager game = new GameManager(List.of(a, b, c));
        int before = game.houseChips();
        BellResult r = game.ringBell(a);
        check("CHIP outcome", r.outcome() == RingOutcome.CHIP);
        check("A has 1 chip", a.chips() == 1);
        check("house chips -1", game.houseChips() == before - 1);
        check("chip bell clears table", !game.table().isChipBell());
        check("chip symbols reset to 0", game.table().chipSymbolCount() == 0);
        check("no cards won by chip-only bell", r.cardsWon() == 0);
        check("face-up cards were cleared, reserves remain", a.totalCards() == 1
                && b.totalCards() == 1 && c.totalCards() == 1);
    }

    static void ringBothWinsCardsAndChip() {
        // fruit 5 and 3 chip symbols simultaneously
        Player a = visiblePlayer("A", new Card(Fruit.STRAWBERRY, 2, true));
        Player b = visiblePlayer("B", new Card(Fruit.STRAWBERRY, 3, true));
        Player c = visiblePlayer("C", new Card(Fruit.LIME, 1, true));
        GameManager game = new GameManager(List.of(a, b, c));
        BellResult r = game.ringBell(a);
        check("BOTH outcome", r.outcome() == RingOutcome.BOTH);
        check("A has 3 cards", a.totalCards() == 3);
        check("A has 1 chip", a.chips() == 1);
    }

    static void invalidBellPenalizesRinger() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, false));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 1, false));
        Player c = visiblePlayer("C", new Card(Fruit.PLUM, 1, false));
        // give spare cards so the penalty can be paid
        a.addToBottom(new Card(Fruit.BANANA, 2, false));
        a.addToBottom(new Card(Fruit.BANANA, 3, false));
        GameManager game = new GameManager(List.of(a, b, c));
        BellResult r = game.ringBell(a);
        check("INVALID outcome", r.outcome() == RingOutcome.INVALID);
        check("B receives a penalty card", b.totalCards() == 2);
        check("C receives a penalty card", c.totalCards() == 2);
    }

    static void invalidBellPaysFaceUpCardsOneAtATime() {
        Player a = faceUpPlayer("A",
                new Card(Fruit.BANANA, 1, false),
                new Card(Fruit.LIME, 1, false),
                new Card(Fruit.PLUM, 1, false));
        Player b = visiblePlayer("B", new Card(Fruit.BANANA, 1, false));
        Player c = visiblePlayer("C", new Card(Fruit.LIME, 1, false));
        GameManager game = new GameManager(List.of(a, b, c));
        BellResult r = game.ringBell(a);
        check("face-up false bell is INVALID", r.outcome() == RingOutcome.INVALID);
        check("face-up false bell pays B", b.totalCards() == 2);
        check("face-up false bell pays C", c.totalCards() == 2);
        check("face-up false bell keeps unpaid cards", a.totalCards() == 1);
    }

    static void invalidBellEliminatingCurrentAdvancesTurn() {
        Player a = visiblePlayer("A", new Card(Fruit.BANANA, 1, false));
        Player b = visiblePlayer("B", new Card(Fruit.LIME, 1, false));
        Player c = visiblePlayer("C", new Card(Fruit.PLUM, 1, false));
        b.addToBottom(new Card(Fruit.BANANA, 2, false));
        c.addToBottom(new Card(Fruit.LIME, 2, false));
        GameManager game = new GameManager(List.of(a, b, c)); // turn starts at A
        BellResult r = game.ringBell(a);
        check("current player's last-card false bell is INVALID", r.outcome() == RingOutcome.INVALID);
        check("current player eliminated after paying last card", a.isEliminated());
        check("turn advances away from eliminated current", game.currentPlayer() != a
                && game.currentPlayer().isActive());
    }

    static void ringerLeadsNextTurn() {
        // visible: A=Strawberry2, B=Strawberry3 (=5 -> fruit bell), C=Lime1
        // extra draw cards so nobody is eliminated after the face-up pile is taken
        Player a = visiblePlayer("A", new Card(Fruit.STRAWBERRY, 2, false));
        a.addToBottom(new Card(Fruit.BANANA, 1, false));
        Player b = visiblePlayer("B", new Card(Fruit.STRAWBERRY, 3, false));
        b.addToBottom(new Card(Fruit.BANANA, 1, false));
        Player c = visiblePlayer("C", new Card(Fruit.LIME, 1, false));
        c.addToBottom(new Card(Fruit.BANANA, 1, false));
        GameManager game = new GameManager(List.of(a, b, c)); // turn starts at A
        check("turn starts at A", game.currentPlayer() == a);
        game.ringBell(b); // B wins the fruit bell
        check("after a valid bell, the ringer leads next turn", game.currentPlayer() == b);
        check("game not over (players still hold cards)", !game.isGameOver());
    }

    static void houseChipPoolDepletes() {
        Player a = new Player("A");
        Player b = new Player("B");
        Player c = new Player("C");
        queueChipBellCards(a, b, c, 4);
        GameManager game = new GameManager(List.of(a, b, c)); // houseChips=3

        showNextChipBell(a, b, c);
        game.ringBell(a);
        showNextChipBell(a, b, c);
        game.ringBell(b);
        showNextChipBell(a, b, c);
        game.ringBell(c); // the 3 house chips are now all spent
        check("house chips depleted", game.houseChips() == 0);

        showNextChipBell(a, b, c);
        BellResult r = game.ringBell(a); // after depletion
        check("no chip won after depletion", !r.chipWon());
        check("depleted chip bell still clears table", !game.table().isChipBell());
    }

    // ------------------------------------------------------------ life / elimination

    static void reviveConsumesChipAndPullsCards() {
        Player a = new Player("A"); // 0 cards, 1 chip
        a.addChip();
        Player b = new Player("B");
        Player c = new Player("C");
        for (int i = 0; i < 5; i++) {
            b.addToBottom(new Card(Fruit.BANANA, 1, false));
            c.addToBottom(new Card(Fruit.LIME, 1, false));
        }
        GameManager game = new GameManager(List.of(a, b, c));
        game.ensurePlayableOrEliminate(a);
        check("revive: chip spent", a.chips() == 0);
        check("revive: 3 from each = 6 cards pulled", a.totalCards() == 6);
        check("revive: B has 2 left", b.totalCards() == 2);
        check("revive: still active", a.isActive());
    }

    static void revivePullsMultipleFaceUpCardsWithoutDroppingPile() {
        Player a = new Player("A"); // 0 cards, 1 chip
        a.addChip();
        Player b = faceUpPlayer("B",
                new Card(Fruit.BANANA, 1, false),
                new Card(Fruit.BANANA, 2, false),
                new Card(Fruit.BANANA, 3, false));
        Player c = faceUpPlayer("C",
                new Card(Fruit.LIME, 1, false),
                new Card(Fruit.LIME, 2, false),
                new Card(Fruit.LIME, 3, false));
        GameManager game = new GameManager(List.of(a, b, c));
        game.ensurePlayableOrEliminate(a);
        check("revive from face-up piles pulls all 6 cards", a.totalCards() == 6);
        check("revive from face-up piles leaves B empty", b.totalCards() == 0);
        check("revive from face-up piles leaves C empty", c.totalCards() == 0);
    }

    static void reviveSettlesDonorsWhoRunOut() {
        Player a = new Player("A"); // 0 cards, 1 chip
        a.addChip();
        Player b = new Player("B");
        Player c = new Player("C");
        b.addToBottom(new Card(Fruit.BANANA, 1, false));
        c.addToBottom(new Card(Fruit.LIME, 1, false));

        GameManager game = new GameManager(List.of(a, b, c));
        game.ensurePlayableOrEliminate(a);

        check("revive donor B with 0 cards is eliminated", b.isEliminated());
        check("revive donor C with 0 cards is eliminated", c.isEliminated());
        check("revived player wins when donors are out", game.isGameOver() && game.winner() == a);
    }

    static void eliminationWhenNoChip() {
        Player a = new Player("A"); // 0 cards, 0 chips
        Player b = new Player("B");
        Player c = new Player("C");
        b.addToBottom(new Card(Fruit.BANANA, 1, false));
        c.addToBottom(new Card(Fruit.LIME, 1, false));
        GameManager game = new GameManager(List.of(a, b, c));
        game.ensurePlayableOrEliminate(a);
        check("eliminated when no chip", a.isEliminated());
    }

    static void outsidePlayerCannotTouchGameState() {
        Player a = visiblePlayer("A", new Card(Fruit.STRAWBERRY, 2, false));
        Player b = visiblePlayer("B", new Card(Fruit.STRAWBERRY, 3, false));
        Player c = visiblePlayer("C", new Card(Fruit.LIME, 1, false));
        GameManager game = new GameManager(List.of(a, b, c));
        Player outsider = new Player("OUTSIDER");

        expectThrows("outside player cannot ring", InvalidPlayerException.class,
                () -> game.ringBell(outsider));
        expectThrows("outside player cannot revive from game", InvalidPlayerException.class,
                () -> game.ensurePlayableOrEliminate(outsider));
        expectThrows("outside player cannot be force-eliminated", InvalidPlayerException.class,
                () -> game.forceEliminate(outsider));
        check("outside player did not collect table cards", outsider.totalCards() == 0);
        check("game table still intact after outside attempts", game.table().isFruitBell());
    }

    // -------------------------------------------------------------- integration

    static void fullSimulationProducesWinner() {
        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            players.add(new Player("P" + i));
        }
        Random random = new Random(25L);
        GameManager game = new GameManager(players, random);
        Player winner = new halligalli.runner.SimulationRunner(game, random, false).run();
        check("simulation ends", game.isGameOver());
        check("winner exists", winner != null);
        check("only one active player remains", game.activePlayers().size() == 1);
    }

    // ------------------------------------------------------------ helpers

    /** Creates a player whose given card is the visible top card. */
    static Player visiblePlayer(String name, Card visible) {
        Player p = new Player(name);
        p.addToBottom(visible);
        p.flip();
        return p;
    }

    /** Creates a player whose cards are all in the face-up pile; the last card is visible. */
    static Player faceUpPlayer(String name, Card... cards) {
        Player p = new Player(name);
        for (Card card : cards) {
            p.addToBottom(card);
        }
        for (int i = 0; i < cards.length; i++) {
            p.flip();
        }
        return p;
    }

    static void queueChipBellCards(Player a, Player b, Player c, int rounds) {
        for (int i = 0; i < rounds; i++) {
            a.addToBottom(new Card(Fruit.BANANA, 1, true));
            b.addToBottom(new Card(Fruit.LIME, 2, true));
            c.addToBottom(new Card(Fruit.PLUM, 1, true));
        }
    }

    static void showNextChipBell(Player a, Player b, Player c) {
        a.flip();
        b.flip();
        c.flip();
    }

    static void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + label);
        } else {
            failed++;
            System.out.println("  [FAIL] " + label);
        }
    }

    static void expectThrows(String label, Class<? extends Throwable> expected, Runnable action) {
        try {
            action.run();
            check(label, false);
        } catch (Throwable t) {
            check(label, expected.isInstance(t));
        }
    }
}
