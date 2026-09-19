#!/bin/bash
set -euo pipefail
mkdir -p build/classes
mapfile -t files < <(find src/main/java tests -name '*.java' -print | sort)
javac -encoding UTF-8 -d build/classes "${files[@]}"
for source in tests/*Test.java; do
  java -ea -cp build/classes "$(basename "$source" .java)"
done
