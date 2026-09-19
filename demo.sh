#!/bin/bash
set -euo pipefail
mkdir -p build/classes
mapfile -t files < <(find src/main/java -name '*.java' -print | sort)
javac -encoding UTF-8 -d build/classes "${files[@]}" Demo.java
java -cp build/classes Demo
