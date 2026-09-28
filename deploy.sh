#!/usr/bin/env bash
# One-command deploy of IELTS Prep (backend + frontend) with Docker.
#
#   Fresh machine:
#     git clone https://github.com/Firdovsirz/ielts-prep-backend.git ielts-prep && ielts-prep/deploy.sh
#
#   In this folder:
#     ./deploy.sh                build and (re)start everything → http://localhost:3000
#     ./deploy.sh --pull         update both repositories first, then rebuild
#     ./deploy.sh --api-key      add or replace the Claude (Anthropic) API key, then restart
#     ./deploy.sh --reset-admin  set a new login e-mail and password (e.g. when the login is lost)
#     ./deploy.sh --down         stop the app (the data volume is kept)
#
# The frontend lives in its own repository and is cloned into ./frontend on first run. On first run .env is created
# from .env.example; set ADMIN_EMAIL / ADMIN_PASSWORD / ANTHROPIC_API_KEY in the environment to skip the questions.
# Ports: FRONTEND_PORT / BACKEND_PORT in .env; if one is taken by another service the next free port is used.
# The API key can also be entered in the app: Settings → Claude API.
set -euo pipefail

# Everything runs inside main() so the whole script is read before it starts: `--pull` may replace this file.
main() {
  cd "$(dirname "$0")"
  local frontend_repo="${FRONTEND_REPO:-https://github.com/Firdovsirz/ielts-prep-frontend.git}"
  local pull=false down=false set_key=false reset_admin=false
  for arg in "$@"; do
    case "$arg" in
      --pull) pull=true ;;
      --down) down=true ;;
      --api-key) set_key=true ;;
      --reset-admin) reset_admin=true ;;
      -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
      *) fail "Unknown option: $arg (see --help)" ;;
    esac
  done

  # --- Docker ------------------------------------------------------------------------------------------------------
  command -v docker >/dev/null || fail "Docker is not installed (https://docs.docker.com/get-docker/)."
  docker info >/dev/null 2>&1 || fail "Docker is not running."
  if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else
    command -v docker-compose >/dev/null || fail "Docker Compose is not installed."
    COMPOSE=(docker-compose)
  fi

  if $down; then "${COMPOSE[@]}" down; exit 0; fi
  if $reset_admin; then reset_admin_login; exit 0; fi

  # --- Sources -----------------------------------------------------------------------------------------------------
  if $pull; then
    if [ -d .git ]; then
      say "Updating ielts-prep-backend"
      git pull --ff-only
    fi
    if [ -d frontend/.git ]; then
      say "Updating ielts-prep-frontend"
      git -C frontend pull --ff-only
    fi
    # Continue with the freshly pulled version of this script.
    local rest=()
    for arg in "$@"; do [ "$arg" = "--pull" ] || rest+=("$arg"); done
    exec "./deploy.sh" ${rest[@]+"${rest[@]}"}
  fi
  if [ ! -f frontend/package.json ]; then
    say "Cloning the frontend into ./frontend"
    git clone --depth 1 "$frontend_repo" frontend
  fi

  # --- .env --------------------------------------------------------------------------------------------------------
  if [ ! -f .env ]; then
    say "Creating .env from .env.example"
    cp .env.example .env
    local email="${ADMIN_EMAIL:-}" password="${ADMIN_PASSWORD:-}" key="${ANTHROPIC_API_KEY:-}"
    if [ -z "$email" ] || [ -z "$password" ]; then
      [ -t 0 ] || fail "Set ADMIN_EMAIL and ADMIN_PASSWORD to create the login (no terminal to ask)."
      [ -n "$email" ] || read -r -p "  Admin e-mail: " email
      [ -n "$password" ] || ask_password password
    fi
    password_problem "$password" && fail "$(password_problem "$password")"
    if [ -z "$key" ] && [ -t 0 ]; then
      read -r -s -p "  Claude API key (sk-ant-…, Enter to skip — you can add it later): " key; echo
    fi
    env_set ADMIN_EMAIL "$email"
    env_set ADMIN_PASSWORD "$password"
    env_set JWT_SECRET "$( (openssl rand -base64 48 2>/dev/null || head -c 48 /dev/urandom | base64) | tr -d '\n')"
    [ -n "$key" ] && env_set ANTHROPIC_API_KEY "$(printf '%s' "$key" | tr -d '[:space:]')"
    [ -n "${TZ:-}" ] && env_set TZ "$TZ"
  elif $set_key; then
    local key="${ANTHROPIC_API_KEY:-}"
    if [ -z "$key" ]; then
      [ -t 0 ] || fail "Set ANTHROPIC_API_KEY to change the key (no terminal to ask)."
      read -r -s -p "  Claude API key (sk-ant-…): " key; echo
    fi
    key="$(printf '%s' "$key" | tr -d '[:space:]')"
    case "$key" in sk-ant-*) ;; *) fail "That does not look like an Anthropic API key (it starts with sk-ant-)." ;; esac
    env_set ANTHROPIC_API_KEY "$key"
    say "API key saved in .env (ending …${key: -4})"
  fi
  [ "$(env_get ADMIN_PASSWORD)" = "change-me-please" ] && fail "Change ADMIN_PASSWORD in .env before deploying."
  if [ -z "$(env_get ANTHROPIC_API_KEY)" ]; then
    say "No Claude API key yet — add it in the app (Settings → Claude API) or run ./deploy.sh --api-key"
  fi

  # --- Ports -------------------------------------------------------------------------------------------------------
  choose_port FRONTEND_PORT 3000
  choose_port BACKEND_PORT 8090

  # --- Build and start ---------------------------------------------------------------------------------------------
  say "Building and starting (the first build takes a few minutes)"
  "${COMPOSE[@]}" up -d --build

  local port="$FRONTEND_PORT" bind
  bind="${FRONTEND_BIND:-$(env_get FRONTEND_BIND)}"
  say "Waiting for the app on http://localhost:$port (the backend needs up to a minute to start)"
  for _ in $(seq 1 120); do
    if curl -fsS "http://localhost:$port/api/system/health" 2>/dev/null | grep -q UP; then
      printf '\n\033[1;32m✓ IELTS Prep is running: http://localhost:%s\033[0m  (API on 127.0.0.1:%s, sign in as %s)\n' \
        "$port" "$BACKEND_PORT" "$(env_get ADMIN_EMAIL)"
      if [ "$bind" = "127.0.0.1" ]; then
        echo "  Listening on 127.0.0.1 only — point your HTTPS reverse proxy at 127.0.0.1:$port (web) and 127.0.0.1:$BACKEND_PORT (API)."
      else
        echo "  On a server, put it behind HTTPS (microphone recording needs it) — see README: Deploying on a server."
      fi
      exit 0
    fi
    sleep 2
  done
  "${COMPOSE[@]}" ps
  fail "The app did not become healthy in 4 minutes — check: ${COMPOSE[*]} logs backend"
}

say() { printf '\033[1;31m▸\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

env_get() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- || true; }
env_set() { # env_set KEY VALUE — replaces or appends, safe for any characters in VALUE
  K="$1" V="$2" awk 'BEGIN { k = ENVIRON["K"]; v = ENVIRON["V"] }
    index($0, k "=") == 1 { print k "=" v; done = 1; next } { print }
    END { if (!done) print k "=" v }' .env > .env.tmp && mv .env.tmp .env
}

# Prints why a password is unusable (and succeeds), or nothing (and fails) when it is fine.
password_problem() {
  if [ ${#1} -lt 8 ]; then echo "The password must be at least 8 characters."; return 0; fi
  case "$1" in *'$'*) echo "Please avoid the \$ character in the password (Docker treats it as a variable)."; return 0 ;; esac
  return 1
}
ask_password() { # ask_password VAR — hidden input, asked twice
  local p1 p2 problem
  while true; do
    read -r -s -p "  Password (8+ characters): " p1; echo
    if problem="$(password_problem "$p1")"; then echo "  $problem"; continue; fi
    read -r -s -p "  Repeat the password: " p2; echo
    [ "$p1" = "$p2" ] && break
    echo "  The passwords do not match — try again."
  done
  printf -v "$1" '%s' "$p1"
}

# Sets a new login e-mail/password in the running app (no old password needed) and records it in .env.
reset_admin_login() {
  [ -f .env ] || fail "No .env here — run ./deploy.sh first."
  "${COMPOSE[@]}" ps --status running --services 2>/dev/null | grep -qx backend \
    || fail "The backend is not running — start it with ./deploy.sh first."
  local email="${ADMIN_EMAIL:-}" password="${ADMIN_PASSWORD:-}" current out
  current="$(env_get ADMIN_EMAIL)"
  if [ -z "$email" ] || [ -z "$password" ]; then
    [ -t 0 ] || fail "Set ADMIN_EMAIL and ADMIN_PASSWORD (no terminal to ask)."
    if [ -z "$email" ]; then
      read -r -p "  New login e-mail [${current}]: " email
      email="${email:-$current}"
    fi
    [ -n "$password" ] || ask_password password
  fi
  password_problem "$password" && fail "$(password_problem "$password")"
  say "Updating the login (about 30 seconds)"
  # The credentials go in on standard input, never on a command line.
  if ! out="$(printf '%s\n%s\n' "$email" "$password" \
      | "${COMPOSE[@]}" exec -T backend java -jar /app/app.jar --task=reset-admin 2>&1)"; then
    printf '%s\n' "$out" | grep -E "Task failed|Unknown task|ERROR" | tail -5 >&2
    fail "Could not update the login (see above). Is the backend up to date? Run ./deploy.sh first."
  fi
  printf '%s\n' "$out" | grep -q "Admin login updated" \
    || { printf '%s\n' "$out" | tail -5 >&2; fail "Could not update the login."; }
  env_set ADMIN_EMAIL "$email"
  env_set ADMIN_PASSWORD "$password"
  printf '\033[1;32m✓ Login updated — sign in as %s\033[0m\n' "$email"
}

# A port is taken when something listens on it or another container publishes it (Docker may not run a listening
# proxy process). Ports published by this app's own running containers are fine: that is a redeploy.
port_busy() {
  if docker ps --format '{{.Label "com.docker.compose.project"}}|{{.Ports}}' 2>/dev/null \
      | grep -v '^ielts-prep|' | grep -qE "[:.]$1->"; then
    return 0
  fi
  ours "$1" && return 1
  if command -v ss >/dev/null 2>&1; then
    ss -ltn 2>/dev/null | awk 'NR > 1 { print $4 }' | grep -qE "[:.]$1\$"
  elif command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
  fi
}
ours() {
  docker ps --filter "label=com.docker.compose.project=ielts-prep" --format '{{.Ports}}' 2>/dev/null | grep -qE "[:.]$1->"
}
choose_port() { # choose_port VAR DEFAULT — keeps the configured port if free, otherwise the next free one
  local var="$1" port wanted
  port="${!var:-$(env_get "$var")}"
  port="${port:-$2}"
  wanted="$port"
  while port_busy "$port"; do port=$((port + 1)); done
  if [ "$port" != "$wanted" ]; then
    say "Port $wanted is already in use — using $port for $var (saved in .env)"
  fi
  env_set "$var" "$port"
  export "$var=$port"
}

main "$@"
