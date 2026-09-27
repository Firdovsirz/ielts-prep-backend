# IELTS Prep — common tasks. Run `make help` for the list.

SHELL := /bin/bash
# macOS: pick JDK 21 automatically; elsewhere set JAVA_HOME yourself.
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 2>/dev/null)
export JAVA_HOME
# `docker compose` (plugin) or the standalone `docker-compose`.
COMPOSE ?= $(shell docker compose version >/dev/null 2>&1 && echo "docker compose" || echo docker-compose)

MVN := cd backend && ./mvnw -B
TASK = $(MVN) -q spring-boot:run -Dspring-boot.run.arguments="$(1)"

MODULE ?= reading
COUNT ?= 5

.PHONY: help install dev backend frontend test test-backend test-frontend lint build gen-api \
        docker-up docker-down docker-logs generate generate-sync buffer seed status fetch-templates fetch-sources reset

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

install: ## Install frontend dependencies (the Maven wrapper fetches backend ones)
	cd frontend && npm ci

dev: ## Run backend (:8090) and frontend (:5173) together
	@$(MAKE) -j2 backend frontend

backend: ## Run the Spring Boot backend on :8090
	$(MVN) spring-boot:run

frontend: ## Run the Vite dev server on :5173 (proxies /api to :8090)
	cd frontend && npm run dev

test: test-backend test-frontend ## Run all tests

test-backend: ## JUnit tests
	$(MVN) test

test-frontend: ## Vitest + typecheck + lint
	cd frontend && npm run typecheck && npm run lint && npm test

lint: ## Lint and format-check the frontend
	cd frontend && npm run lint && npm run format:check

build: ## Build the backend jar and the frontend bundle
	$(MVN) -q package -DskipTests
	cd frontend && npm run build

gen-api: ## Export OpenAPI (docs/api/openapi.json) and regenerate frontend/src/api/schema.d.ts
	$(MVN) -q test -Dtest=OpenApiExportTest
	cd frontend && npm run gen:api

docker-up: ## Build and start the whole app in Docker → http://localhost:3000
	$(COMPOSE) up -d --build

docker-down: ## Stop the Docker stack (data volume is kept)
	$(COMPOSE) down

docker-logs: ## Follow the Docker logs
	$(COMPOSE) logs -f

generate: ## Generate + verify content via the Batches API: make generate MODULE=reading COUNT=10
	$(call TASK,--task=generate --module=$(MODULE) --count=$(COUNT))

generate-sync: ## Same, one item at a time (no batch discount, results immediately)
	$(call TASK,--task=generate --module=$(MODULE) --count=$(COUNT) --sync)

buffer: ## Top up every content bucket to the minimum now
	$(call TASK,--task=buffer)

seed: ## (Re)load the seed content from data/seed
	$(call TASK,--task=seed)

status: ## Content inventory and buffer levels
	$(call TASK,--task=status)

fetch-templates: ## Fetch official IELTS format pages into data/templates/official
	$(call TASK,--task=fetch-templates)

fetch-sources: ## Fetch licensed source texts listed in data/sources/seeds.json
	$(call TASK,--task=fetch-sources)

reset: ## Delete the local database and recordings (stop the backend first): make reset CONFIRM=yes
	@if [ "$(CONFIRM)" != "yes" ]; then echo "This deletes data/db and data/recordings. Run: make reset CONFIRM=yes"; exit 1; fi
	rm -f data/db/*.db data/.jwt-secret
	find data/recordings -mindepth 1 ! -name .gitkeep -delete
	@echo "Local progress deleted. Seed content and the admin account are recreated on the next start."
