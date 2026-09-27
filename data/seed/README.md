# Seed content

Everything under `data/seed/` is loaded into the database on first start (`SeedLoader`) and marked
`VERIFIED` so every module works before any Claude API call is made. The files use **exactly the same
JSON shape that Claude produces at generation time** (the schemas in `prompts/schemas/`), so the same
parsing, validation and marking code handles both.

Validate with:

```bash
node scripts/validate-seed.mjs            # everything
node scripts/validate-seed.mjs data/seed/reading
```

The backend runs the same checks (`ItemValidator`) on every generated item before the independent
verification pass.

## File format

One JSON object per file (or an array of them):

```json
{
  "task_type": "READING_PASSAGE",
  "source_url": "https://en.wikipedia.org/wiki/Urban_beekeeping",
  "licence": "CC BY-SA 4.0 (topic seed only; passage text is original)",
  "content": { "...": "conforms to prompts/schemas/reading-passage.schema.json" }
}
```

| task_type | schema | folder |
|---|---|---|
| `READING_PASSAGE` | `reading-passage` | `reading/` |
| `LISTENING_SECTION` | `listening-section` | `listening/` |
| `WRITING_TASK1_ACADEMIC` | `writing-task1-academic` | `writing/` |
| `WRITING_TASK1_GENERAL` | `writing-task1-general` | `writing/` |
| `WRITING_TASK2` | `writing-task2` | `writing/` |
| `SPEAKING_PART1/2/3` | `speaking-part1/2/3` | `speaking/` |
| `GRAMMAR_LESSON` | `grammar-lesson` | `grammar/lessons/` |
| `GRAMMAR_EXERCISE` | `grammar-exercise-set` | `grammar/exercises/` |
| `GRAMMAR_DIAGNOSTIC` | `grammar-diagnostic-question` | `grammar/diagnostic.json` (array) |
| `VOCAB_WORD_BANK` | `vocab-word-bank` | `vocab/` |

Every property in a schema is **required** and no extra properties are allowed. Unused parts are
present but empty: `""`, `[]`, `{"title":"","columns":[],"rows":[]}` for `table`,
`{"title":"","nodes":[],"edges":[]}` for `diagram`, `{"title":"","features":[],"markers":[]}` for `map`,
`0` for `word_limit`, `false` for `number_allowed`.

## Copyright rules (non-negotiable)

- Never reproduce, paraphrase closely, or imitate specific items from Cambridge IELTS books, official
  practice tests or any other paid material. All passages, scripts, prompts and questions are original.
- Wikipedia / Wikimedia (CC BY-SA 4.0), government, UN/WHO/OECD, Our World in Data and arXiv pages may be
  used as **topic seeds** only. Record the URL and licence; never copy sentences from them.
- Writing/Speaking/Grammar/Vocabulary items have `"source_url": ""` and
  `"licence": "Original content"`.

## Question groups (Reading and Listening)

Question numbers are local to the passage/section, start at 1 and run contiguously across groups in
order. Each group carries the exact IELTS rubric in `instructions`, including the range, for example
`"Questions 1–5\nDo the following statements agree with the information given in the passage?\nWrite\nTRUE if the statement agrees with the information\nFALSE if the statement contradicts the information\nNOT GIVEN if there is no information on this"`.

| question_type | how to encode | answers |
|---|---|---|
| `MULTIPLE_CHOICE` | per-question `options` A–D, one correct | `["C"]` |
| `MULTIPLE_CHOICE_MULTI` | "Choose TWO letters, A–E." Group `options` A–E; two (or three) questions with the **same** prompt | one distinct letter per question, e.g. Q5 `["B"]`, Q6 `["E"]` (order-free when marked) |
| `TRUE_FALSE_NOT_GIVEN` | statements in `prompt`; group `options` empty | `["TRUE"]` / `["FALSE"]` / `["NOT GIVEN"]` — use each at least once |
| `YES_NO_NOT_GIVEN` | the writer's views/claims | `["YES"]` / `["NO"]` / `["NOT GIVEN"]` |
| `MATCHING_INFORMATION` | "Which paragraph contains…?" group `options` = `{"key":"A","text":"Paragraph A"}`… | paragraph letter; add "NB You may use any letter more than once." |
| `MATCHING_HEADINGS` | group `options` = headings keyed `i, ii, iii…` (more headings than paragraphs); `prompt` = `"Paragraph B"` | `["iv"]` |
| `MATCHING_FEATURES` | group `options` = people/places/dates A–E | letter |
| `MATCHING_SENTENCE_ENDINGS` | `prompt` = sentence beginning; group `options` = endings A–G (more than questions) | letter |
| `SENTENCE_COMPLETION` | `prompt` = the sentence with the gap written `{{n}}` | words from the passage within `word_limit` |
| `SUMMARY_COMPLETION` / `NOTE_COMPLETION` / `FORM_COMPLETION` | text in `context` with gaps `{{n}}`; `prompt` = `""`. With a word bank: group `options` A–H and letter answers, `word_limit` 0 | words or letter |
| `TABLE_COMPLETION` | `table` with `{{n}}` inside cells; `prompt` = `""` | words |
| `FLOW_CHART_COMPLETION` | `flow_steps` with `{{n}}`; `prompt` = `""` | words |
| `DIAGRAM_LABEL_COMPLETION` | `diagram.nodes` (x/y 0–100) with some labels `{{n}}`, `edges` connect them; `prompt` = `""` | words |
| `SHORT_ANSWER` | `prompt` = the question | words within `word_limit` |
| `MAP_LABELLING` (listening) | `map.features` (rects on a 0–100 canvas), `map.markers` letters placed on unlabeled features; group `options` = `{"key":"A","text":"A"}`…; `prompt` = place name | marker letter |
| `MATCHING` (listening) | group `options` A–G; `prompt` = item | letter |

Rules the validator enforces:

- `justification_span` is copied **verbatim** from the passage/script (a full sentence or clause).
  For NOT GIVEN, copy the sentence closest to the topic.
- Reading completion answers are words taken **verbatim from the passage**, spelt as in the passage,
  within the word limit. Put optional words in parentheses: `"(the) harbour"`; list genuine alternatives
  after the canonical answer.
- No answer may appear in its own stem.
- TFNG / YNNG / MULTIPLE_CHOICE / SENTENCE_COMPLETION / SHORT_ANSWER follow passage order.
- Reading: difficulty 1 and 2 → 13 questions; difficulty 3 → 14 questions (a full test = 40). Passage is
  700–900 words in 5–9 labelled paragraphs, at least two question types per passage.
- Listening: exactly 10 questions; `location` is the 0-based index of the script line that contains the
  justification span; `parts` split the questions (Section 4 is one part starting at line 0); answers are
  said in the script in question order; Section 1 and 3 have 2+ speakers, Section 4 is a monologue.
