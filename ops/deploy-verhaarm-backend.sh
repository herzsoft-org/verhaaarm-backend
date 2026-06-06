#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE_NAME="verhaarm"
APP_JAR="/opt/verhaarm/app.jar"
BACKUP_DIR="/opt/verhaarm/backups"
DB_NAME="verhaaarm"
LOCAL_HEALTH_URL="http://127.0.0.1:18080/actuator/health"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
APP_BACKUP="$BACKUP_DIR/app.jar.$TS"
DB_BACKUP="$BACKUP_DIR/verhaaarm.$TS.dump"
BACKUPS_READY=0

usage() {
  echo "Usage: $0 /path/to/uploaded.jar"
}

wait_for_health() {
  local url="$1"
  local attempts="${2:-20}"
  local delay="${3:-3}"
  local body=""

  for i in $(seq 1 "$attempts"); do
    body="$(curl -fsS --max-time 5 "$url" || true)"
    if command -v python3 >/dev/null 2>&1; then
      if printf '%s' "$body" | python3 -c 'import json,sys; data=json.load(sys.stdin); sys.exit(0 if data.get("status")=="UP" else 1)' 2>/dev/null; then
        echo "Health check OK: $url"
        return 0
      fi
    elif printf '%s' "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      echo "Health check OK: $url"
      return 0
    fi

    echo "Health check not UP yet ($i/$attempts): $url"
    sleep "$delay"
  done

  echo "Health check failed: $url"
  return 1
}

print_diagnostics() {
  echo "== systemd status =="
  systemctl --no-pager -l status "$SERVICE_NAME" || true
  echo "== recent journal =="
  journalctl -u "$SERVICE_NAME" -n 200 --no-pager || true
}

rollback() {
  local exit_code=$?
  local line_no="${1:-unknown}"
  local failed_command="${2:-unknown}"

  trap - ERR
  set +e

  echo "Deployment failed at line $line_no: $failed_command"
  print_diagnostics

  if [ "$BACKUPS_READY" -ne 1 ]; then
    echo "Backups were not fully created; rollback skipped."
    exit "$exit_code"
  fi

  echo "Starting rollback."
  systemctl stop "$SERVICE_NAME" || true

  echo "Restoring previous app JAR: $APP_BACKUP -> $APP_JAR"
  cp -a "$APP_BACKUP" "$APP_JAR"
  chown root:root "$APP_JAR"
  chmod 755 "$APP_JAR"

  echo "Terminating active PostgreSQL connections for database: $DB_NAME"
  sudo -n -u postgres psql -d postgres -c \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" || true

  echo "Restoring PostgreSQL database from: $DB_BACKUP"
  sudo -n -u postgres pg_restore \
    --clean \
    --if-exists \
    --single-transaction \
    --exit-on-error \
    --dbname "$DB_NAME" \
    "$DB_BACKUP"

  echo "Starting service after rollback."
  systemctl start "$SERVICE_NAME"
  wait_for_health "$LOCAL_HEALTH_URL" 20 3
  print_diagnostics

  echo "Rollback completed; failing deployment intentionally."
  exit "$exit_code"
}

prune_backups() {
  echo "Pruning old backups; keeping newest 3 app backups and newest 3 database backups."
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'app.jar.*' -printf '%f\n' \
    | sort -r \
    | tail -n +4 \
    | while IFS= read -r backup_name; do
        rm -f -- "$BACKUP_DIR/$backup_name"
      done

  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'verhaaarm.*.dump' -printf '%f\n' \
    | sort -r \
    | tail -n +4 \
    | while IFS= read -r backup_name; do
        rm -f -- "$BACKUP_DIR/$backup_name"
      done
}

if [ "$(id -u)" -ne 0 ]; then
  echo "This script must be run as root."
  exit 1
fi

if [ "$#" -ne 1 ]; then
  usage
  exit 2
fi

UPLOADED_JAR="$1"

if [ ! -f "$UPLOADED_JAR" ]; then
  echo "Uploaded JAR does not exist or is not a file: $UPLOADED_JAR"
  exit 1
fi

if [ ! -s "$UPLOADED_JAR" ]; then
  echo "Uploaded JAR is empty: $UPLOADED_JAR"
  exit 1
fi

if [ ! -f "$APP_JAR" ]; then
  echo "Current app JAR does not exist: $APP_JAR"
  exit 1
fi

trap 'rollback "$LINENO" "$BASH_COMMAND"' ERR

echo "Creating backup directory: $BACKUP_DIR"
install -d -o root -g postgres -m 0770 "$BACKUP_DIR"

echo "Backing up current app JAR to: $APP_BACKUP"
cp -a "$APP_JAR" "$APP_BACKUP"

echo "Backing up PostgreSQL database '$DB_NAME' to: $DB_BACKUP"
sudo -n -u postgres pg_dump \
  --format=custom \
  --file "$DB_BACKUP" \
  "$DB_NAME"

if [ ! -s "$APP_BACKUP" ]; then
  echo "App JAR backup is missing or empty: $APP_BACKUP"
  exit 1
fi

if [ ! -s "$DB_BACKUP" ]; then
  echo "Database backup is missing or empty: $DB_BACKUP"
  exit 1
fi

BACKUPS_READY=1

echo "Stopping service: $SERVICE_NAME"
systemctl stop "$SERVICE_NAME"

echo "Installing uploaded JAR: $UPLOADED_JAR -> $APP_JAR"
mv "$UPLOADED_JAR" "$APP_JAR"
chown root:root "$APP_JAR"
chmod 755 "$APP_JAR"

echo "Starting service: $SERVICE_NAME"
systemctl start "$SERVICE_NAME"
wait_for_health "$LOCAL_HEALTH_URL" 20 3
print_diagnostics
prune_backups

echo "Deployment completed successfully."
