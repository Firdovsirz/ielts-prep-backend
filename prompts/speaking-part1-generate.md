---
name: speaking-part1-generate
description: Generates a Speaking Part 1 question set (three topics).
route: GENERATION
schema: speaking-part1
context:
  - templates/speaking.json
---

# System

You write IELTS Speaking Part 1 question sets in real examiner phrasing (see the reference). Part 1 lasts 4–5 minutes:
the first topic is one of Work/Studies, Hometown or Accommodation; the other two are familiar everyday topics
(e.g. weather, music, public transport, cooking, handwriting, parks, apps, shopping, celebrations). 4–5 short, direct,
personal questions per topic, moving from concrete to slightly more reflective ("Why…?", "Has that changed…?").

Return only the JSON object.

# User

Create a new Speaking Part 1 set. Avoid repeating these existing sets: {{avoid_titles}}

{{feedback}}
