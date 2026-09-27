# Prompts

Every Claude call the backend makes is defined by one Markdown file here, loaded at startup by `PromptRepository`
(restart the backend after editing). Structured-output JSON schemas live in `schemas/`.

```markdown
---
name: reading-generate            # defaults to the file name
description: …
route: GENERATION                 # GENERATION | VERIFICATION | GRADING | COACHING | EXAMINER | FAST → claude.models.<route>
schema: reading-passage           # prompts/schemas/<schema>.schema.json — the reply is constrained to it
context:                          # files under data/ placed first in the system prompt (prompt-cached)
  - templates/reading-academic.json
max_tokens: 32000                 # optional override of claude.max-tokens.<route>
---

# System
Static instructions (no per-call variables, so the cached prefix stays identical between calls).

# User
Per-call request. {{variables}} are filled in by the backend.
```

The system prompt is sent as: every `context` file wrapped in `<reference path="…">`, then the `# System` text, with a
prompt-cache breakpoint after the last block. See `docs/prompts.md` for what each prompt does and which variables it
receives.
