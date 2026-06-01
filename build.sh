#!/usr/bin/env bash
# 의존성 없는 빌드: src + test 를 build/ 로 컴파일한다.
set -euo pipefail
cd "$(dirname "$0")"

OUT=build
rm -rf "$OUT"
mkdir -p "$OUT"

echo "컴파일 중..."
find src test -name "*.java" > .sources.txt
javac -d "$OUT" -encoding UTF-8 @.sources.txt
rm -f .sources.txt
echo "빌드 완료 → $OUT/"
