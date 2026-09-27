#!/usr/bin/env node
// Validates seed content files in data/seed/** against the JSON schemas in prompts/schemas
// plus IELTS-format rules (numbering, verbatim justification spans, word limits, option keys).
// Usage: node scripts/validate-seed.mjs [file-or-dir ...]   (default: data/seed)
// The backend applies the same rules at generation time (ItemValidator.java).
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SCHEMA_DIR = join(ROOT, 'prompts', 'schemas');

const TASK_SCHEMAS = {
  READING_PASSAGE: 'reading-passage',
  LISTENING_SECTION: 'listening-section',
  WRITING_TASK1_ACADEMIC: 'writing-task1-academic',
  WRITING_TASK1_GENERAL: 'writing-task1-general',
  WRITING_TASK2: 'writing-task2',
  SPEAKING_PART1: 'speaking-part1',
  SPEAKING_PART2: 'speaking-part2',
  SPEAKING_PART3: 'speaking-part3',
  GRAMMAR_LESSON: 'grammar-lesson',
  GRAMMAR_EXERCISE: 'grammar-exercise-set',
  GRAMMAR_DIAGNOSTIC: 'grammar-diagnostic-question',
  VOCAB_WORD_BANK: 'vocab-word-bank',
};

const CHOICE_TYPES = new Set([
  'MULTIPLE_CHOICE', 'MULTIPLE_CHOICE_MULTI', 'TRUE_FALSE_NOT_GIVEN', 'YES_NO_NOT_GIVEN',
  'MATCHING_INFORMATION', 'MATCHING_HEADINGS', 'MATCHING_FEATURES', 'MATCHING_SENTENCE_ENDINGS',
  'MATCHING', 'MAP_LABELLING',
]);
const ORDERED_TYPES = new Set([
  'TRUE_FALSE_NOT_GIVEN', 'YES_NO_NOT_GIVEN', 'MULTIPLE_CHOICE', 'SENTENCE_COMPLETION', 'SHORT_ANSWER',
]);

function loadSchema(name) {
  return JSON.parse(readFileSync(join(SCHEMA_DIR, `${name}.schema.json`), 'utf8'));
}

// Minimal JSON-schema subset validator (type, enum, required, additionalProperties:false, items, $ref-free).
function validateSchema(schema, value, path, errors) {
  const t = schema.type;
  if (t === 'object') {
    if (typeof value !== 'object' || value === null || Array.isArray(value)) return errors.push(`${path}: expected object`);
    for (const key of schema.required ?? []) if (!(key in value)) errors.push(`${path}.${key}: missing`);
    for (const key of Object.keys(value)) {
      if (!schema.properties?.[key]) { if (schema.additionalProperties === false) errors.push(`${path}.${key}: unexpected property`); continue; }
      validateSchema(schema.properties[key], value[key], `${path}.${key}`, errors);
    }
  } else if (t === 'array') {
    if (!Array.isArray(value)) return errors.push(`${path}: expected array`);
    value.forEach((v, i) => validateSchema(schema.items, v, `${path}[${i}]`, errors));
  } else if (t === 'string') {
    if (typeof value !== 'string') return errors.push(`${path}: expected string`);
  } else if (t === 'integer') {
    if (!Number.isInteger(value)) return errors.push(`${path}: expected integer`);
  } else if (t === 'number') {
    if (typeof value !== 'number') return errors.push(`${path}: expected number`);
  } else if (t === 'boolean') {
    if (typeof value !== 'boolean') return errors.push(`${path}: expected boolean`);
  }
  if (schema.enum && !schema.enum.includes(value)) errors.push(`${path}: ${JSON.stringify(value)} not in enum`);
}

const norm = (s) => s.replace(/[‘’]/g, "'").replace(/[“”]/g, '"').replace(/[–—]/g, '-').replace(/\s+/g, ' ').trim().toLowerCase();
const wordCount = (s) => s.trim().split(/\s+/).filter(Boolean).length;
const answerWords = (a) => wordCount(a.replace(/[()]/g, ''));

function checkGroups(groups, sourceText, errors, warnings, { expectCount, isListening, scriptLines }) {
  const numbers = groups.flatMap((g) => g.questions.map((q) => q.number));
  const expected = Array.from({ length: numbers.length }, (_, i) => i + 1);
  if (JSON.stringify(numbers) !== JSON.stringify(expected)) errors.push(`question numbers must be 1..N contiguous in order, got ${numbers.join(',')}`);
  if (expectCount && numbers.length !== expectCount) errors.push(`expected ${expectCount} questions, got ${numbers.length}`);
  const types = new Set(groups.map((g) => g.question_type));
  if (!isListening && types.size < 2) errors.push('reading passage needs at least 2 different question types');
  const src = norm(sourceText);

  for (const g of groups) {
    const qs = g.questions;
    const range = `${qs[0]?.number}–${qs[qs.length - 1]?.number}`;
    const tag = `group ${g.group_id} (${g.question_type}, Q${range})`;
    if (!g.instructions.trim()) errors.push(`${tag}: empty instructions`);
    const choice = CHOICE_TYPES.has(g.question_type) || g.options.length > 0;
    const groupKeys = new Set(g.options.map((o) => o.key));

    if (g.question_type === 'MATCHING_HEADINGS' && g.options.length <= qs.length) errors.push(`${tag}: needs more headings than paragraphs`);
    if (g.question_type === 'MULTIPLE_CHOICE') for (const q of qs) if (q.options.length < 3) errors.push(`${tag} Q${q.number}: MULTIPLE_CHOICE needs per-question options`);
    if (g.question_type === 'MULTIPLE_CHOICE_MULTI') {
      if (g.options.length < 5) errors.push(`${tag}: MULTIPLE_CHOICE_MULTI needs 5+ shared options`);
      const all = qs.map((q) => q.answers[0]);
      if (new Set(all).size !== all.length) errors.push(`${tag}: MULTIPLE_CHOICE_MULTI answers must be distinct letters`);
    }
    if (g.question_type === 'TABLE_COMPLETION' && g.table.rows.length === 0) errors.push(`${tag}: empty table`);
    if (g.question_type === 'FLOW_CHART_COMPLETION' && g.flow_steps.length === 0) errors.push(`${tag}: empty flow_steps`);
    if (g.question_type === 'DIAGRAM_LABEL_COMPLETION' && g.diagram.nodes.length === 0) errors.push(`${tag}: empty diagram`);
    if (g.question_type === 'MAP_LABELLING' && (g.map.features.length === 0 || g.map.markers.length === 0)) errors.push(`${tag}: map needs features and markers`);

    // gaps present for context-based completion
    const gapText = [g.context, ...g.table.rows.flat(), ...g.flow_steps, ...g.diagram.nodes.map((n) => n.label)].join(' ');
    if (['SUMMARY_COMPLETION', 'NOTE_COMPLETION', 'FORM_COMPLETION', 'TABLE_COMPLETION', 'FLOW_CHART_COMPLETION', 'DIAGRAM_LABEL_COMPLETION'].includes(g.question_type)) {
      for (const q of qs) if (!gapText.includes(`{{${q.number}}}`)) errors.push(`${tag}: gap {{${q.number}}} missing from context/table/flow/diagram`);
    }

    let lastPos = -1;
    for (const q of qs) {
      const qt = `${tag} Q${q.number}`;
      if (q.answers.length === 0) { errors.push(`${qt}: no answers`); continue; }
      if (choice) {
        const keys = g.question_type === 'TRUE_FALSE_NOT_GIVEN' ? new Set(['TRUE', 'FALSE', 'NOT GIVEN'])
          : g.question_type === 'YES_NO_NOT_GIVEN' ? new Set(['YES', 'NO', 'NOT GIVEN'])
          : new Set([...groupKeys, ...q.options.map((o) => o.key)]);
        for (const a of q.answers) if (!keys.has(a)) errors.push(`${qt}: answer "${a}" is not an option key (${[...keys].join('/')})`);
      } else if (g.word_limit > 0) {
        for (const a of q.answers) if (answerWords(a) > g.word_limit) errors.push(`${qt}: answer "${a}" exceeds word limit ${g.word_limit}`);
        if (!g.number_allowed && q.answers.some((a) => /\d/.test(a))) warnings.push(`${qt}: numeric answer but number_allowed=false`);
      }
      if (!choice && !isListening) {
        const canonical = norm(q.answers[0].replace(/[()]/g, ''));
        if (!src.includes(canonical)) errors.push(`${qt}: completion answer "${q.answers[0]}" does not appear in the passage`);
      }
      if (q.prompt && q.answers.some((a) => !choice && a.length > 3 && norm(q.prompt).includes(norm(a)))) errors.push(`${qt}: answer leaked in the stem`);
      const span = norm(q.justification_span);
      if (!span) errors.push(`${qt}: empty justification_span`);
      const pos = src.indexOf(span);
      if (span && pos < 0) errors.push(`${qt}: justification_span not found verbatim: "${q.justification_span.slice(0, 80)}"`);
      if (isListening && span && pos >= 0) {
        const line = Number(q.location);
        if (!Number.isInteger(line) || !scriptLines[line] || !norm(scriptLines[line]).includes(span)) warnings.push(`${qt}: location "${q.location}" is not the script line containing the span`);
      }
      if (ORDERED_TYPES.has(g.question_type) && pos >= 0) {
        if (pos < lastPos) warnings.push(`${qt}: questions of this type should follow passage/script order`);
        lastPos = pos;
      }
    }
  }
}

function validateReading(c, errors, warnings) {
  const text = c.paragraphs.map((p) => p.text).join('\n');
  const words = wordCount(text);
  if (words < 650 || words > 1000) errors.push(`passage word count ${words} outside 700–900 (±50 tolerance)`);
  const labels = c.paragraphs.map((p) => p.label);
  if (new Set(labels).size !== labels.length) errors.push('duplicate paragraph labels');
  checkGroups(c.question_groups, text, errors, warnings, { expectCount: c.difficulty === 3 ? 14 : 13, isListening: false });
  for (const g of c.question_groups) for (const q of g.questions) if (!labels.includes(q.location)) errors.push(`Q${q.number}: location "${q.location}" is not a paragraph label`);
}

function validateListening(c, errors, warnings) {
  const lines = c.script.map((l) => l.text);
  const ids = new Set(c.speakers.map((s) => s.id));
  for (const [i, l] of c.script.entries()) if (!ids.has(l.speaker)) errors.push(`script[${i}]: unknown speaker ${l.speaker}`);
  const words = wordCount(lines.join(' '));
  if (words < 450 || words > 1300) warnings.push(`script word count ${words} (typical 550–1000)`);
  if (c.section === 4 && c.speakers.length !== 1) errors.push('section 4 must be a monologue');
  if ((c.section === 1 || c.section === 3) && c.speakers.length < 2) errors.push(`section ${c.section} needs 2+ speakers`);
  let expectFrom = 1;
  let lastLine = -1;
  for (const p of c.parts) {
    if (p.questions_from !== expectFrom) errors.push(`parts must cover questions contiguously (expected from ${expectFrom})`);
    if (p.start_line <= lastLine || p.start_line >= lines.length) errors.push(`part start_line ${p.start_line} invalid`);
    expectFrom = p.questions_to + 1; lastLine = p.start_line;
  }
  if (expectFrom !== 11) errors.push('parts must end at question 10');
  if (c.parts[0]?.start_line !== 0) errors.push('first part must start at line 0');
  checkGroups(c.question_groups, lines.join('\n'), errors, warnings, { expectCount: 10, isListening: true, scriptLines: lines });
  const script = norm(lines.join(' '));
  for (const g of c.question_groups) {
    if (CHOICE_TYPES.has(g.question_type) || g.options.length) continue;
    for (const q of g.questions) {
      if (!q.answers.some((x) => script.includes(norm(x.replace(/[()]/g, ''))))) warnings.push(`Q${q.number}: answer "${q.answers[0]}" not heard verbatim in script (ok only if spelled out or said as digits)`);
    }
  }
}

function validateContent(task, c, errors, warnings) {
  switch (task) {
    case 'READING_PASSAGE': return validateReading(c, errors, warnings);
    case 'LISTENING_SECTION': return validateListening(c, errors, warnings);
    case 'WRITING_TASK1_ACADEMIC': {
      if (['LINE', 'BAR', 'PIE', 'TABLE'].includes(c.chart_type)) {
        if (!c.categories.length || !c.series.length) errors.push('chart needs categories and series');
        for (const s of c.series) if (s.values.length !== c.categories.length) errors.push(`series ${s.name}: ${s.values.length} values for ${c.categories.length} categories`);
        if (c.chart_type === 'PIE') for (const s of c.series) { const sum = s.values.reduce((a, b) => a + b, 0); if (Math.abs(sum - 100) > 1.5) warnings.push(`pie ${s.name} sums to ${sum}`); }
      }
      if (c.chart_type === 'PROCESS' && c.process_steps.length < 5) errors.push('process needs 5+ steps');
      if (c.chart_type === 'MAP' && c.maps.length !== 2) errors.push('map comparison needs exactly two maps');
      return;
    }
    case 'WRITING_TASK1_GENERAL': if (c.bullet_points.length !== 3) errors.push('letter needs 3 bullets'); return;
    case 'SPEAKING_PART1': if (c.topics.length !== 3) errors.push('Part 1 needs 3 topics'); return;
    case 'SPEAKING_PART2': if (c.cue_card.bullets.length !== 3) errors.push('cue card needs 3 bullets'); if (!c.cue_card.explain.startsWith('and explain')) errors.push('cue card final line must start "and explain"'); return;
    case 'GRAMMAR_EXERCISE': if (c.items.length < 3) errors.push('exercise needs 3+ items'); for (const it of c.items) if (it.check_mode === 'EXACT' && !it.accepted_answers.length) errors.push(`item ${it.id}: EXACT needs accepted_answers`); return;
    case 'GRAMMAR_DIAGNOSTIC': if (c.options.length !== 4 || !c.options.some((o) => o.key === c.answer)) errors.push(`${c.id}: needs 4 options including the answer`); return;
    default: return;
  }
}

function validateFile(file) {
  const errors = []; const warnings = [];
  let doc;
  try { doc = JSON.parse(readFileSync(file, 'utf8')); } catch (e) { return { errors: [`invalid JSON: ${e.message}`], warnings }; }
  const docs = Array.isArray(doc) ? doc : [doc];
  docs.forEach((d, i) => {
    const pre = docs.length > 1 ? `[${i}] ` : '';
    for (const k of ['task_type', 'source_url', 'licence', 'content']) if (!(k in d)) errors.push(`${pre}missing top-level "${k}"`);
    const schemaName = TASK_SCHEMAS[d.task_type];
    if (!schemaName) return errors.push(`${pre}unknown task_type ${d.task_type}`);
    const e2 = []; const w2 = [];
    validateSchema(loadSchema(schemaName), d.content, 'content', e2);
    if (!e2.length) validateContent(d.task_type, d.content, e2, w2);
    errors.push(...e2.map((e) => pre + e)); warnings.push(...w2.map((w) => pre + w));
  });
  return { errors, warnings };
}

function collect(p) {
  if (statSync(p).isDirectory()) return readdirSync(p).flatMap((f) => collect(join(p, f)));
  return p.endsWith('.json') ? [p] : [];
}

const targets = process.argv.slice(2);
const files = (targets.length ? targets : [join(ROOT, 'data', 'seed')]).flatMap(collect);
let failed = 0;
for (const f of files) {
  const { errors, warnings } = validateFile(f);
  const rel = f.replace(ROOT + '/', '');
  if (errors.length) { failed++; console.log(`✗ ${rel}`); errors.forEach((e) => console.log(`    ERROR ${e}`)); }
  else console.log(`✓ ${rel}`);
  warnings.forEach((w) => console.log(`    warn  ${w}`));
}
console.log(`\n${files.length - failed}/${files.length} files valid`);
process.exit(failed ? 1 : 0);
