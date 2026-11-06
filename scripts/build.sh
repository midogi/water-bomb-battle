#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out
find src -name '*.java' -print > out/sources.txt
javac --release 17 -encoding UTF-8 -d out @out/sources.txt
echo 'Build OK (Java 17 compatible).'
