# Halli Galli Plus+ (Team 25)

A Halli Galli variant with a chip (life) system. 2026 Spring JAVA Programming Lab.

See [docs/DESIGN.en.md](docs/DESIGN.en.md) for the full design and rules (Korean: [docs/DESIGN.md](docs/DESIGN.md)).

## Requirements
- JDK 17+ (developed/verified on OpenJDK 21)
- No external dependencies (plain `javac`)

## Build & run

```bash
./build.sh            # compile src + test -> build/
./run.sh test         # run the self-tests
./run.sh sim          # local bot simulation (default seed=25, 3 players)
./run.sh sim 42 4     # seed=42, 4-player simulation
```

### Socket multiplayer (Week 12)

```bash
# 1) Server (waits for 3 players)
./run.sh server 5555 3

# 2) Connect a client from another terminal (human, console)
./run.sh client localhost 5555 MyName
#    input: f=FLIP (on your turn), b=BELL (anytime), q=quit

# 2') Or the Swing GUI client (graphical board + FLIP/BELL buttons)
./run.sh gui localhost 5555 MyName

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

## Structure

| Package | Role |
|---|---|
| `halligalli.model` | domain: `Fruit`, `Card` (immutable), `Player` |
| `halligalli.core` | engine: `DeckFactory`, `Table`, `GameManager`, `RingOutcome`, `BellResult` |
| `halligalli.exception` | custom exceptions |
| `halligalli.runner` | local driver: `SimulationRunner` |
| `halligalli.net` | socket layer: `GameServer`, `ClientHandler`, `GameClient` (console), `SwingClient` (GUI), `BotClient`, `StateView`, `Protocol` |
| `halligalli.test` | self-test runner |

The engine is separate from the input source (console/bot/socket), so the socket layer
(`halligalli.net`) reuses the same `GameManager` unchanged.

## Rules summary
- 56 cards, 16 of which carry a chip symbol. The house starts with one physical chip (life) per player.
- From the visible cards (each player's top face-up card): **a fruit total of 5** or **3 chip symbols** → bell.
  - Fruit 5 → win all face-up cards / 3 chips → win 1 chip (cards stay) / both → win both.
- 0 cards → spend 1 chip and receive 3 cards from each other player to revive. No chip → eliminated.
- A false bell pays 1 card to each other player. Last player standing wins.
- The player who rings a valid bell leads the next turn (flips first).

## Progress
- [x] Week 10–11: Basic OOP (model/core/runner + 45 self-tests passing)
- [x] Week 12: socket multiplayer (authoritative server + console/GUI/bot clients, verified via net_demo)
- [ ] Week 13: stronger exceptions + JUnit
- [ ] Week 14: simulation statistics + presentation slides
