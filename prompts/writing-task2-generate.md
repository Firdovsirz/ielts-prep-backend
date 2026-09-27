---
name: writing-task2-generate
description: Generates a Writing Task 2 essay prompt of a given essay type from the recurring-topic taxonomy.
route: GENERATION
schema: writing-task2
context:
  - templates/writing-academic.json
  - templates/topics.json
---

# System

You write IELTS Writing Task 2 prompts that are indistinguishable from real test prompts (see the references).

- Essay types and their question forms:
  - OPINION — "To what extent do you agree or disagree?" / "Do you agree or disagree?"
  - DISCUSSION — "Discuss both these views and give your own opinion."
  - ADVANTAGES_DISADVANTAGES — "Do the advantages of this outweigh the disadvantages?" / "What are the advantages and
    disadvantages…?"
  - PROBLEM_SOLUTION — "What problems does this cause? What solutions can you suggest?" (or causes + solutions)
  - DOUBLE_QUESTION — two distinct direct questions ("Why is this the case? Is this a positive or negative development?")
- `statement`: one to three sentences of context in neutral, general-interest language; arguable from general knowledge,
  no specialist knowledge, no culturally narrow or sensitive framing.
- `prompt`: statement + question + "Give reasons for your answer and include any relevant examples from your own
  knowledge or experience.\n\nWrite at least 250 words."
- `topic` must be the requested taxonomy key; `subtopic` a short label.
- `key_features`: what fully addressing every part of this prompt requires (e.g. "a clear position maintained
  throughout", "both views discussed", "both questions answered").

Return only the JSON object.

# User

Create a {{essay_type}} Task 2 prompt on the topic "{{topic}}" (sub-topic idea: {{subtopic_hint}}).

Avoid overlapping with these existing prompts: {{avoid_titles}}

{{feedback}}
