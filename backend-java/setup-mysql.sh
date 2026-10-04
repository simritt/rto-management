#!/usr/bin/env bash
# One command to connect YOUR MySQL: creates the app user + database, seeds roles, writes .env.
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
export JAVA_HOME="$(ls -d "$HERE"/.tools/jdk-* 2>/dev/null | head -1)"; [ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
[ -f target/rto-management-backend-1.0.0.jar ] || ./mvn.sh -q -DskipTests package
exec java -jar target/rto-management-backend-1.0.0.jar --rto.command=setup-mysql "$@"
