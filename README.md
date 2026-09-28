# IELTS Prep

A personal, AI-powered IELTS preparation system: realistic practice for all four papers, examiner-style grading of
Writing and Speaking with Claude, a grammar coach driven by your own mistakes, spaced-repetition vocabulary, a study
plan that counts down to your test date, a weekly coach report and full mock tests.

Spring Boot 3 (Java 21) · H2 · Flyway · React 19 + TypeScript + Vite · TanStack Query · Recharts · Claude API.

> Independent study tool. Not affiliated with or endorsed by IELTS, the British Council, IDP or Cambridge University
> Press & Assessment. Band scores are estimates.

## Contents

- [What it does](#what-it-does)
- [Repositories](#repositories)
- [Quick start with Docker](#quick-start-with-docker)
- [Deploying on a server](#deploying-on-a-server)
- [Local development](#local-development)
- [Configuration](#configuration)
- [Working without an API key](#working-without-an-api-key)
- [Content: seed, generation and sources](#content-seed-generation-and-sources)
- [Models and cost](#models-and-cost)
- [Resetting progress, export and backup](#resetting-progress-export-and-backup)
- [Project layout](#project-layout)
- [Tests](#tests)
- [Design notes](#design-notes)
- [Troubleshooting](#troubleshooting)

## What it does

| Module | Highlights |
|---|---|
| **Listening** | Four-part tests or single parts with 10 questions each. Scripts are spoken by the browser's speech synthesis with one voice per speaker (accent and gender matched). Exam mode plays once, with reading time and checking time. The results page shows the transcript, with the line that answers each question. |
| **Reading** | Academic passages with all 14 question types (headings, TFNG/YNNG, matching, completion, diagrams, choose-two …), exam and practice timing, and answer evidence highlighted in the passage after marking. |
| **Writing** | Academic Task 1 with real rendered charts, tables, processes and maps; General Training letters; Task 2 essays. Graded by Claude on the four public criteria with quoted evidence, tagged errors highlighted in your text, three concrete improvements, vocabulary upgrades and a model answer. |
| **Speaking** | Parts 1–3 with an AI examiner (conversation mode) or a scripted examiner, recorded in the browser and transcribed (Web Speech API, or Whisper). Graded on fluency, lexis and grammar; pronunciation cannot be judged from a transcript and is not scored. |
| **Grammar** | A 40-question adaptive diagnostic across 13 areas, lessons and exercise sets, and drills generated from the sentences you actually got wrong. |
| **Vocabulary** | SM-2 flashcards with interval previews. Words come from what you flag in Reading and Listening, from your Writing and Speaking feedback, and from 13 topic word banks. Academic Word List sublists are tagged. |
| **Dashboard** | Band history per module with rolling averages, criterion trends, accuracy by question type, timing, blanks and an activity heatmap. |
| **Study plan** | A rolling seven-day plan weighted towards your weakest skills. It moves through phases as the test date approaches (Build → Sharpen → Exam week), schedules mock tests, and ticks tasks off automatically when you complete them. |
| **Coach** | A weekly report every Monday at 07:00: what went well, what to watch, a band outlook and three priorities. It also re-plans the week. |
| **Mock test** | All four papers back to back in exam mode with real timings, then one band report. |

Every attempt is stored (sessions, answers, grades, errors), and you can export everything to JSON or CSV.

## Repositories

The app is split across two repositories. Each has its own Docker Compose file:

| Repository | Contents | Docker Compose |
|---|---|---|
| [ielts-prep-backend](https://github.com/Firdovsirz/ielts-prep-backend) (this one) | Spring Boot API (`backend/`), prompts, seed data, docs, `deploy.sh` | `backend/docker-compose.yml` — the API alone · `docker-compose.yml` — the whole app |
| [ielts-prep-frontend](https://github.com/Firdovsirz/ielts-prep-frontend) | React app, nginx image | `docker-compose.yml` — the web app alone, proxying `/api` to `BACKEND_URL` |

For the whole app the frontend is cloned into `./frontend` inside this repository (`deploy.sh` and `make` do it
automatically; the folder is gitignored here).

## Quick start with Docker

Requirements: Docker with Compose (`docker compose` or the standalone `docker-compose`) and git.

**One command, on a fresh machine:**

```bash
git clone https://github.com/Firdovsirz/ielts-prep-backend.git ielts-prep && ielts-prep/deploy.sh
```

It clones the frontend, creates `.env` (asking for the admin e-mail and password, and generating the token secret),
builds both images and starts them. Open **http://localhost:3000** when it prints the URL. To skip the questions, pass
the values in: `ADMIN_EMAIL=me@example.com ADMIN_PASSWORD='…' ANTHROPIC_API_KEY=sk-ant-… ielts-prep/deploy.sh`.

| Command (in the `ielts-prep` folder) | What it does |
|---|---|
| `./deploy.sh` (or `make deploy`) | Build and (re)start everything — also applies `.env` changes such as a new API key |
| `./deploy.sh --pull` | Update both repositories, then rebuild and restart |
| `./deploy.sh --api-key` | Add or replace the Claude API key |
| `./deploy.sh --reset-admin` | Set a new login e-mail and password (no old password needed) |
| `./deploy.sh --down` | Stop the app (data is kept) |
| `docker compose logs -f backend` | Follow the backend logs |
| `docker compose down -v` | Stop and delete all data |

- The backend image bakes in the prompts and the default data (templates, descriptors, seed content). On first start
  these are copied into the `ielts-prep-data` volume, the database is created and the 174 verified seed items are
  loaded.
- The API and Swagger UI are also available on `http://localhost:8090` (bound to localhost only).

**Each part on its own** (for example the API on one server and the web app on another):

```bash
# ielts-prep-backend
cp .env.example .env                      # set ADMIN_EMAIL, ADMIN_PASSWORD, ANTHROPIC_API_KEY
cd backend && docker compose up -d --build     # API on :8090 (reads ../.env and backend/.env)

# ielts-prep-frontend
cp .env.example .env                      # BACKEND_URL=http://<api-host>:8090
docker compose up -d --build              # nginx on :3000, proxies /api to BACKEND_URL
```

> Microphone recording and speech recognition only work on `https://` or `http://localhost`. To use the app from
> another device, put it behind a TLS reverse proxy (Caddy, Traefik, nginx).

## Deploying on a server

`deploy.sh` works the same on a Linux server. Ports come from `.env` (`FRONTEND_PORT`, `BACKEND_PORT`). If one is
already used by another service or container, the script takes the next free port, saves it and prints it.

The browser allows microphone recording and speech recognition only over **HTTPS** (or on `localhost`), so put the
app behind nginx with TLS. Ready-made configs for two domains are in the repositories. Replace the domain names if you
use others:

| Domain | Config file | Proxies to |
|---|---|---|
| `ielts.firdovsirzaev.online` — the app | `frontend/deploy/nginx/ielts.firdovsirzaev.online.conf` | the web container, `127.0.0.1:3300`; it forwards `/api` to the backend itself |
| `api-ielts.firdovsirzaev.online` — the API | `deploy/nginx/api-ielts.firdovsirzaev.online.conf` | the backend, `127.0.0.1:8300` (REST API, Swagger UI, OpenAPI spec) |

Both configs set security headers, allow 60 MB uploads (Speaking recordings) and 300 s timeouts (grading), and limit
login attempts to 10 per minute per IP. The app talks to its own domain (`/api`), so the browser needs no CORS; the API
domain is for Swagger UI and direct API use.

```bash
cd ~/ielts-prep
# 1. Pin the ports used in the nginx configs and keep the app off the public interface
sed -i -e 's/^FRONTEND_PORT=.*/FRONTEND_PORT=3300/' -e 's/^BACKEND_PORT=.*/BACKEND_PORT=8300/' .env
grep -q '^FRONTEND_BIND=' .env && sed -i 's/^FRONTEND_BIND=.*/FRONTEND_BIND=127.0.0.1/' .env || echo 'FRONTEND_BIND=127.0.0.1' >> .env
./deploy.sh

# 2. nginx sites (both DNS A records must point at this server)
cp deploy/nginx/api-ielts.firdovsirzaev.online.conf frontend/deploy/nginx/ielts.firdovsirzaev.online.conf /etc/nginx/sites-available/
ln -sf /etc/nginx/sites-available/ielts.firdovsirzaev.online.conf /etc/nginx/sites-enabled/
ln -sf /etc/nginx/sites-available/api-ielts.firdovsirzaev.online.conf /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx

# 3. HTTPS certificates (certbot rewrites both sites to HTTPS and redirects HTTP)
certbot --nginx -d ielts.firdovsirzaev.online -d api-ielts.firdovsirzaev.online
```

If `deploy.sh` reports that it had to use different ports, change `127.0.0.1:3300` / `127.0.0.1:8300` in the two
config files to match. With Caddy instead of nginx, the whole setup is
`ielts.example.com { reverse_proxy 127.0.0.1:3300 }`.

## Local development

Requirements: **JDK 21**, **Node.js 22 LTS** or newer, git, and `make` (optional, for shortcuts). No Maven installation
is needed, because the Maven wrapper (`./mvnw`) downloads it.

```bash
git clone https://github.com/Firdovsirz/ielts-prep-backend.git ielts-prep && cd ielts-prep
cp .env.example .env         # set ADMIN_EMAIL and ADMIN_PASSWORD
make install                 # clones the frontend into ./frontend and installs its packages
make dev                     # backend on :8090 + Vite on :5173 → http://localhost:5173
```

Without `make`:

```bash
cd backend && ./mvnw spring-boot:run      # terminal 1 — reads ../.env
cd frontend && npm ci && npm run dev      # terminal 2 — proxies /api to http://localhost:8090
```

The backend reads the repo-root `.env` (and `backend/.env`, which overrides it). Real environment variables override
both. Swagger UI is at http://localhost:8090/swagger-ui.html.

Useful targets (`make help` lists them all):

| Command | What it does |
|---|---|
| `make dev` / `make backend` / `make frontend` | Run both, or one side |
| `make test` | All backend and frontend tests, type-checking and lint |
| `make gen-api` | Export `docs/api/openapi.json` and regenerate `frontend/src/api/schema.d.ts` |
| `make generate MODULE=reading COUNT=10` | Generate and verify new content (Batches API) |
| `make status` | Content inventory and buffer levels |
| `make deploy` / `make docker-down` | Docker stack |
| `make reset CONFIRM=yes` | Delete the local database (see below) |

## Configuration

All secrets and per-install settings live in `.env` (gitignored). `.env.example` documents every key.

| Variable | Default | Meaning |
|---|---|---|
| `ANTHROPIC_API_KEY` | — | Your Claude API key. Leave empty to run offline; it can also be entered in the app (Settings → Claude API), which overrides this value. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | — | The login created on the **first** start (stored only as a BCrypt hash). Editing them later does not change the account: use Settings → Account, or `./deploy.sh --reset-admin` if the login is lost. Avoid `$` in the password (Docker treats it as a variable). |
| `JWT_SECRET` | generated | Signs login tokens. If blank, one is generated and kept in `data/.jwt-secret`. Changing it signs everyone out. |
| `JWT_TTL_HOURS` | `720` | How long a login lasts. |
| `CLAUDE_DAILY_SPEND_CAP_USD` | `3.00` | Hard daily limit on API spend. |
| `CLAUDE_MODEL_GENERATION` … `CLAUDE_MODEL_FAST` | `claude-sonnet-5` / `claude-haiku-4-5` | Model per route (see [Models and cost](#models-and-cost)). |
| `TZ` | `UTC` | Local timezone. The spend cap, study-plan days and the Monday coach run use it. |
| `SPEAKING_TRANSCRIPTION` | `BROWSER` | `BROWSER` (Web Speech API, free) or `WHISPER` (server-side, better accuracy). |
| `WHISPER_URL`, `WHISPER_API_KEY` | — | OpenAI-compatible `/audio/transcriptions` endpoint when using `WHISPER`. |
| `FRONTEND_PORT`, `BACKEND_PORT` | `3000`, `8090` | Host ports for Docker. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Only needed when the frontend is served from a different origin than `/api`. |

Advanced settings (content-buffer size, coach schedule, prices, effort, token limits) are in
`backend/src/main/resources/application.yml`. `backend/application-example.yml` is a commented copy. In-app
preferences (test date, bands, daily study time, listening timings, error-log threshold) are under **Settings**.

## Working without an API key

The app is fully usable offline with its seed content:

- **Works:** Reading and Listening (auto-marked with IELTS band conversion), the grammar diagnostic, lessons and
  exercises (open answers are self-assessed), vocabulary flashcards and topic banks, the dashboard, the rule-based study
  plan and coach report, the Speaking test with the scripted examiner, and mock tests (Listening and Reading bands).
- **Needs the key:** Writing and Speaking grading, the conversational AI examiner, generating new passages, sections,
  tasks and drills, vocabulary enrichment, and AI-personalised plans and coach reports.

**Add the key** (any one of these):

- **In the app:** Settings → Claude API → paste the key → **Save key**. The key is checked with Anthropic, takes
  effect immediately (no restart), is stored only on your server (`data/.anthropic-api-key`, owner-only permissions)
  and overrides the one in `.env`.
- **With the deploy script:** `./deploy.sh --api-key` asks for the key, saves it in `.env` and restarts the app.
- **By hand:** set `ANTHROPIC_API_KEY=` in `.env`, then `./deploy.sh` (Docker) or restart the backend.

Anything that failed to grade can then be re-graded from its result page (**Grade now**). Settings → Claude API shows
where the key comes from and today's spend.

## Content: seed, generation and sources

**Seed content.** `data/seed/` holds 174 hand-checked items: 9 reading passages, 12 listening sections, 14 writing
prompts, 9 speaking parts, 13 grammar lessons, 26 exercise sets, a 78-question diagnostic bank and 13 vocabulary banks.
They load automatically on start-up (existing items are never duplicated). `SeedContentTest` validates every file
against its schema and self-marks every question with its key.

**Generating more.** Every generated item goes through two passes: Claude writes it, deterministic checks run, then a
separate call answers every question blind (or reviews the item) and anything ambiguous is rewritten, up to three
attempts. Only verified items are ever served. A background buffer keeps at least five unseen items per bucket once a
key is set. To generate in bulk (Message Batches API, half price):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=generate --module=reading --count=10"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=generate --module=listening --count=8 --variant=S4"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=generate --module=writing --count=6 --sync"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--task=status"
```

Modules: `reading`, `listening`, `writing`, `writing-general`, `speaking`, `grammar`, `vocab`. Options: `--count=N`,
`--variant=…` (e.g. `P3` for Passage 3, `S4` for Listening Part 4, a topic for `vocab`), `--sync` (one at a time, no
batch), `--no-wait` (submit and let the running backend finish), `--task-type=…`. Other tasks: `--task=buffer`,
`--task=seed`, `--task=fetch-templates`, `--task=fetch-sources`. The `make` targets wrap these commands.

Tasks can run while the app is running, because H2 runs in auto-server mode. In Docker:
`docker compose exec backend java -jar /app/app.jar --task=status`.

**Copyright-safe sourcing.**
- All passages, scripts and prompts are original. Licensed or public-domain sources (listed with their licences in
  `data/sources/seeds.json`) supply facts only, never copied text.
- Official IELTS materials are used only for **format**: `--task=fetch-templates` downloads the free official format
  and sample pages, respecting `robots.txt` with per-host delays, and extracts structure (timings, question types,
  word limits) into gitignored folders.
- The band descriptors in `data/descriptors/` are paraphrased.

Every prompt and schema is documented in [`docs/prompts.md`](docs/prompts.md).

## Models and cost

| Route | Default | Used for |
|---|---|---|
| Generation, Verification | `claude-sonnet-5` | Writing and blind-checking content |
| Grading | `claude-sonnet-5` (effort high) | Writing and Speaking grading |
| Coaching | `claude-sonnet-5` | Weekly coach report, plan personalisation |
| Examiner | `claude-sonnet-5` (effort low) | Live Speaking examiner |
| Fast | `claude-haiku-4-5` | Vocabulary enrichment, grammar answer checks |

**Change a model:** set `CLAUDE_MODEL_<ROUTE>` in `.env` and restart. If you switch to a model without a price in
`claude.pricing` (`application.yml`), add its per-million-token prices so costs are logged correctly.

**Cost controls:**
- **Prompt caching.** Reference documents and instructions are sent first and cached, so repeat calls within five
  minutes pay 10% for them.
- **Batches API** for bulk generation (50% off).
- **Hard daily cap** (`CLAUDE_DAILY_SPEND_CAP_USD`, default $3). Background jobs may use at most 70% of it, so grading
  always has headroom.
- **Usage log.** Every call is recorded with tokens and cost in `api_usage`, visible under Settings.

Typical costs with the defaults: grading one Task 2 essay costs about $0.03–0.06, a Speaking test about $0.03, a
verified reading passage about $0.10–0.20, and a weekly coach report about $0.03.

## Resetting progress, export and backup

- **Keep content, clear practice history:** Settings → Reset progress (type `RESET`). This deletes sessions, grades,
  the error log, grammar progress, vocabulary, plans, coach reports, mock tests and recordings. Content, settings and
  your login stay.
- **Start completely fresh (local):** stop the backend, then `make reset CONFIRM=yes`. This deletes `data/db/` and the
  recordings. The admin account and seed content are recreated on the next start.
- **Start completely fresh (Docker):** `docker compose down -v && docker compose up -d`.
- **Export:** Settings → Export JSON / Export CSV (a ZIP with one CSV per table), or
  `GET /api/export?format=json|csv`. Credentials are never exported.
- **Backup:** the whole state is `data/` (locally) or the `ielts-prep-data` volume (Docker). Stop the backend and copy
  it.

## Project layout

```
backend/            Spring Boot API (Java 21) — Dockerfile, docker-compose.yml
  src/main/java/com/ieltsprep/
    claude/         ClaudeService, prompt/schema loading, caching, retries, spend cap, usage log, batches
    content/        items, seed loader, validators, topic taxonomy
    generation/     blueprints, two-pass generation, content buffer, batch generation
    reading/ listening/ writing/ speaking/ grammar/ vocab/   the modules
    grading/ errorlog/ marking/ band/                        grading dispatch, error log, answer marking, BandCalculator
    dashboard/ plan/ coach/ mock/ export/ settings/ auth/ system/ pipeline/
  src/main/resources/db/migration/   Flyway migrations
frontend/           ielts-prep-frontend, cloned here (gitignored in this repo) — React + Vite + TypeScript
  src/api/          typed client (schema.d.ts is generated from the OpenAPI spec)
  src/features/     one folder per page/module
  src/components/ src/lib/            shared UI and pure helpers (unit-tested)
data/               seed content, format templates, descriptors, AWL, source list; db/ and recordings/ at runtime
prompts/            one Markdown file per Claude call + JSON schemas
docs/               prompts.md, api/openapi.json
scripts/            validate-seed.mjs
docker-compose.yml  whole app (backend + ./frontend)
deploy.sh           one-command deploy
Makefile            shortcuts
```

## Tests

```bash
make test                       # everything
cd backend && ./mvnw test       # 193 JUnit tests
cd frontend && npm test         # 34 Vitest tests (+ npm run typecheck, npm run lint)
```

- **Backend unit tests:** `BandCalculator` (conversion tables, overall rounding, criteria bands), `AnswerMarker`
  (spelling variants, numbers, optional words, word limits), SM-2, AWL lookup, adaptive diagnostic, error-log trends,
  the study planner.
- **Backend integration tests** (full Spring context, in-memory H2, Claude mocked):
  - one flow per module: Reading, Listening, Writing, Speaking, Grammar, Vocabulary, Dashboard, Plan, Coach and the
    four-paper mock test;
  - prompt and schema catalogue checks;
  - seed-content validation and self-marking;
  - OpenAPI export.
- **Frontend tests:** pure helpers for charts, countdowns, voices, highlighting, word counts, vocabulary and plan
  actions. The UI was also checked visually with Playwright in light and dark themes.

## Design notes

- **H2 instead of SQLite.** Several things write concurrently:
  - Writing and Speaking grading on virtual threads;
  - the content buffer;
  - batch polling;
  - vocabulary enrichment;
  - CLI tasks while the server runs.

  H2's MVStore handles that with row-level locking, and its auto-server mode lets a second process share the file.
  SQLite allows a single writer and would need a separate JDBC driver and Hibernate dialect. The database is one file,
  `data/db/ielts.mv.db`.
- **Port 8090,** because 8080 is commonly taken by other local services. Change it with `SERVER_PORT` (backend) and
  `BACKEND_URL` (Vite proxy).
- **Async grading.** Grading runs after the submit transaction commits, on a virtual thread; the UI polls the result.
- **Audio.** Listening uses the browser's `SpeechSynthesis`, so no TTS key is needed. Voice quality depends on the
  browser and OS; Chrome and Edge have the best English accents, and on macOS extra voices can be added under
  System Settings → Accessibility → Spoken Content. Speaking records with `MediaRecorder`; transcription uses the Web
  Speech API (Chrome and Edge) or Whisper.
- **API contract.** springdoc generates the OpenAPI spec, and `openapi-typescript` generates the frontend types, so a
  backend DTO change shows up as a TypeScript error.
- **Security.**
  - Single-user login with a BCrypt password and HMAC-signed JWT.
  - Secrets only in `.env`.
  - The Docker backend runs as a non-root user.
  - Exports never include credentials.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Login says "Incorrect email or password" (401) | The account was created on the first start with the values `.env` had then. Run `./deploy.sh --reset-admin` on the server to set a new e-mail and password. |
| "No API key" in the sidebar | Click it (or Settings → Claude API) and paste your key, or run `./deploy.sh --api-key`. |
| Writing or Speaking stuck on "grading failed" | Check the key and Settings → Claude API; then **Grade now** on the result page. |
| HTTP 429 "daily spend cap reached" | Wait until midnight (local `TZ`) or raise `CLAUDE_DAILY_SPEND_CAP_USD`. |
| No voices or robotic voices in Listening | Use Chrome or Edge, or install more system voices; Speech rate is under Settings. |
| Microphone blocked | Allow it in the browser; use `http://localhost` or HTTPS. |
| "Bind for 0.0.0.0:8090 failed: port is already allocated" | Re-run `./deploy.sh` — it now picks free ports. Or set `BACKEND_PORT` / `FRONTEND_PORT` in `.env`; for a local backend, `SERVER_PORT`. |
| `docker compose: unknown command` | Use `docker-compose` (standalone Compose); the Makefile detects either. |
| "Database may be already in use" | An old backend process is still running from a different jar or directory; stop it. |
