#!/usr/bin/env bash
# 실행 진입점.
#   ./run.sh               -> 로컬 봇 시뮬레이션(기본 seed=25, 3인)
#   ./run.sh sim 42 4      -> 로컬 시뮬레이션(seed=42, 4인)
#   ./run.sh test          -> 자체 테스트
#   ./run.sh server [port] [players] [seed]   -> 소켓 서버
#   ./run.sh client [host] [port] [name]      -> 사람 콘솔 클라이언트
#   ./run.sh gui    [host] [port] [name]      -> 사람 Swing GUI 클라이언트
#   ./run.sh bot [host] [port] [name] [seed] [level]  -> 봇 클라이언트
#        level = fast(봇데모) | easy | normal(기본) | hard  : 봇 반응속도
set -euo pipefail
cd "$(dirname "$0")"

[ -d build ] || ./build.sh

MODE="${1:-sim}"
case "$MODE" in
  test)
    java -cp build halligalli.test.TestRunner
    ;;
  sim)
    shift || true
    java -cp build halligalli.runner.SimulationRunner "$@"
    ;;
  server)
    shift || true
    java -cp build halligalli.net.GameServer "$@"
    ;;
  client)
    shift || true
    java -cp build halligalli.net.GameClient "$@"
    ;;
  gui)
    shift || true
    java -cp build halligalli.net.SwingClient "$@"
    ;;
  bot)
    shift || true
    java -cp build halligalli.net.BotClient "$@"
    ;;
  *)
    # 첫 인자가 숫자면 시뮬 인자로 간주
    java -cp build halligalli.runner.SimulationRunner "$@"
    ;;
esac
