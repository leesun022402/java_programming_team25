#!/usr/bin/env bash
# 실행 진입점.
#   ./run.sh               -> 로컬 봇 시뮬레이션(기본 seed=25, 3인)
#   ./run.sh sim 42 4      -> 로컬 시뮬레이션(seed=42, 4인)
#   ./run.sh test          -> 자체 테스트
#   ./run.sh server [port] [players] [seed]   -> 소켓 서버
#   ./run.sh client [host] [port] [name]      -> 사람 콘솔 클라이언트
#   ./run.sh gui    [host] [port] [name]      -> 사람 Swing GUI 클라이언트
#   ./run.sh gui                              -> GUI 런처(서버/봇/난이도 선택)
#   ./run.sh launcher                         -> GUI 런처
#   ./run.sh bot [host] [port] [name] [seed] [level]  -> 봇 클라이언트
#        level = fast(봇데모) | easy | normal(기본) | hard  : 봇 반응속도
#   ./run.sh replay logs/game-*.json          -> JSON 게임 로그 터미널 리플레이
#   ./run.sh stats-import logs/game-*.json --difficulty fast
#   ./run.sh stats                            -> SQLite 전적 요약
set -euo pipefail
cd "$(dirname "$0")"

[ -d build ] || ./build.sh

CP="build"
for jar in lib/*.jar; do
  [ -e "$jar" ] && CP="$CP:$jar"
done

MODE="${1:-sim}"
case "$MODE" in
  test)
    java -cp "$CP" halligalli.test.TestRunner
    ;;
  sim)
    shift || true
    java -cp "$CP" halligalli.runner.SimulationRunner "$@"
    ;;
  server)
    shift || true
    java -cp "$CP" halligalli.net.GameServer "$@"
    ;;
  client)
    shift || true
    java -cp "$CP" halligalli.net.GameClient "$@"
    ;;
  gui)
    shift || true
    java -cp "$CP" halligalli.net.SwingClient "$@"
    ;;
  launcher)
    java -cp "$CP" halligalli.net.GameLauncher
    ;;
  bot)
    shift || true
    java -cp "$CP" halligalli.net.BotClient "$@"
    ;;
  replay)
    shift || true
    java -cp "$CP" halligalli.net.GameReplay "$@"
    ;;
  stats-import)
    shift || true
    java -cp "$CP" halligalli.stats.StatsCli import "$@"
    ;;
  stats)
    shift || true
    if [ "$#" -eq 0 ]; then
      java -cp "$CP" halligalli.stats.StatsCli summary
    else
      java -cp "$CP" halligalli.stats.StatsCli "$@"
    fi
    ;;
  *)
    # 첫 인자가 숫자면 시뮬 인자로 간주
    java -cp "$CP" halligalli.runner.SimulationRunner "$@"
    ;;
esac
