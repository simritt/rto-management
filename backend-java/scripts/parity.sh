#!/usr/bin/env bash
# Parity gate: runs the reference test-suite (backend/tests, Python) over HTTP against THIS Java backend.
#
#   scripts/parity.sh                         # whole suite
#   scripts/parity.sh tests/test_auth.py -x   # any pytest arguments
#
# Needs a MySQL account that may create/drop the test database. Defaults match the throw-away dev instance
# (127.0.0.1:3399, root, no password); override with DB_HOST / DB_PORT / DB_USER / DB_PASSWORD.
set -euo pipefail
HERE="$(cd "$(dirname "$0")/.." && pwd)"
PY_BACKEND="$HERE/../backend"
PY="$PY_BACKEND/.venv/Scripts/python"; [ -x "$PY" ] || PY="$PY_BACKEND/.venv/bin/python"
export DB_HOST="${DB_HOST:-127.0.0.1}" DB_PORT="${DB_PORT:-3399}" DB_USER="${DB_USER:-root}" DB_PASSWORD="${DB_PASSWORD-}"
TEST_DB="${TEST_DB_NAME:-rto_management_test}"
PORT="${PARITY_PORT:-8081}"
SECRET="test-only-secret-$(printf 'x%.0s' {1..40})"   # the reference suite signs tokens with this secret

echo "== 1/4 build"
"$HERE/mvn.sh" -q -DskipTests package
JAR="$(ls "$HERE"/target/rto-management-backend-*.jar | head -1)"
JAVA="$(ls -d "$HERE"/.tools/jdk-* | head -1)/bin/java"

echo "== 2/4 fresh test database from the unmodified database/*.sql"
( cd "$PY_BACKEND" && TEST_DB_NAME="$TEST_DB" USE_TEST_DB=true "$PY" -m scripts.apply_schema --test \
  && USE_TEST_DB=true "$PY" - <<'EOF'
from app.core.database import SessionFactory
from app.seed import seed_payable_types, seed_rbac
with SessionFactory() as db:
    seed_rbac(db); seed_payable_types(db)
EOF
)

echo "== 3/4 start Java server on :$PORT"
LOG="$HERE/target/parity-server.log"
DB_NAME="$TEST_DB" JWT_SECRET="$SECRET" PORT="$PORT" STORAGE_DIR="$HERE/target/parity-storage" \
  "$JAVA" -jar "$JAR" > "$LOG" 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null || true' EXIT
for i in $(seq 1 60); do
  curl -sf "http://127.0.0.1:$PORT/api/v1/health" >/dev/null 2>&1 && break
  kill -0 $SERVER 2>/dev/null || { echo "server died:"; tail -30 "$LOG"; exit 1; }
  sleep 1
done
curl -sf "http://127.0.0.1:$PORT/api/v1/health" || { echo "server did not become healthy"; tail -30 "$LOG"; exit 1; }
echo

echo "== 4/4 reference suite over HTTP"
cd "$PY_BACKEND"
RTO_BASE_URL="http://127.0.0.1:$PORT" RTO_SKIP_DB_REBUILD=1 TEST_DB_NAME="$TEST_DB" "$PY" -m pytest -q --tb=short -p no:cacheprovider "$@" || RC=$?
exit ${RC:-0}
