#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/build.sh
find tests -name '*.java' -print > out/test-sources.txt
javac --release 17 -encoding UTF-8 -cp out -d out @out/test-sources.txt
java -ea -cp out tests.ProtocolTest
java -ea -cp out tests.GameLogicTest
