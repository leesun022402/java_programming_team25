# Halli Galli Plus+ — Design / Implementation Plan

Team 25 · 2026 Spring JAVA Programming Lab
A Halli Galli variant with a chip (life) system. Final goal: socket-based real-time multiplayer.

---

## 1. Variant Rules (finalized)

Based on standard Halli Galli (56 cards, 16 of which carry a chip symbol).

1. **Cards**: 56 total, 16 carry a chip symbol. The **house** starts with one **physical chip
   (life token)** per player.
2. **Ringing the bell**
   - You may ring when the visible cards show **a fruit total of 5** OR **3+ chip symbols**.
   - Whoever rings first takes the reward.
   - Fruit total 5: take all face-up cards on the table.
   - 3 chip symbols: take **1 chip** and reset the face-up cards (they are not awarded as a card reward).
   - Both met: take **both** the cards and the chip.
3. **Chips = lives.** Running out of cards is not an instant loss: spend 1 chip and receive
   **3 cards from each other player** (or fewer if they hold fewer) to revive. With no chip, you are eliminated.
4. Once the house has handed out all its chips, no more are added (**total chips = player count**).
   A chip **never returns to the house** once used (not reusable).
5. All other rules follow standard Halli Galli — a false bell pays 1 card to each other player.

**Key for evaluation:** the visible cards are only the **top card of each player's face-up pile**
(standard Halli Galli).

---

## 2. Architecture (layer separation)

The pure **game engine** is separated from the **input/driver layer** so that the socket layer
later reuses the same `GameManager` unchanged.

```
java_programming/
├─ src/halligalli/
│  ├─ model/      domain (no I/O, pure data/rules)
│  │   ├─ Fruit.java        enum: BANANA, STRAWBERRY, LIME, PLUM
│  │   ├─ Card.java         immutable {Fruit, count(1-5), hasChip}
│  │   └─ Player.java       faceDown/faceUp piles, chips, eliminated
│  ├─ core/       game-rule engine
│  │   ├─ DeckFactory.java  builds 56 cards + marks 16 chips + shuffles
│  │   ├─ Table.java        aggregates visible cards (fruit totals, chip count)
│  │   ├─ RingOutcome.java  enum: FRUIT, CHIP, BOTH, INVALID
│  │   ├─ BellResult.java   ring result + reward details
│  │   └─ GameManager.java  turn flow, chip pool, ringBell validation, life/elimination
│  ├─ exception/  custom exceptions (for Week 13)
│  │   ├─ HalliGalliException.java
│  │   ├─ InvalidBellException.java
│  │   └─ GameOverException.java
│  ├─ runner/     local driver (separate from the engine)
│  │   └─ SimulationRunner.java  bot-based demo (full-round playthrough)
│  └─ net/        socket layer (Week 12)
│      ├─ Protocol.java     message constants
│      ├─ GameServer.java   authoritative server
│      ├─ ClientHandler.java per-connection thread
│      ├─ StateView.java    STATE parser + board renderer
│      ├─ GameClient.java   human console client
│      └─ BotClient.java    auto-playing bot client
├─ test/halligalli/test/  self-tests (Week 13)
├─ build.sh / run.sh / net_demo.sh
└─ README.md
```

### Mapping to the slide's OOP design

| Slide | Implementation |
|---|---|
| CARD | `model.Card` + `core.DeckFactory` |
| PLAYER | `model.Player` |
| CHIP | physical chips = `GameManager.houseChips` + `Player.chips` / chip symbol = `Card.hasChip` |
| GAME MANAGER | `core.GameManager` (chip management + BELL validation) |
| TABLE | `core.Table` (per-fruit / chip-symbol aggregation) |

---

## 3. Core domain

- **Card**: immutable. Standard distribution — 14 per fruit (count 1:5, 2:3, 3:3, 4:2, 5:1)
  × 4 fruits = 56 cards. 4 per fruit (16 total) carry a chip symbol.
- **Player**: `faceDown` (draw pile, Deque), `faceUp` (face-up pile, Deque), `chips` (starts at 0), `eliminated`.
- **Table**: aggregates only each player's top face-up card.
  - `fruitTotals()`: per-fruit sums
  - `chipSymbolCount()`: number of visible cards with a chip symbol

---

## 4. Bell validation (GameManager.ringBell)

The engine only provides the **validation API**; who rings first is decided by the call order of
`ringBell()` (the input source is separate).

| Condition | Outcome | Handling |
|---|---|---|
| fruit 5 | FRUIT | move all face-up cards to the winner's draw pile bottom; clear face-up piles |
| 3+ chip symbols | CHIP | grant 1 chip from the house pool (0 if depleted); reset the face-up cards |
| both | BOTH | cards + chip |
| neither | INVALID | false bell: ringer pays 1 card to each other active player |

> The player who rings a valid bell (FRUIT/CHIP/BOTH) **leads the next turn** (flips first). A false bell (INVALID) leaves the turn order unchanged.

---

## 5. Chips (lives) & elimination

- House chip pool starts at the player count. Distributed via chip-bell wins. Not reusable. No top-ups once depleted.
- 0 cards → if a chip is held, spend 1 and pull min(3, held) cards from each other active player to revive.
  Still 0 afterwards → eliminated. No chip → eliminated immediately.
- Last player standing wins.

---

## 6. Driver / testing

- **SimulationRunner**: bots compete with random reactions in a full console playthrough (engine verification). Seeded for reproducibility.
- **test/**: verifies DeckFactory (56 cards / 16 chips), Table aggregation, each RingOutcome, and life/elimination scenarios with plain-Java assertions. JUnit can be introduced in Week 13; the structure is already separated for it.
- Build: dependency-free `javac` (`build.sh` / `run.sh`). Maven/Gradle optional.

---

## 7. Implementation schedule (mapped to the slides)

| Week | Task | Mapping |
|---|---|---|
| 10–11 | Basic OOP | model + core + runner (bot sim) + self-tests ← **done** |
| 12 | Socket Programming | `halligalli.net` authoritative server + console/bot clients ← **done** |
| 13 | Exceptions & Testing | strengthen exceptions + introduce JUnit |
| 14 | Simulations & Slides | sim statistics/demo, presentation slides |

---

## 8. Socket multiplayer (Week 12, implemented)

### Model: authoritative server + arrival order

- The **server holds the game state (`GameManager`) exclusively**. Clients only send commands.
- All commands are **serialized under a single server lock (synchronized)** → processed in arrival order.
- **FLIP** only on your turn; **BELL** anytime by anyone. The first valid BELL to acquire the lock wins,
  so **network latency + reaction speed decide the race** (as in real Halli Galli).
- A late BELL finds the table already changed and resolves to `INVALID` (false-bell penalty) — a natural penalty.

### Protocol (line-based text, UTF-8)

- C→S: `JOIN <name>`, `FLIP`, `BELL`, `QUIT`
- S→C: `WELCOME`, `INFO`, `START`, `EVENT <text>`, `STATE|...`, `ERROR`, `GAMEOVER <name>`
- `STATE` example: `STATE|turn=P1|bellable=false|house=3|chipsym=0|fruits=BANANA:3,LIME:1|p=P1,12,0,active|...`

### Classes (`halligalli.net`)

| Class | Role |
|---|---|
| `Protocol` | message constants |
| `GameServer` | authoritative server: accept loop, command serialization, broadcasting, shutdown |
| `ClientHandler` | one thread per connection (receive commands → forward; send messages) |
| `StateView` | parse `STATE` line + render the board (shared by console/bot/GUI) |
| `GameClient` | human console client (reader thread + stdin input) |
| `SwingClient` | human Swing GUI client (graphical board + FLIP/BELL buttons, dependency-free custom painting) |
| `BotClient` | auto-playing bot (random reactions create the bell race; for verification/demo) |

> All terminal/GUI output is in English. The `STATE` message includes each player's visible card
> (`p=name,cards,chips,status,FRUIT:count:chip`) so the GUI can draw the actual cards.

The engine (`core`) was reused without modification; only `GameManager.forceEliminate(Player)`
was added to avoid a turn deadlock on disconnect.

### Verification

- `net_demo.sh` (server + 3 bots) → the authoritative server plays a full game to completion and yields a winner.
- Human console client + 2 bots → STATE board rendering, EVENT/ERROR reception, bell race (dozens of times) all work.
