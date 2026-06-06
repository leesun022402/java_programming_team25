# Halli Galli Plus+ — 설계/구현 계획

Team 25 · 2026 Spring JAVA Programming Lab
칩(목숨) 시스템을 도입한 변형 할리갈리 — 소켓 기반 실시간 멀티플레이를 최종 목표로 한다.

---

## 1. 변형 룰 (확정본)

기본 할리갈리(56장, 그중 16장에 칩 심볼)를 기반으로 한다.

1. **카드** 56장, 그중 16장에 칩 심볼이 그려져 있음. **하우스**는 플레이어 수만큼 **물리 칩(목숨 토큰)** 을 가지고 시작.
2. **종 치기**
   - 보이는 카드들의 **같은 과일 합계가 5** 이거나, **칩 심볼 목표치**를 채우면 종을 칠 수 있다.
   - 칩 심볼 목표치는 기본 3개이며, 활성 플레이어가 2명뿐이면 2개로 낮아진다.
   - 가장 먼저 종을 친 사람이 보상을 가진다.
   - 과일 5 충족: 테이블의 모든 공개 카드를 가져감.
   - 칩 심볼 목표치 충족: **칩 1개**를 가짐 + 공개 카드 초기화(공개 카드는 카드 보상으로 가져가지 않음).
   - 둘 다 충족: 칩, 카드 **모두** 가짐.
3. **칩 = 목숨.** 카드가 0장이 되어도 즉시 패배가 아니라, 칩 1개를 소비하고 다른 플레이어들에게서 **각 3장씩**(부족하면 가진 만큼) 받아 부활. 칩이 없으면 탈락.
4. 하우스 칩이 모두 분배되면 더 이상 추가되지 않음(**전체 칩 = 플레이어 수**로 제한). 칩은 **사용 시 하우스로 돌아오지 않음**(재사용 불가).
5. 나머지 룰은 표준 할리갈리와 동일 — 오발(false bell) 시 다른 플레이어 각각에게 카드 1장 벌칙.

**판정 핵심:** 보이는 카드 = 각 플레이어 **공개 더미의 맨 위 카드**만 집계(표준 할리갈리 방식).

---

## 2. 아키텍처 (계층 분리)

순수 **게임 엔진**과 **입력/구동 계층**을 분리해, 이후 소켓 계층이 같은 `GameManager`를 그대로 쓰도록 설계한다.

```
java_programming/
├─ src/halligalli/
│  ├─ model/      도메인 (I/O 없음, 순수 데이터/규칙)
│  │   ├─ Fruit.java        enum: BANANA, STRAWBERRY, LIME, PLUM
│  │   ├─ Card.java         불변 {Fruit, count(1~5), hasChip}
│  │   └─ Player.java       faceDown/faceUp 더미, chips, eliminated
│  ├─ core/       게임 규칙 엔진
│  │   ├─ DeckFactory.java  56장 생성 + 16장 칩 마킹 + 셔플
│  │   ├─ GameRules.java    JSON 설정에서 바꿀 수 있는 룰 값
│  │   ├─ Table.java        보이는 카드 집계(과일 합계, 칩 심볼 수)
│  │   ├─ RingOutcome.java  enum: FRUIT, CHIP, BOTH, INVALID
│  │   ├─ BellResult.java   판정 결과 + 획득 내역
│  │   └─ GameManager.java  턴 진행, 칩 풀, ringBell 판정, 목숨/탈락
│  ├─ exception/  커스텀 예외 (Week 13 대비)
│  │   ├─ HalliGalliException.java
│  │   ├─ InvalidGameSetupException.java
│  │   ├─ InvalidPlayerException.java
│  │   ├─ InvalidBellException.java
│  │   ├─ NoChipException.java
│  │   └─ GameOverException.java
│  └─ runner/     구동 계층 (엔진과 분리)
│      └─ SimulationRunner.java  봇 기반 데모(전체 라운드 시연)
│      (이후: SocketServer / SocketClient 가 동일 GameManager 사용)
├─ src/halligalli/stats/
│  ├─ GameLogStats.java    JSON 로그에서 전적 통계 추출
│  ├─ StatsDatabase.java   JDBC/SQLite 저장 및 집계 쿼리
│  └─ StatsCli.java        stats-import / stats 실행 진입점
├─ config/game_settings.json  GUI/서버 기본값 + 룰 옵션
├─ logs/                 JSON 게임 로그(실행 시 생성, git 제외)
├─ data/                 SQLite DB(실행 시 생성, git 제외)
├─ test/halligalli/test/  자체 테스트 (Week 13)
├─ build.sh / run.sh
└─ README.md
```

### 슬라이드 OOP 매핑

| 슬라이드 | 구현 |
|---|---|
| CARD | `model.Card` + `core.DeckFactory` |
| PLAYER | `model.Player` |
| CHIP | 물리 칩 = `GameManager.houseChips` + `Player.chips` / 칩 심볼 = `Card.hasChip` |
| GAME MANAGER | `core.GameManager` (칩 관리 + BELL Validation) |
| TABLE | `core.Table` (과일별/칩 심볼 집계) |

---

## 3. 핵심 도메인

- **Card**: 불변. 표준 분포 — 과일당 14장(개수 1:5장, 2:3장, 3:3장, 4:2장, 5:1장) × 4종 = 56장. 과일당 4장씩 16장에 칩 심볼.
- **Player**: `faceDown`(뽑을 더미, Deque), `faceUp`(공개 더미, Deque), `chips`(0에서 시작), `eliminated`.
- **Table**: 각 플레이어 공개 더미 맨 위 카드만 집계.
  - `fruitTotals()`: 과일별 개수 합
  - `chipSymbolCount()`: 칩 심볼 보이는 카드 수

---

## 4. 종 치기 판정 (GameManager.ringBell)

엔진은 **판정 API만** 제공하고, 누가 먼저인지는 `ringBell()` 호출 순서로 결정(입력 소스 분리).

| 조건 | Outcome | 처리 |
|---|---|---|
| 과일 5 | FRUIT | 모든 공개 카드를 승자 faceDown 바닥으로, 공개 더미 비움 |
| 칩 심볼 목표치 충족 | CHIP | 하우스 풀에서 칩 1개 → 승자(풀 소진 시 0), 공개 카드 초기화 |
| 둘 다 | BOTH | 카드 + 칩 모두 |
| 미충족 | INVALID | 오발: 승자가 다른 활성 플레이어 각각에게 카드 1장 벌칙 |

> 유효한 종(FRUIT/CHIP/BOTH)을 친 플레이어가 **다음 턴을 시작**한다(먼저 카드를 냄). 오발(INVALID)은 턴이 그대로 진행.

---

## 5. 칩(목숨) & 탈락

- 하우스 칩 풀 = 플레이어 수에서 시작. 칩-종 승리로 분배. 재사용 불가. 풀 소진 시 추가 없음.
- 카드 0장 → 칩 있으면 1 소비 + 다른 활성 플레이어에게서 각 min(3, 보유) 회수해 부활. 그래도 0장이면 탈락. 칩 없으면 즉시 탈락.
- 최후 1인 생존 시 승리.

---

## 6. 구동 / 테스트

- **SimulationRunner**: 봇들이 무작위 반응으로 종을 치는 전체 게임을 콘솔 시연(엔진 검증). 시드 고정으로 재현 가능.
- **test/**: DeckFactory(56장·16칩), Table 집계, 각 RingOutcome, 목숨/탈락 시나리오를 plain-Java 어서션으로 검증. JUnit은 Week 13에 추가 가능하도록 구조만 분리.
- 빌드: 의존성 없는 `javac` (`build.sh` / `run.sh`). Maven/Gradle 선택.
- **JSON 설정**: `config/game_settings.json`에서 기본 포트, 기본 이름, 봇 난이도/수, seed,
  로그 저장 여부, 룰 옵션(`fruitBellThreshold`, `chipBellThreshold`, `reviveCardsPerPlayer`)을 읽는다.
- **JSON 게임 로그/리플레이**: 소켓 서버가 `EVENT`, `STATE`, `TURN`, `GAMEOVER` 등을 `logs/game-*.json`에 저장하고,
  `./run.sh replay <log.json>`으로 터미널에서 순서대로 다시 확인한다.
- **SQLite/JDBC 전적 DB**: `./run.sh stats-import <log.json>`이 JSON 로그를 파싱해
  `data/halligalli_stats.db`에 `games`, `players`, `player_game_stats` 테이블로 저장한다.
  `./run.sh stats`는 플레이어별 승률, 평균 턴 수, 벨 성공/오발 횟수와 봇 난이도별 승률을 출력한다.

---

## 7. 구현 일정 (슬라이드 매핑)

| Week | 작업 | 본 설계 대응 |
|---|---|---|
| 10–11 | Basic OOP | model + core + runner(봇 시뮬) + 자체 테스트 ← **완료** |
| 12 | Socket Programming | `halligalli.net` 권위 서버 + 콘솔/봇 클라이언트 ← **완료** |
| 13 | Exceptions & Testing + JSON/DB | exception 강화, JSON 설정/로그/리플레이, SQLite/JDBC 전적 DB ← **완료**, JUnit 도입 |
| 14 | Simulations & Slides | 시뮬 통계/시연, 발표자료 |

---

## 8. 소켓 멀티플레이 (Week 12, 구현 완료)

### 모델: 권위 서버 + 도착 순서

- **서버가 게임 상태(`GameManager`)를 단독 보유**한다. 클라이언트는 명령만 보낸다.
- 모든 명령은 서버의 단일 락으로 **직렬화(synchronized)** → 도착 순서대로 처리.
- **FLIP**은 자기 차례에만, **BELL**은 누구나 언제든 가능. 가장 먼저 락을 잡은 유효 BELL이
  승리하므로, **네트워크 지연 + 반응 속도가 경쟁을 결정**(실제 할리갈리와 동일).
- 늦게 도착한 BELL은 이미 테이블 상태가 바뀌어 `INVALID`(오발 벌칙)로 처리 — 자연스러운 페널티.

### 프로토콜 (줄 단위 텍스트, UTF-8)

- C→S: `JOIN <name>`, `FLIP`, `BELL`, `QUIT`
- S→C: `WELCOME`, `INFO`, `START`, `EVENT <설명>`, `STATE|...`, `ERROR`, `GAMEOVER <name>`
- `STATE` 예: `STATE|turn=P1|bellable=false|house=3|chipsym=0|fruits=BANANA:3,LIME:1|p=P1,12,0,active|...`

### 클래스 (`halligalli.net`)

| 클래스 | 역할 |
|---|---|
| `Protocol` | 메시지 상수 |
| `GameServer` | 권위 서버. accept 루프, 명령 직렬화, 브로드캐스트, 종료 관리 |
| `ClientHandler` | 연결 1개당 스레드(명령 수신 → 서버 전달, 메시지 송신) |
| `StateView` | `STATE` 줄 파싱 + 보드 렌더링(콘솔/봇/GUI 공유) |
| `GameClient` | 사람용 콘솔 클라이언트(수신 스레드 + stdin 입력) |
| `SwingClient` | 사람용 Swing GUI 클라이언트(그래픽 보드 + FLIP/BELL 버튼, 무의존 커스텀 페인팅) |
| `GameLauncher` | 서버/참가/봇 수/봇 난이도를 선택하는 Swing 런처 |
| `BotClient` | 자동 플레이 봇(무작위 반응으로 종 경쟁 생성, 검증/시연용) |
| `GameSettings` | `config/game_settings.json` 파싱 및 기본값 제공 |
| `GameLogRecorder` | 게임 이벤트/상태를 JSON 로그로 저장 |
| `GameReplay` | 저장된 JSON 로그를 터미널 리플레이로 출력 |

### SQLite/JDBC 전적 DB (`halligalli.stats`)

| 클래스 | 역할 |
|---|---|
| `GameLogStats` | JSON 로그를 읽어 플레이어별 FLIP 수, 벨 성공, 오발, 최종 카드/칩, 승자를 집계 |
| `StatsDatabase` | JDBC로 SQLite DB 연결, 테이블 생성, import, 전적/난이도별 승률 쿼리 수행 |
| `StatsCli` | `./run.sh stats-import`, `./run.sh stats`, `./run.sh stats games` 명령 처리 |

DB 스키마:

| 테이블 | 내용 |
|---|---|
| `players` | 플레이어 이름, 봇 여부 |
| `games` | 로그 파일, 시작/종료 시각, seed, 플레이어 수, 봇 난이도, 총 턴 수, 승자 |
| `player_game_stats` | 게임별 플레이어 승패, FLIP 수, 벨 성공/오발 횟수, 최종 카드/칩 |

> 모든 터미널/GUI 출력은 영어. `STATE` 메시지에 각 플레이어의 보이는 카드(`p=name,cards,chips,status,FRUIT:count:chip`)를 포함해 GUI가 실제 카드를 그릴 수 있다.

엔진(`core`)은 전혀 수정 없이 재사용했고, 연결 끊김 시 턴 교착을 막기 위해
`GameManager.forceEliminate(Player)`만 추가했다.

### 검증

- `net_demo.sh`로 서버 + 봇 3 자동 게임 → 권위 서버가 한 게임을 끝까지 진행하고 승자 도출.
- 사람 콘솔 클라이언트 + 봇 2 혼합 → STATE 보드 렌더링, EVENT/ERROR 수신, 종 경쟁(수십 회) 정상.
