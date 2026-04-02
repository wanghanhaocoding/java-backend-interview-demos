#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
PROJECT_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
OUTPUT_DIR=${OUTPUT_DIR:-"$PROJECT_ROOT/lab-output/oom"}
MAIN_CLASS="com.example.jvmstabilitydemo.oom.OomLeakDemo"

usage() {
    cat <<'EOF'
Usage:
  ./scripts/run-oom-lab.sh preview
  ./scripts/run-oom-lab.sh run

Commands:
  preview  Compile the project and print one safe preview round
  run      Compile the project and run the real OOM lab with GC log and heap dump
EOF
}

compile_with_javac() {
    mkdir -p "$PROJECT_ROOT/target/classes"
    find "$PROJECT_ROOT/src/main/java" -name '*.java' | sort > "$PROJECT_ROOT/target/java-sources.list"
    javac -encoding UTF-8 -d "$PROJECT_ROOT/target/classes" @"$PROJECT_ROOT/target/java-sources.list"
}

compile_project() {
    cd "$PROJECT_ROOT"
    if command -v mvn >/dev/null 2>&1; then
        if mvn -q -DskipTests compile; then
            return
        fi
        echo "Maven compile failed, falling back to javac." >&2
    else
        echo "Maven not found, falling back to javac." >&2
    fi

    compile_with_javac
}

run_preview() {
    compile_project
    cd "$PROJECT_ROOT"
    java -cp target/classes "$MAIN_CLASS" --preview
}

run_oom_lab() {
    compile_project
    mkdir -p "$OUTPUT_DIR"
    cd "$PROJECT_ROOT"

    echo "OOM lab output directory: $OUTPUT_DIR"
    echo "GC log: $OUTPUT_DIR/gc.log"
    echo "Heap dump directory: $OUTPUT_DIR"
    echo "Running $MAIN_CLASS ..."

    exec java \
        -Xms128m \
        -Xmx128m \
        -XX:+HeapDumpOnOutOfMemoryError \
        "-XX:HeapDumpPath=$OUTPUT_DIR" \
        -XX:+PrintGCDetails \
        -XX:+PrintGCDateStamps \
        "-Xloggc:$OUTPUT_DIR/gc.log" \
        -cp target/classes \
        "$MAIN_CLASS" \
        --run
}

case "${1:-}" in
    preview)
        run_preview
        ;;
    run)
        run_oom_lab
        ;;
    help|-h|--help|"")
        usage
        ;;
    *)
        echo "Unknown command: $1" >&2
        usage
        exit 1
        ;;
esac
