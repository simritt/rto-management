#!/usr/bin/env bash
# Runs the project-local Maven with the project-local JDK (nothing is installed system-wide).
HERE="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$(ls -d "$HERE"/.tools/jdk-* | head -1)"
export PATH="$JAVA_HOME/bin:$PATH"
exec "$HERE/.tools/apache-maven-3.9.9/bin/mvn" -Dmaven.repo.local="$HERE/.tools/m2" "$@"
