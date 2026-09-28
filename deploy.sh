#!/usr/bin/env bash
# One-command deploy of IELTS Prep (backend + frontend) with Docker.
#
#   Fresh machine:
#     git clone https://github.com/Firdovsirz/ielts-prep-backend.git ielts-prep && ielts-prep/deploy.sh
#
#   In this folder:
#     ./deploy.sh            build and (re)start everything → http://localhost:3000
#     ./deploy.sh --pull     update both repositories first, then rebuild
#     ./deploy.sh --down     stop the app (the data volume is kept)
#
# The frontend lives in its own repository and is cloned into ./frontend on first run. On first run .env is created
# from .env.example; set ADMIN_EMAIL / ADMIN_PASSWORD (and optionally ANTHROPIC_API_KEY) in the environment to skip
# the prompts, e.g.  ADMIN_EMAIL=me@example.com ADMIN_PASSWORD='…' ./deploy.sh
set -euo pipefail

cd "$(dirname "$0")"
FRONTEND_REPO="${FRONTEND_REPO:-https://github.com/Firdovsirz/ielts-prep-frontend.git}"
PULL=false
DOWN=false
for arg in "$@"; do
  case "$arg" in
    --pull) PULL=true ;;
    --down) DOWN=true ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg (see --help)"; exit 2 ;;
  esac
done

say() { printf '\033[1;31m▸\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# --- Docker ----------------------------------------------------------------------------------------------------------
command -v docker >/dev/null || fail "Docker is not installed (https://docs.docker.com/get-docker/)."
docker info >/dev/null 2>&1 || fail "Docker is not running."
if docker compose version >/dev/null 2>&1; then COMPOSE=(docker compose); else
  command -v docker-compose >/dev/null || fail "Docker Compose is not installed."
  COMPOSE=(docker-compose)
fi

if $DOWN; then "${COMPOSE[@]}" down; exit 0; fi

# --- Sources ---------------------------------------------------------------------------------------------------------
if $PULL && [ -d .git ]; then
  say "Updating ielts-prep-backend"
  git pull --ff-only
fi
if [ ! -f frontend/package.json ]; then
  say "Cloning the frontend into ./frontend"
  git clone --depth 1 "$FRONTEND_REPO" frontend
elif $PULL && [ -d frontend/.git ]; then
  say "Updating ielts-prep-frontend"
  git -C frontend pull --ff-only
fi

# --- .env ------------------------------------------------------------------------------------------------------------
env_get() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- || true; }
env_set() { # env_set KEY VALUE — replaces or appends, safe for any characters in VALUE
  K="$1" V="$2" awk 'BEGIN { k = ENVIRON["K"]; v = ENVIRON["V"] }
    index($0, k "=") == 1 { print k "=" v; done = 1; next } { print }
    END { if (!done) print k "=" v }' .env > .env.tmp && mv .env.tmp .env
}

if [ ! -f .env ]; then
  say "Creating .env from .env.example"
  cp .env.example .env
  email="${ADMIN_EMAIL:-}"
  password="${ADMIN_PASSWORD:-}"
  if [ -z "$email" ] || [ -z "$password" ]; then
    [ -t 0 ] || fail "Set ADMIN_EMAIL and ADMIN_PASSWORD to create the login (no terminal to ask)."
    [ -n "$email" ] || read -r -p "  Admin e-mail: " email
    if [ -z "$password" ]; then read -r -s -p "  Admin password (8+ characters): " password; echo; fi
  fi
  [ ${#password} -ge 8 ] || fail "The admin password must be at least 8 characters."
  env_set ADMIN_EMAIL "$email"
  env_set ADMIN_PASSWORD "$password"
  secret=$(openssl rand -base64 48 2>/dev/null || head -c 48 /dev/urandom | base64)
  env_set JWT_SECRET "$(printf '%s' "$secret" | tr -d '\n')"
  [ -n "${ANTHROPIC_API_KEY:-}" ] && env_set ANTHROPIC_API_KEY "$ANTHROPIC_API_KEY"
  [ -n "${TZ:-}" ] && env_set TZ "$TZ"
fi
[ "$(env_get ADMIN_PASSWORD)" = "change-me-please" ] && fail "Change ADMIN_PASSWORD in .env before deploying."
[ -z "$(env_get ANTHROPIC_API_KEY)" ] && say "No ANTHROPIC_API_KEY in .env — the app runs with seed content; add the key later and re-run."

# --- Build and start -------------------------------------------------------------------------------------------------
say "Building and starting (the first build takes a few minutes)"
"${COMPOSE[@]}" up -d --build

port="${FRONTEND_PORT:-$(env_get FRONTEND_PORT)}"
port="${port:-3000}"
say "Waiting for the app on http://localhost:$port"
for _ in $(seq 1 90); do
  if curl -fsS "http://localhost:$port/api/system/health" 2>/dev/null | grep -q UP; then
    printf '\n\033[1;32m✓ IELTS Prep is running: http://localhost:%s\033[0m  (sign in as %s)\n' "$port" "$(env_get ADMIN_EMAIL)"
    exit 0
  fi
  sleep 2
done
"${COMPOSE[@]}" ps
fail "The app did not become healthy in 3 minutes — check: ${COMPOSE[*]} logs backend"
