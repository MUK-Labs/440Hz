#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
check_dir="$(mktemp -d)"
trap 'rm -rf "$check_dir"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -d "$check_dir" "$project_dir/app/src/main/java/org/pitch440/app/Schedule.java" "$project_dir/tools/ScheduleChecks.java"
java -cp "$check_dir" ScheduleChecks
