#!/usr/bin/env bash
# Starts the API on http://127.0.0.1:8000  (Swagger UI: /docs). Reads settings from .env in this folder.
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
export JAVA_HOME="$(ls -d "$HERE"/.tools/jdk-* 2>/dev/null | head -1)"; [ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
[ -f target/rto-management-backend-1.0.0.jar ] || ./mvn.sh -q -DskipTests package
exec java -jar target/rto-management-backend-1.0.0.jar "$@"
