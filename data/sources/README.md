# Sources

`seeds.json` lists open-licence pages used as **topic seeds** for generating Reading passages and
Listening scripts. A seed is a topic prompt plus factual grounding, nothing more: Claude always writes
an **original** passage or script on the seed's topic. It never copies, closely paraphrases or
translates sentences from the source. The generated item records the seed's `url` and `licence` in
`source_url` / `licence` (see `data/seed/README.md`).

## Allowed sources and licences

| provider | example | licence recorded |
|---|---|---|
| `wikipedia` | `https://en.wikipedia.org/wiki/<Title>` | `CC BY-SA 4.0` |
| `ourworldindata` | `https://ourworldindata.org/<topic>` | `CC BY 4.0` |
| `arxiv` | `https://arxiv.org/abs/<id>` (abstract page only) | `arXiv.org perpetual non-exclusive licence (abstract metadata CC0)` |
| `gutenberg` | `https://www.gutenberg.org/ebooks/<n>` | `Public domain (US)` |
| `un`, `who`, `oecd`, `gov` | official pages whose terms allow reuse | the licence stated on the page |

Never use Cambridge IELTS books, official practice tests, test-prep sites or any other paid or
all-rights-reserved material, not even as a seed.

## seeds.json format

```json
{"seeds":[{"id":"wiki-<slug>","title":"…","url":"https://…","licence":"CC BY-SA 4.0",
  "provider":"wikipedia","topic":"<taxonomy key | science | history | nature>",
  "modules":["reading","listening"],"summary":"one sentence on what a passage could cover"}]}
```

- `topic` is one of the 13 keys in `data/templates/topics.json`, or `science`, `history` or `nature`
  for topics that only appear in Academic passages. Every taxonomy key has at least 3 seeds.
- `id` is `<provider prefix>-<slug>`, for example `wiki-`, `owid-`, `arxiv-` or `gutenberg-`.
- Every `url` was checked to return HTTP 200 when the list was written. Wikipedia seeds use the
  canonical article title, not a redirect.

## Fetch cache

Running the backend with `--task=fetch-sources` refreshes `data/sources/cache/`, which is gitignored.
For each seed it stores the fetched text and its metadata (URL, licence, fetch time, HTTP status).
Generation takes its grounding facts from this cache.

The fetcher obeys each host's `robots.txt`, sends a descriptive `User-Agent`, sends requests one at a
time per host with a delay between them, and backs off on `429` and `5xx` responses.
