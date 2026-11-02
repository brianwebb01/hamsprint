# Foundations

A foundation is a small framework that several questions hang on: one
pattern, rule or table that makes a cluster of facts easier to remember
than learning each number on its own. Training shows a foundation just
before the first question that builds on it, then introduces that
foundation's questions one after another, so each new fact lands on
structure the learner already has.

Files live in `app/src/main/assets/foundations/{technician,general,extra}.json`.
File order is curriculum order: put the most basic frameworks first and
frameworks that build on others after them.

## Shape

```json
{
  "id": "g-power",
  "title": "Power limits",
  "summary": "One number covers nearly every band. Learn it, then the few exceptions.",
  "points": [
    { "label": "Default, every HF band", "value": "1500 watts PEP output", "q": ["G1C05", "G1C06"] },
    { "label": "30 meters", "value": "200 watts PEP output", "q": ["G1C01"] }
  ],
  "hook": "Think big by default (1500 W), small on the narrow 30 meter band (200 W).",
  "questions": ["G1C01", "G1C05", "G1C06"],
  "aid": { "kind": "spectrum", "bands": ["30m"] }
}
```

| Field | Rules |
|---|---|
| `id` | Unique in the file, lowercase kebab case, starting with the pool letter: `t-`, `g-`, `e-`. |
| `title` | 40 characters or fewer. Names the framework, not a question. |
| `summary` | 140 characters or fewer. The one idea that ties the points together. |
| `points` | 2 to 8 rows, in a meaningful order (ascending, by band, by layer...). `label` up to 28 characters, `value` up to 48. `q` lists the question(s) the row answers; every id must also be in `questions`. |
| `hook` | Optional, 160 characters or fewer. The pattern, mnemonic or contrast that makes the rows stick ("halve from 80: 80, 40, 20, then 15"). |
| `questions` | 3 or more question ids in the order they should be introduced, simplest first. A question belongs to at most one foundation. |
| figures | Not a field: a lesson automatically shows the pool figures its questions use, so rows like "Symbol 5" can refer to them. |
| `aid` | Optional. A visual aid without `q`, using the shapes in [VISUAL_AIDS.md](VISUAL_AIDS.md). Limited to `bandplan`, `spectrum`, `repeater`, `modes`, `ionosphere`, `codes`, `antenna`, `filter`, `logic`, `component`. |

## Accuracy rules (checked)

The question pool is the source of truth. Foundations reorganize what
the correct answers already say; they never add facts of their own.

- Every number in a point's `value` appears in the correct answer of one
  of that point's `q` questions (commas ignored, 1,500 equals 1500).
- Every number in a point's `label` appears in the stem or answers of one
  of its `q` questions, or names a real band ("30 meters", "70 cm").
- Every number in `summary` and `hook` appears in a point of the same
  foundation, or in the text of one of its questions, or is a small count
  from 1 to 12.
- No em or en dashes anywhere. Use commas, colons or "to".
- Frequencies follow the pool: MHz from 1 MHz up, kHz below it.

Text values (like "Lower sideband") are not machine checked, so write
them from the correct answers and keep the wording close to the pool's.

## What makes a good foundation

- It answers "how are these facts organized?" A rule with exceptions, a
  progression, a ranking, a table by band or by layer, a family of codes.
- It links questions whose answers the rows actually give. A learner who
  knows the rows should be able to answer every linked question.
- It is small: if it needs more than 8 rows, split it.
- Skip clusters where no pattern helps. Questions without a foundation
  are still trained, after the foundation clusters.

Run `python3 tools/foundations/validate.py` after editing. The unit test
`FoundationAssetsTest` enforces the same rules.
