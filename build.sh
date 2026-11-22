#!/usr/bin/env bash
# Dependency-free build + test of the Minecraft-independent engine (cosmos-core).
# The Fabric mod build (fabric/) uses Gradle + Loom and needs network access to Mojang/Fabric maven.
set -euo pipefail
cd "$(dirname "$0")"
OUT=build/core
rm -rf "$OUT"; mkdir -p "$OUT/main" "$OUT/test"
find core/src/main/java -name '*.java' > "$OUT/main.txt"
javac --release 21 -Xlint:all,-serial -d "$OUT/main" @"$OUT/main.txt"
cp -r core/src/main/resources/. "$OUT/main/"
find core/src/test/java -name '*.java' > "$OUT/test.txt"
javac --release 21 -cp "$OUT/main" -d "$OUT/test" @"$OUT/test.txt"
(cd "$OUT/main" && jar --create --file ../cosmos-core.jar .)
mkdir -p "$OUT/preview"
find preview/src/main/java -name '*.java' > "$OUT/preview.txt"
javac --release 21 -cp "$OUT/main" -d "$OUT/preview" @"$OUT/preview.txt"
echo "built $OUT/cosmos-core.jar"
if [[ "${1:-}" != "--no-test" ]]; then
  java -cp "$OUT/main:$OUT/test" dev.saurus.cosmos.core.AllTests
fi
