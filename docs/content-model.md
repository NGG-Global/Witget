# Content model

How the Widget Pack Content Bible becomes runtime data, and how a response is
chosen.

---

## 1. The pipeline

```
docs/references/widget_pack_content_bible_v1.docx   ← source of truth, never edited
                 │
                 │  tools/content/extract_content.py
                 ▼
app/src/main/assets/content/*.json                  ← generated, committed
                 │
                 │  ContentRepository (parse once, cache for the process)
                 ▼
ContentDocument → pool(state, personality) → ResponseSelector → rendered text
```

Run the extractor after any change to the Bible:

```bash
python3 -m pip install python-docx
python3 tools/content/extract_content.py
./gradlew :app:testDebugUnitTest --tests '*ContentPackTest'
```

The extractor asserts the shape of every table it reads before writing anything,
so a re-ordered or restructured Bible fails loudly instead of producing plausible
nonsense. It also fails on an unknown personality mode, a missing mode within a
state, or a category it does not recognise.

`manifest.json` records the SHA-256 of the exact `.docx` the assets were built
from, so a drifted content pack is detectable.

---

## 2. What was extracted

| File | Contents | Count |
|---|---|---|
| `screentime.json` | 6 usage bands + equivalency framing, plus the 50-unit equivalency library | 140 responses, 50 units |
| `joke.json` | metadata/footer pool, the curated joke library, per-personality category weights | 40 responses, 120 jokes |
| `battery.json` | 8 discharge bands + 4 charging bands | 240 |
| `dayvibe.json` | 5 busy bands + 5 special overlays | 200 |
| `weather.json` | 11 conditions | 220 |
| `countdown.json` | 8 distance bands | 160 |
| `progress.json` | 5 percent bands + 4 scope alternates | 180 |
| `magic8ball.json` | positive / neutral / negative, 12 / 6 / 12 per personality | 150 |

**1 330 responses + 120 jokes + 50 equivalency units = 1 500 strings.**

> The Bible's checklist states a total of 1 450, but its own line items sum to
> 1 500. The line items are authoritative and are what shipped; the summary
> figure appears to be a transcription slip in the source document.

Every state carries a pool for all five personalities. Every pool is four
responses, except the 8 ball (12/6/12 per personality) and the joke metadata
line (8 per personality).

---

## 3. Schema

One file per widget, so a battery update parses ~50 KB rather than the whole
library:

```json
{
  "schemaVersion": 1,
  "widget": "BATTERY",
  "locale": "en",
  "states": [
    {
      "key": "BAT_B6",
      "label": "15-24%",
      "pools": [
        {
          "mode": "SARCASTIC",
          "responses": [
            { "id": "BAT_B6_SARCASTIC_01", "text": "{percent}% …", "vars": ["percent"] }
          ]
        }
      ]
    }
  ]
}
```

`vars` is precomputed at build time rather than parsed at runtime, and
`ContentPackTest` asserts it matches the text exactly — selection filters on it,
so a stale list would either hide a usable response or leak a placeholder.

### IDs

`WIDGET_STATE_MODE_INDEX`, the pattern the Bible itself prescribes:
`BAT_B6_SARCASTIC_03`. IDs are the anti-repeat currency and are asserted unique
across the whole pack. Changing an ID resets that string's history for every
user, so treat them as stable.

State keys keep the Bible's own tokens with `-` normalised to `_`:
`ST-B1`→`ST_B1`, `BAT-CH1`→`BAT_CH1`, `DAY-S3`→`DAY_S3`, `TP-S4`→`TP_S4`,
`WX1`, `CD5`. The one rename is `8B-Positive`→`M8_POSITIVE`, because an
identifier cannot begin with a digit.

### Motifs

Every joke carries `glyph` (a `MotifGlyph` name) and `colour` (a `ColourRole`
name the tile retints to), and every equivalency unit carries `glyph`. These
are *curated*, not derived: the libraries are fixed at 120 and 50 entries, so
each assignment lives in the extractor's `JOKE_MOTIFS` and `UNIT_GLYPHS`
tables, reviewed against the actual text — J076 "blue paint" is
`PAINT`/`SLATE`, J116's toaster is `TOAST`/`CLAY`, ST01's trilogy is `FILM`.
Nothing matches keywords at runtime, so the pairing can never surprise.

Adding a joke or unit to the Bible without adding its motif fails the
extractor; shipping a name that does not exist in the Kotlin enums fails
`ContentPackTest`. Changing a motif does not touch response IDs, so it never
resets anti-repeat history.

---

## 4. Trigger logic

Every threshold below is transcribed from the Bible. All of them are tested from
both sides of each boundary.

**Battery** — charging wins over every discharge band.
Charging: 0–20, 21–60, 61–90, 91–100.
Discharging: 95–100, 80–94, 60–79, 40–59, 25–39, 15–24, 6–14, 1–5.
0% folds into the lowest band, which the Bible does not define.

**Screen Time** — bands `<30`, `30–89`, `90–179`, `180–299`, `300–479`, `≥480`
minutes. Equivalency ratio prefers 0.5–12.0, widening to 0.2–25.0. Formatting:
percentage below 1, one decimal from 1 to 9.9, rounded integer from 10.

**Day Vibe** —
`score = min(100, booked/6 + meetings×5 + backToBack×8 + early + late)`, with
+8 for a first event before 08:00 and +8 for an event ending after 20:00. Bands:
0–19, 20–39, 40–59, 60–79, 80–100. One special overlay may replace the band
response, ordered so the most disruptive fact wins: 3+ back-to-back, no lunch
gap, early start, late finish, tasks-only.

> The Bible names "back-to-back" without defining it numerically. The
> implementation counts an adjacency when the next event starts no more than
> **five minutes** after the previous one ends — the tightest reading that still
> tolerates calendars which pad meetings.

**Weather** — priority order, which is deliberately *not* the state numbering:

| Priority | Condition | State | Threshold |
|---|---|---|---|
| 1 | Extreme heat | `WX1` | temp ≥ 38 or feels ≥ 40 |
| 2 | Thunder / heavy rain | `WX4` | WMO 95–99, 65, 67, 82 |
| 3 | Very cold | `WX6` | temp ≤ 3 |
| 4 | High UV | `WX3` | UV ≥ 8 |
| 5 | Rain | `WX5` | WMO drizzle/rain/snow codes |
| 6 | Strong wind | `WX8` | sustained ≥ 35 or gust ≥ 50 km/h |
| 7 | Hot | `WX2` | 32–37 |
| 8 | Cold | `WX7` | 4–10 |
| 9 | Humid | `WX9` | feels ≥ temp + 4 **and** temp ≥ 24 |
| 10 | Pleasant | `WX10` | 18–26, rain < 20%, wind < 25 |
| 11 | Cloudy | `WX11` | fallback |

The four safety-relevant conditions are flagged, and the plain condition label
is pinned beside the temperature for them — the Bible is firm that humour may
follow, never replace, a heat, storm, cold or UV warning.

**Countdown** — bands `<1h`, `1–23h`, `1–2d`, `3–6d`, `1–3w`, `1–3mo`,
`3–12mo`, `≥1y`. Days are counted as whole **calendar** days between local
dates, which is why 23:00→08:00 reads as "tomorrow". Units:
`sleeps = ceil(hours/24)` (minimum 1), weeks and weekends `/7`, months `/30.44`,
years `/365.2425`.

**Time Progress** — `(now − start) / (end − start) × 100` in the local zone,
clamped to 99.9 before rollover. Bands 0–14, 15–34, 35–64, 65–84, 85–99. Period
boundaries are half-open, so at the boundary the reading flips to 0% of the new
period rather than lingering at 100% of the old. On medium and large tiles there
is a 35% chance of the scope-specific alternate pool, seeded from the period so
it does not flicker between redraws.

**Daily Joke** — category weighting per personality (Chaotic is 45% Absurd,
Dry is 40% Anti-joke, and so on). The category is drawn first, then the joke,
which is what makes the Bible's percentages actually hold regardless of how many
jokes each category happens to contain.

**Magic 8 Ball** — 40% positive, 20% neutral, 40% negative. Outcomes never
depend on question text; the widget stores no question.

---

## 5. Variables

Every variable the Bible defines is implemented, and `ContentPackTest` fails on
any variable in a response that is not in this set:

```
percent  remaining_percent  minutes  hours  hours_short
ratio  equivalent
temp  feels  uv  wind  rain_chance
event  event_upper  days  sleeps  weeks  weekends  months  years
period  period_upper
first_time  late_time  meeting_count  booked_hours  free_hours
score  day_of_year  category
```

Two guarantees:

1. A response whose required variables cannot **all** resolve is filtered out
   before selection. It is never rendered with a gap.
2. `Interpolation.interpolate` strips anything that somehow survives and tidies
   the surrounding whitespace and punctuation, so the worst case is a slightly
   terser sentence rather than a visible `{percent}`.

There is a test asserting no rendered string ever contains a brace.

---

## 6. Anti-repetition

One engine, `ResponseSelector`, used by all eight widgets. Rules are **data**,
not branches: `AntiRepeatPolicy` with the concrete per-pool values in
`AntiRepeatPolicies`, transcribed from the Bible's global policy table.

| Pool | Rule | Policy |
|---|---|---|
| Status widgets (battery) | exclude the last 4 IDs per trigger/state | `recentCount = 4` |
| Daily Joke | 90-day hard cooldown, prefer unseen until 80% of the pool is seen | `cooldownDays = 90`, `preferUnseenUntilCoverage = 0.8` |
| Screen Time commentary | 5-day cooldown | `cooldownDays = 5` |
| Screen Time equivalency unit | 7-day cooldown | `cooldownDays = 7` |
| Weather | 3-day cooldown per condition | `cooldownDays = 3` |
| Day Vibe | 5 days; special overlays 3 | two policies |
| Countdown | no repeat on consecutive updates; one new line per day | `recentCount = 1`, `cooldownDays = 1` |
| Time Progress | no repeat in adjacent periods | `excludeAdjacentPeriods = true` |
| Magic 8 Ball | exclude the previous 12 answers | `recentCount = 12` |

### The filter chain

1. **Resolvable** — drop responses whose variables cannot resolve.
2. **Copy budget** — drop responses that overflow the breakpoint's character
   budget. If nothing fits, fall back to the *shortest* available. Never
   truncate: the Bible requires selecting a shorter eligible string rather than
   cutting a punchline.
3. **Anti-repeat** — apply adjacency, cooldown and recency.
4. **Coverage** — prefer never-seen responses while below the coverage threshold.

Each step relaxes in reverse order if it would leave nothing, which is the
Bible's *"reset history only if the eligible pool is exhausted"*.

### Determinism

"Daily" content is stable because the chosen ID is **recorded against a
`periodKey`** and reused for the rest of that period — not because the seed
happens to be deterministic. Refreshing a Daily Joke widget twenty times returns
the same joke; the next local day draws a new one. The seed combines the local
date with the widget instance key, so two Daily Joke widgets on one home screen
show different jokes.

Interactive content (the 8 ball) is genuinely random, bounded only by the
12-answer exclusion.

### Storage

History is per **widget instance**, per **pool**, stored as one JSON record per
instance in DataStore — so a widget's entire footprint is two keys, deleting it
is a two-key removal, and one update performs one history read and one write.
Entries are bounded by `maxHistoryEntries`, so history cannot grow without limit.

`ContentSession` batches an update's selections, and later selections in the same
update see earlier ones — which is what stops a widget rendered at two
breakpoints in one update from showing the same string twice.

---

## 7. Adding content or a personality

**More responses:** add rows to the Bible, re-run the extractor, run
`ContentPackTest`. No Kotlin changes.

**A sixth personality:** add its column to every response table in the Bible, add
one entry to the `Personality` enum, add its key to the extractor's `MODES` list
and its weighting to `JOKE_MODE_WEIGHTS`. Nothing in the widgets, the selector or
the renderer needs touching — pools are looked up by mode key, and a content pack
that predates a new mode falls back to Neutral rather than rendering nothing.

**Another language:** the schema already carries `locale`. Emit
`content/<locale>/*.json`, have `ContentRepository` resolve the device locale
with an English fallback, and translate intent rather than words — the Bible's
own instruction, since sarcasm does not survive literal translation.
