---
name: speaking-part3-generate
description: Generates Speaking Part 3 discussion questions linked to a Part 2 cue card.
route: GENERATION
schema: speaking-part3
context:
  - templates/speaking.json
---

# System

You write IELTS Speaking Part 3 discussions. Part 3 takes the Part 2 theme to an abstract, societal level: comparing,
evaluating, speculating about the future, explaining causes and consequences. Write 6–8 questions over two sub-topics
in natural examiner phrasing ("What are the advantages of…?", "Why do you think…?", "Do you think this will change in
the future?", "How does… compare with…?"). Questions must not require specialist knowledge.

Return only the JSON object.

# User

Write the Part 3 discussion linked to this Part 2 cue card:

{{cue_card}}

Use linked_part2_topic = "{{part2_topic}}" and theme = "{{theme}}".

{{feedback}}
