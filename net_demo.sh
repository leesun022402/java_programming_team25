#!/usr/bin/env bash
# 소켓 멀티플레이 데모: 서버 1 + 봇 3을 띄워 한 게임을 자동 진행한다.
#   ./net_demo.sh [port] [players] [seed]
set -uo pipefail
cd "$(dirname "$0")"

PORT="${1:-5557}"
PLAYERS="${2:-3}"
SEED="${3:-25}"

[ -d build ] || ./build.sh

echo "=== 소켓 데모: 서버 + 봇 ${PLAYERS} (port=${PORT}) ==="
java -cp build halligalli.net.GameServer "$PORT" "$PLAYERS" "$SEED" &
SERVER_PID=$!
sleep 1

BOT_PIDS=()
for i in $(seq 1 "$PLAYERS"); do
  java -cp build halligalli.net.BotClient localhost "$PORT" "BOT$i" "$((SEED + i))" fast &
  BOT_PIDS+=($!)
  sleep 0.2
done

# 서버 종료를 기다림(게임 끝나면 셧다운)
wait "$SERVER_PID"
for pid in "${BOT_PIDS[@]}"; do
  kill "$pid" 2>/dev/null || true
done
echo "=== 데모 종료 ==="
