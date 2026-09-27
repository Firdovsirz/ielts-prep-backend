# Prompts and schemas

Every call the backend makes to Claude is defined by one Markdown file in [`prompts/`](../prompts) and constrained by
one JSON schema in [`prompts/schemas/`](../prompts/schemas). This document explains how those files are used, what each
prompt does, what it receives and which code calls it, and how to change them safely.

- [How a prompt becomes an API call](#how-a-prompt-becomes-an-api-call)
- [Models, effort and cost controls](#models-effort-and-cost-controls)
- [Pipelines](#pipelines): content generation · grading · examiner · grammar · vocabulary · coach and plan
- [Prompt reference](#prompt-reference)
- [Schema reference](#schema-reference)
- [Editing prompts safely](#editing-prompts-safely)

## How a prompt becomes an API call

```markdown
---
name: reading-generate          # defaults to the file name
description: …                  # one line, shown in logs and in this document
route: GENERATION               # which model/effort/max_tokens to use (see below)
schema: reading-passage         # prompts/schemas/reading-passage.schema.json
context:                        # reference files under data/, placed first in the system prompt
  - templates/reading-academic.json
max_tokens: 32000               # optional; overrides claude.max-tokens.<route>
---

# System
Static instructions. Never contains {{variables}} — it is the cached prefix.

# User
The per-call request. {{variables}} are filled in by the backend.
```

`ClaudeService` turns a `ClaudeCall(prompt, vars, resultType)` into a Messages API request:

1. **System blocks** — each `context` file wrapped in `<reference path="data/…">…</reference>`, then the `# System`
   text. A prompt-cache breakpoint (`cache_control: ephemeral`, TTL from `claude.cache-ttl`, default 5 minutes) goes on
   the last system block, so the reference documents and instructions are cached and re-billed at 10% on the next call
   within the TTL.
2. **User message** — the `# User` section with `{{variables}}` substituted. Placeholders must start with a letter;
   anything else (for example `{{7}}` gap markers inside exam text) is left untouched, and so is any variable the caller
   did not supply.
3. **Structured output** — `output_config.format` is the JSON schema, so the reply is always valid JSON of that shape.
   It is deserialised into the Java record for the result type (snake_case fields).
4. **Effort** — `output_config.effort` from `claude.effort.<route>` (left out for Haiku, which does not accept it).
5. **Transport** — streaming with the official `anthropic-java` SDK (long outputs never hit request timeouts); SDK
   retries are off and transient failures (429, 5xx, overloaded, network) are retried by a `RetryTemplate` with
   exponential backoff (`claude.max-attempts`, `claude.initial-backoff-ms`).
6. **Accounting** — every call, successful or not, is written to `api_usage` with input/output/cache tokens and its
   cost in USD (prices in `claude.pricing`), in its own transaction so failures are logged too.

The Message Batches API uses the same request builder (`PreparedRequest.toBatchRequest`) at 50% of the price.

## Models, effort and cost controls

| Route | Default model | Effort | Max tokens | Used for |
|---|---|---|---|---|
| `GENERATION` | `claude-sonnet-5` | medium | 32000 | Writing passages, sections, prompts, exercises, word banks |
| `VERIFICATION` | `claude-sonnet-5` | medium | 16000 | Blind solving and reviewing generated items |
| `GRADING` | `claude-sonnet-5` | high | 24000 | Writing and Speaking grading |
| `COACHING` | `claude-sonnet-5` | medium | 16000 | Weekly coach report, study-plan personalisation |
| `EXAMINER` | `claude-sonnet-5` | low | 4000 | The live Speaking examiner (latency matters) |
| `FAST` | `claude-haiku-4-5` | — | 4000 | Vocabulary enrichment, grammar answer checks, template extraction |

Change a model with an environment variable in `.env` (`CLAUDE_MODEL_GENERATION`, `CLAUDE_MODEL_VERIFICATION`,
`CLAUDE_MODEL_GRADING`, `CLAUDE_MODEL_COACHING`, `CLAUDE_MODEL_EXAMINER`, `CLAUDE_MODEL_FAST`) and restart. If you add a
model, add its price under `claude.pricing` in `application.yml` so the cost log and the spend cap stay accurate.

**Daily spend cap.** `SpendGuard` sums today's `api_usage` cost before every call and refuses new calls once
`CLAUDE_DAILY_SPEND_CAP_USD` (default $3.00) is reached (HTTP 429 in the UI). Background work — the content buffer,
batches, vocabulary enrichment, the weekly coach run — may only use `claude.background-share` (70%) of the cap, so
interactive grading always has headroom.

## Pipelines

### Content generation (two-pass, verified)

```
Blueprint.plan() ──▶ *-generate (GENERATION) ──▶ ItemValidator (deterministic) ──▶ *-verify / content-review (VERIFICATION)
        ▲                                                   │ fail                          │
        └──────────── objections fed back as {{feedback}} ◀─┴───────────────────────────────┘   pass ──▶ item VERIFIED
```

- A **blueprint** (`ReadingBlueprint`, `ListeningBlueprint`, `WritingBlueprints`, `SpeakingBlueprints`,
  `GrammarBlueprints`, `VocabBlueprint`) chooses the topic (least-practised first), question-type mix, variant and the
  titles to avoid, and fills the generate prompt's variables.
- **`ItemValidator`** checks structure without calling Claude: question counts and numbering, word limits, answers that
  literally appear in the text, option letters, figure data consistency, and so on.
- **Verification** is done blind by a separate call that never sees the key. For items with auto-marked questions
  (Reading, Listening, grammar exercise sets) the verifier answers every question from the text alone; `QuestionJudge`
  marks those answers with the app's real marking rules (`AnswerMarker`). Any disagreement, a low-confidence answer or a
  reported blocker fails the item. Items without closed questions (Writing and Speaking prompts, word banks) go through
  `content-review` instead.
- Up to three attempts; each retry receives the verifier's objections in `{{feedback}}`. Only `VERIFIED` items are ever
  served; the last failed draft is kept as `FAILED` for inspection.
- `ContentBuffer` keeps at least five unseen verified items per bucket (every 30 minutes, background share of the cap).
  `make generate` submits the same work through the Batches API (generate batch → verify batch → regenerate failures).

### Grading

- **Writing** (`writing-grade`): one call per task with the band descriptors, the task's examiner notes and, for
  Academic Task 1, the figure's data. The model returns four criterion bands with quoted evidence, errors tagged with the
  grammar/vocabulary taxonomy (`original` must be a verbatim excerpt so the UI can highlight it), three concrete
  improvements, vocabulary upgrades and a model answer. The app computes the task band from the criteria (mean, rounded
  down to the half band) and the Writing band as (Task 1 + 2 × Task 2) / 3.
- **Speaking** (`speaking-grade`): grades transcripts. Pronunciation cannot be judged from text, so the model returns
  `pronunciation_assessable: false` and the band is the mean of the other three criteria. Optional audio metrics
  (speech rate, pauses) are passed in `{{audio_analysis}}` when Whisper transcription is enabled.
- Errors from both are written to the error log, which drives grammar drills, the study plan and the coach.

### Speaking examiner

`speaking-examiner` runs in conversation mode only: the backend supplies the test plan (Part 1 topics, the Part 2 cue
card, Part 3 questions), the conversation so far and the part's status; the model returns the next examiner turn and the
stage (`PART1`, `PART3` or `END`). Part 2 timing, the cue card and the transitions are scripted by the app, and without
an API key the whole test runs from the script.

### Grammar

- `grammar-exercise-generate` → `grammar-exercise-verify`: new exercise sets for one of the 13 areas.
- `grammar-error-drill-generate` → `grammar-exercise-verify`: a drill built from one of the candidate's own sentences
  (from the error log) plus three fresh items on the same rule.
- `grammar-answer-check` (FAST): open answers (sentence transformations, upgrades) that did not match the key exactly
  are checked in one batched call; without an API key the candidate self-assesses.

### Vocabulary

- `vocab-enrich` (FAST) fills in definition, examples, collocations, CEFR level and topic for up to 20 new cards at a
  time (after capture, and every 10 minutes for anything pending).
- `vocab-word-bank-generate` → `content-review`: new topic word banks on demand.

### Coach and study plan

- `coach-report` receives the week's statistics (`WeeklyStats`) and writes the report. It runs every Monday at 07:00
  (`ielts.coach.cron`) and on demand; without an API key a rule-based report (`CoachFallback`) is written instead.
- `study-plan` receives the candidate profile (`PlanInputs`), the rule-based draft for the next seven days and the
  latest coach priorities, and returns a personalised plan. `PlanService.merge` validates it: unknown actions or
  variants are dropped, mock-test and rest days are locked, and every day keeps its vocabulary review. Any failure falls
  back to the draft.

## Prompt reference

Variables are listed as they appear in the `# User` section. "Context" files are under `data/`.

### Content generation

| Prompt | Route · Schema | Context | Variables | Called from |
|---|---|---|---|---|
| `reading-generate` | GENERATION · `reading-passage` | `templates/reading-academic.json` | `passage_number`, `question_count`, `n` (first question number), `question_plan`, `topic_title`, `topic_summary`, `background_facts`, `passage_character`, `cefr_target`, `avoid_titles`, `feedback` | `ReadingBlueprint` |
| `listening-generate` | GENERATION · `listening-section` | `templates/listening.json` | `section`, `scenario`, `accent_hint`, `question_plan`, `n`, `background_facts`, `avoid_titles`, `feedback` | `ListeningBlueprint` |
| `writing-task1-academic-generate` | GENERATION · `writing-task1-academic` | `templates/writing-academic.json` | `chart_type`, `avoid_titles`, `feedback` | `WritingBlueprints` |
| `writing-task1-general-generate` | GENERATION · `writing-task1-general` | `templates/writing-general.json` | `letter_type`, `avoid_titles`, `feedback` | `WritingBlueprints` |
| `writing-task2-generate` | GENERATION · `writing-task2` | `templates/writing-academic.json`, `templates/topics.json` | `essay_type`, `topic`, `subtopic_hint`, `avoid_titles`, `feedback` | `WritingBlueprints` |
| `speaking-part1-generate` | GENERATION · `speaking-part1` | `templates/speaking.json` | `avoid_titles`, `feedback` | `SpeakingBlueprints` |
| `speaking-part2-generate` | GENERATION · `speaking-part2` | `templates/speaking.json` | `avoid_titles`, `feedback` | `SpeakingBlueprints` |
| `speaking-part3-generate` | GENERATION · `speaking-part3` | `templates/speaking.json` | `part2_topic`, `cue_card`, `theme`, `feedback` | `SpeakingBlueprints` |
| `grammar-exercise-generate` | GENERATION · `grammar-exercise-set` | `descriptors/grammar-areas.json` | `area`, `area_name`, `area_focus`, `exercise_type`, `recent_errors`, `feedback` | `GrammarBlueprints` |
| `grammar-error-drill-generate` | GENERATION · `grammar-error-drill` | `descriptors/grammar-areas.json` | `subtype`, `area`, `area_name`, `original`, `correction`, `explanation`, `own_sentence`, `other_examples`, `feedback` | `GrammarBlueprints` |
| `vocab-word-bank-generate` | GENERATION · `vocab-word-bank` | `templates/topics.json` | `topic`, `subtopics`, `existing`, `feedback` | `VocabBlueprint` |

`feedback` is empty on the first attempt and carries the verifier's objections on retries. `avoid_titles` lists recent
titles in the same bucket so topics do not repeat. `background_facts` comes from licensed source texts
(`data/sources/seeds.json`, `--task=fetch-sources`) and is used for facts only, never copied.

### Verification

| Prompt | Route · Schema | Context | Variables | Called from |
|---|---|---|---|---|
| `reading-verify` | VERIFICATION · `verification-report` | `templates/reading-academic.json` | `exam_paper` (passage + questions, no key), `question_count` | `ReadingBlueprint` → `QuestionJudge` |
| `listening-verify` | VERIFICATION · `verification-report` | `templates/listening.json` | `exam_paper` (script + questions, no key) | `ListeningBlueprint` → `QuestionJudge` |
| `grammar-exercise-verify` | VERIFICATION · `verification-report` | `descriptors/grammar-areas.json` | `exercise_text` (items without answers) | `GrammarBlueprints` |
| `content-review` | VERIFICATION · `verification-report` | `templates/writing-academic.json`, `templates/speaking.json` | `item_kind`, `content_json`, `checklist` | `WritingBlueprints`, `SpeakingBlueprints`, `VocabBlueprint` |

### Grading, examiner and coaching

| Prompt | Route · Schema | Context | Variables | Called from |
|---|---|---|---|---|
| `writing-grade` | GRADING · `writing-grade` | `descriptors/writing-band-descriptors.json`, `templates/writing-academic.json`, `descriptors/grammar-areas.json` | `task_label`, `prompt_text`, `figure_block`, `key_features`, `response_text`, `word_count`, `minutes_used`, `minutes_allowed`, `target_band` | `WritingGrader` |
| `speaking-grade` | GRADING · `speaking-grade` | `descriptors/speaking-band-descriptors.json`, `templates/speaking.json`, `descriptors/grammar-areas.json` | `scope`, `transcript`, `audio_analysis`, `target_band` | `SpeakingGrader` |
| `speaking-examiner` | EXAMINER · `speaking-examiner-turn` | `templates/speaking.json` | `plan`, `current_part`, `part_status`, `history` | `ExaminerService` |
| `coach-report` | COACHING · `coach-report` | `descriptors/writing-band-descriptors.json`, `descriptors/speaking-band-descriptors.json` | `week_start`, `week_end`, `stats` | `CoachService` |
| `study-plan` | COACHING · `study-plan` | — | `from`, `to`, `phase`, `days_to_test`, `daily_minutes`, `profile`, `priorities`, `draft` | `PlanService` |

### Fast helpers

| Prompt | Route · Schema | Variables | Called from |
|---|---|---|---|
| `vocab-enrich` | FAST · `vocab-enrich` | `words` (one per line, with context sentences) | `VocabEnricher` |
| `grammar-answer-check` | FAST · `grammar-answer-check` | `area`, `items` | `ExerciseService` |
| `templates-extract` | FAST · `templates-extract` | `doc_id`, `kind`, `module`, `url`, `text` | `FetchTemplatesTask` (`--task=fetch-templates`) |

`templates-extract` reads official IELTS format pages and extracts **structure only** (timings, question types, word
limits) into `data/templates/official/`; it is instructed never to copy exam content.

## Schema reference

| Schema | Produced by | Java type |
|---|---|---|
| `reading-passage` | `reading-generate`, seed files | `reading.ReadingPassage` |
| `listening-section` | `listening-generate`, seed files | `listening.ListeningSection` |
| `writing-task1-academic` | `writing-task1-academic-generate`, seed files | `writing.WritingPrompts.Task1Academic` |
| `writing-task1-general` | `writing-task1-general-generate`, seed files | `writing.WritingPrompts.Task1General` |
| `writing-task2` | `writing-task2-generate`, seed files | `writing.WritingPrompts.Task2` |
| `speaking-part1` / `speaking-part2` / `speaking-part3` | speaking generate prompts, seed files | `speaking.SpeakingPrompts.*` |
| `grammar-exercise-set` | `grammar-exercise-generate`, seed files | `grammar.GrammarContent.ExerciseSet` |
| `grammar-error-drill` | `grammar-error-drill-generate` | `grammar.GrammarContent.ErrorDrill` |
| `grammar-lesson` | seed files only | `grammar.GrammarContent.Lesson` |
| `grammar-diagnostic-question` | seed files only (`data/seed/grammar/diagnostic.json`) | `grammar.GrammarContent.DiagnosticQuestion` |
| `vocab-word-bank` | `vocab-word-bank-generate`, seed files | `vocab.WordBank` |
| `verification-report` | all verify prompts and `content-review` | `generation.VerificationReport` |
| `writing-grade` | `writing-grade` | `grading.GradingModels.WritingGrade` |
| `speaking-grade` | `speaking-grade` | `grading.GradingModels.SpeakingGrade` |
| `speaking-examiner-turn` | `speaking-examiner` | `speaking.ExaminerTurn` |
| `grammar-answer-check` | `grammar-answer-check` | `grammar.AnswerCheck` |
| `vocab-enrich` | `vocab-enrich` | `vocab.VocabEnricher.Enriched` |
| `coach-report` | `coach-report` | `coach.CoachReportContent` |
| `study-plan` | `study-plan` | `plan.PlanService.AiPlan` |
| `templates-extract` | `templates-extract` | JSON tree, saved by `pipeline.FetchTemplatesTask` |

The same content schemas validate the hand-written seed items: `scripts/validate-seed.mjs` and `SeedContentTest` check
every file in `data/seed/` against them, and `SeedContentTest` also self-marks every seeded question with the answer key.

The `subtype` enum in `writing-grade` and `speaking-grade` must match `data/descriptors/grammar-areas.json`;
`GradingSchemaTaxonomyTest` fails if they drift apart.

## Editing prompts safely

- **Wording** in `# System` and `# User` can be changed freely. Restart the backend to reload prompts.
- **Keep variables out of `# System`.** Anything that changes per call belongs in `# User`, otherwise the cached prefix
  changes on every call and caching stops working. `PromptCatalogueTest` enforces this.
- **Schema changes need code changes.** Field names map to Java records (snake_case). Add a field to the schema *and*
  the record; removing a required field the code reads will break grading or generation.
- **Structured outputs support a subset of JSON Schema.** Stick to `type`, `properties`, `required`, `items`, `enum`,
  `description` and `additionalProperties: false`, as the existing schemas do. Numeric limits (`minimum`,
  `maxItems`, …) are enforced in code instead.
- **Test after editing:** `make test-backend` runs `PromptCatalogueTest` (every prompt parses, its schema and context
  files exist, no variables in system text) and the flow tests with a mocked Claude. To try a prompt for real, set the
  API key and run `make generate-sync MODULE=reading COUNT=1` or grade one essay in the app; the `api_usage` table and
  Settings → Claude API show the cost.
