# Halli Galli Plus+ (Team 25)

A Halli Galli variant with a chip (life) system. 2026 Spring JAVA Programming Lab.

See [docs/DESIGN.en.md](docs/DESIGN.en.md) for the full design and rules (Korean: [docs/DESIGN.md](docs/DESIGN.md)).

## Requirements
- JDK 17+ (developed/verified on OpenJDK 21)
- Core game: no external dependencies (plain `javac`)
- SQLite stats feature: Xerial SQLite JDBC jar under `lib/` (`sqlite-jdbc-*.jar`)

## Build & run

```bash
./build.sh            # compile src + test -> build/
./run.sh test         # run the self-tests
./run.sh sim          # local bot simulation (default seed=25, 3 players)
./run.sh sim 42 4     # seed=42, 4-player simulation
./run.sh gui          # graphical launcher (host/join, bots, difficulty)
./run.sh replay logs/game-YYYYMMDD-HHMMSS-SSS.json
./run.sh stats-import logs/game-YYYYMMDD-HHMMSS-SSS.json --difficulty fast
./run.sh stats
```

### Socket multiplayer (Week 12)

```bash
# 1) Server (waits for the configured number of players; 2+ supported)
./run.sh server 5555 3

# 2) Connect a client from another terminal (human, console)
./run.sh client localhost 5555 MyName
#    input: f=FLIP (on your turn), b=BELL (anytime), q=quit

# 2') Or the Swing GUI client (graphical board + FLIP/BELL buttons)
./run.sh gui localhost 5555 MyName

# 2'') Or open the GUI launcher and choose host/join/bots/difficulty there
./run.sh gui

# Fill seats with bots — level: fast | easy | normal | hard (5th arg)
./run.sh bot localhost 5555 BOT1 1 normal

# One-shot auto demo (server + 3 bots, fast)
./net_demo.sh 5557 3 25
```

> All game output is in English. Run the GUI client on a machine with a display (X11/desktop).

The server is **authoritative** — it holds the game state exclusively and serializes commands
in arrival order. FLIP is allowed only on your turn; BELL is allowed by anyone at any time —
the first valid BELL wins (network latency + reaction speed decide the race).

Bot speed levels (5th `bot` arg): `fast` (bot-vs-bot demo), `easy`, `normal` (default, a fair
fight against a human), `hard`.

### JSON config, logs, replay

- `config/game_settings.json` controls launcher/server defaults: port, default player name,
  total players, bot count, bot difficulty, seed, JSON log directory, and rule options
  (`fruitBellThreshold`, `chipBellThreshold`, `reviveCardsPerPlayer`). It also stores the
  default SQLite DB path (`databasePath`).
- When `enableJsonLogs` is true, socket games write replay-friendly event/state logs to `logs/`.
- Replay a saved log from the terminal:

```bash
./run.sh replay logs/game-YYYYMMDD-HHMMSS-SSS.json
```

### SQLite / JDBC stats DB

Socket game logs can be imported into a SQLite database (`data/halligalli_stats.db`) through JDBC.
The DB stores games, players, per-player wins/losses, turn counts, successful bells, false bells,
and bot difficulty win rates.

```bash
./run.sh stats-import logs/game-YYYYMMDD-HHMMSS-SSS.json --difficulty fast
./run.sh stats
./run.sh stats games
```

## Structure

| Package | Role |
|---|---|
| `halligalli.model` | domain: `Fruit`, `Card` (immutable), `Player` |
| `halligalli.core` | engine: `DeckFactory`, `Table`, `GameManager`, `GameRules`, `RingOutcome`, `BellResult` |
| `halligalli.exception` | custom exceptions |
| `halligalli.runner` | local driver: `SimulationRunner` |
| `halligalli.net` | socket layer: `GameServer`, `ClientHandler`, `GameClient` (console), `SwingClient` (GUI), `GameLauncher`, `BotClient`, `StateView`, `GameSettings`, `GameLogRecorder`, `GameReplay`, `Protocol` |
| `halligalli.stats` | SQLite/JDBC stats importer and standings CLI |
| `halligalli.test` | self-test runner |

The engine is separate from the input source (console/bot/socket), so the socket layer
(`halligalli.net`) reuses the same `GameManager` unchanged.

## Rules summary
- 56 cards, 16 of which carry a chip symbol. The house starts with one physical chip (life) per player.
- From the visible cards (each player's top face-up card): **a fruit total of 5** or **enough chip symbols** → bell.
  - Chip target is normally 3, but drops to 2 when only 2 players are active.
  - Fruit 5 → win all face-up cards / chip target met → win 1 chip and clear the face-up cards / both → win both.
- 0 cards → spend 1 chip and receive 3 cards from each other player to revive. No chip → eliminated.
- A false bell pays 1 card to each other player. Last player standing wins.
- The player who rings a valid bell leads the next turn (flips first).

## Progress
- [x] Week 10–11: Basic OOP (model/core/runner + self-tests passing)
- [x] Week 12: socket multiplayer (authoritative server + console/GUI/bot clients, verified via net_demo)
- [x] Week 13: stronger exceptions + JSON settings/logs + SQLite/JDBC stats done; JUnit migration pending
- [ ] Week 14: simulation statistics + presentation slides
