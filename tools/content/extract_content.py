#!/usr/bin/env python3
"""Generate the runtime content pack from the Widget Pack Content Bible.

The Content Bible (docs/references/widget_pack_content_bible_v1.docx) is the
source of truth for every user-visible response string in the app. This script
reads it and emits the machine-readable JSON assets under
app/src/main/assets/content/, which the Kotlin content engine loads at runtime.

The Bible itself is never modified. Re-run this script after the Bible is
updated:

    python3 tools/content/extract_content.py

Response IDs follow the pattern the Bible prescribes in section 11:
WIDGET_STATE_MODE_INDEX, for example BAT_B6_SARCASTIC_03.
"""

from __future__ import annotations

import hashlib
import json
import re
import sys
from collections import OrderedDict
from pathlib import Path

try:
    import docx
except ImportError:  # pragma: no cover - developer tooling guard
    sys.exit("python-docx is required: python3 -m pip install python-docx")

REPO_ROOT = Path(__file__).resolve().parents[2]
BIBLE = REPO_ROOT / "docs" / "references" / "widget_pack_content_bible_v1.docx"
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "content"

SCHEMA_VERSION = 1
LOCALE = "en"

MODES = ["NEUTRAL", "FRIENDLY", "DRY", "SARCASTIC", "CHAOTIC"]

# Bible trigger token -> stable state key used in response IDs and Kotlin enums.
STATE_KEY_OVERRIDES = {
    "8B-Positive": "M8_POSITIVE",
    "8B-Neutral": "M8_NEUTRAL",
    "8B-Negative": "M8_NEGATIVE",
}

JOKE_CATEGORY_KEYS = {
    "Dad / wordplay": "DAD_WORDPLAY",
    "Nerd / tech": "NERD_TECH",
    "Work / life": "WORK_LIFE",
    "Anti-joke / dry": "ANTI_JOKE",
    "Absurd": "ABSURD",
}

EQUIVALENCY_CATEGORY_KEYS = {
    "Pop culture": "POP_CULTURE",
    "Entertainment": "ENTERTAINMENT",
    "Life": "LIFE",
    "Productivity": "PRODUCTIVITY",
    "Absurd": "ABSURD",
    "Audio": "AUDIO",
    "Reading": "READING",
    "Social": "SOCIAL",
    "Gaming": "GAMING",
}

# Category weighting per personality mode, from Content Bible section 4.
JOKE_MODE_WEIGHTS = {
    "NEUTRAL": {"DAD_WORDPLAY": 20, "NERD_TECH": 20, "WORK_LIFE": 20, "ANTI_JOKE": 20, "ABSURD": 20},
    "FRIENDLY": {"DAD_WORDPLAY": 30, "NERD_TECH": 15, "WORK_LIFE": 15, "ANTI_JOKE": 10, "ABSURD": 30},
    "DRY": {"DAD_WORDPLAY": 10, "NERD_TECH": 20, "WORK_LIFE": 20, "ANTI_JOKE": 40, "ABSURD": 10},
    "SARCASTIC": {"DAD_WORDPLAY": 10, "NERD_TECH": 20, "WORK_LIFE": 30, "ANTI_JOKE": 30, "ABSURD": 10},
    "CHAOTIC": {"DAD_WORDPLAY": 10, "NERD_TECH": 25, "WORK_LIFE": 10, "ANTI_JOKE": 10, "ABSURD": 45},
}

VARIABLE_RE = re.compile(r"\{([a-z_]+)\}")
NUMBERED_RE = re.compile(r"^\s*(\d+)\.\s*")


def state_key(trigger: str) -> str:
    """Normalise a Bible trigger token such as 'BAT-CH1' into a stable key."""
    token = trigger.split("•")[0].strip()
    if token in STATE_KEY_OVERRIDES:
        return STATE_KEY_OVERRIDES[token]
    return token.replace("-", "_").upper()


def state_label(trigger: str) -> str:
    parts = trigger.split("•", 1)
    if len(parts) == 2:
        return parts[1].strip()
    return parts[0].strip()


def split_pool(cell_text: str) -> list[str]:
    """Split a '1. a\n2. b' response-pool cell into its individual strings."""
    items: list[str] = []
    for raw in cell_text.replace("\r", "\n").split("\n"):
        line = raw.strip()
        if not line:
            continue
        match = NUMBERED_RE.match(line)
        if match:
            items.append(NUMBERED_RE.sub("", line, count=1).strip())
        elif items:
            # Continuation of the previous entry (defensive; not seen in V1).
            items[-1] = f"{items[-1]} {line}"
        else:
            items.append(line)
    return items


def table_rows(table) -> list[list[str]]:
    return [[c.text.strip() for c in row.cells] for row in table.rows]


def parse_pool_table(table, expected_per_pool: dict[str, int] | None = None) -> list[dict]:
    """Parse a 'Trigger / state | Mode | Response pool' table into state records."""
    states: "OrderedDict[str, dict]" = OrderedDict()
    for row in table_rows(table)[1:]:
        trigger, mode_name, pool_cell = row[0], row[1], row[2]
        if not trigger or not mode_name:
            continue
        mode = mode_name.strip().upper()
        if mode not in MODES:
            raise ValueError(f"Unknown personality mode {mode_name!r} in row {row!r}")
        key = state_key(trigger)
        record = states.setdefault(key, {"key": key, "label": state_label(trigger), "pools": []})
        texts = split_pool(pool_cell)
        if expected_per_pool is not None:
            expected = expected_per_pool.get(key)
            if expected is not None and len(texts) != expected:
                raise ValueError(f"{key}/{mode}: expected {expected} responses, found {len(texts)}")
        responses = []
        for index, text in enumerate(texts, start=1):
            responses.append(
                {
                    "id": f"{key}_{mode}_{index:02d}",
                    "text": text,
                    "vars": sorted(set(VARIABLE_RE.findall(text))),
                }
            )
        record["pools"].append({"mode": mode, "responses": responses})
    for key, record in states.items():
        modes_present = [p["mode"] for p in record["pools"]]
        missing = [m for m in MODES if m not in modes_present]
        if missing:
            raise ValueError(f"State {key} is missing personality modes: {missing}")
        record["pools"].sort(key=lambda p: MODES.index(p["mode"]))
    return list(states.values())


def envelope(widget: str, states: list[dict], **extra) -> dict:
    doc = {"schemaVersion": SCHEMA_VERSION, "widget": widget, "locale": LOCALE, "states": states}
    doc.update(extra)
    return doc


def write(name: str, payload: dict) -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return count_strings(payload)


def count_strings(payload: dict) -> int:
    total = 0
    for state in payload.get("states", []):
        for pool in state["pools"]:
            total += len(pool["responses"])
    return total


def parse_equivalencies(table) -> list[dict]:
    units = []
    for row in table_rows(table)[1:]:
        unit_id, category, minutes, label = row[0], row[1], row[2], row[3]
        if not unit_id:
            continue
        if category not in EQUIVALENCY_CATEGORY_KEYS:
            raise ValueError(f"Unknown equivalency category {category!r}")
        units.append(
            {
                "id": unit_id,
                "category": EQUIVALENCY_CATEGORY_KEYS[category],
                "minutes": float(minutes),
                "label": label,
            }
        )
    return units


def parse_jokes(table) -> list[dict]:
    jokes = []
    for row in table_rows(table)[1:]:
        joke_id, category, text = row[0], row[1], row[2]
        if not joke_id:
            continue
        if category not in JOKE_CATEGORY_KEYS:
            raise ValueError(f"Unknown joke category {category!r}")
        jokes.append({"id": joke_id, "category": JOKE_CATEGORY_KEYS[category], "text": text})
    return jokes


def main() -> int:
    if not BIBLE.exists():
        sys.exit(f"Content Bible not found at {BIBLE}")
    document = docx.Document(str(BIBLE))
    tables = document.tables

    # Table indices are positional in the Bible; assert the shape we expect so a
    # re-ordered document fails loudly instead of producing silent nonsense.
    expected_shapes = {2: (51, 4), 3: (31, 3), 4: (6, 3), 6: (121, 3), 7: (6, 3),
                       9: (61, 3), 11: (51, 3), 13: (56, 3), 15: (41, 3), 17: (46, 3), 19: (16, 3)}
    for index, (rows, cols) in expected_shapes.items():
        actual = (len(tables[index].rows), len(tables[index].columns))
        if actual != (rows, cols):
            sys.exit(f"Content Bible table {index + 1} shape changed: expected {(rows, cols)}, got {actual}")

    totals: "OrderedDict[str, int]" = OrderedDict()

    units = parse_equivalencies(tables[2])
    screentime_states = parse_pool_table(tables[3]) + parse_pool_table(tables[4])
    totals["screentime.json"] = write(
        "screentime.json",
        envelope("SCREEN_TIME", screentime_states, equivalencyUnits=units),
    )

    jokes = parse_jokes(tables[6])
    joke_states = parse_pool_table(tables[7])
    totals["joke.json"] = write(
        "joke.json",
        envelope(
            "DAILY_JOKE",
            joke_states,
            jokes=jokes,
            categoryWeights=[
                {"mode": mode, "weights": [{"category": c, "weight": w} for c, w in JOKE_MODE_WEIGHTS[mode].items()]}
                for mode in MODES
            ],
        ),
    )

    totals["battery.json"] = write("battery.json", envelope("BATTERY", parse_pool_table(tables[9])))
    totals["dayvibe.json"] = write("dayvibe.json", envelope("DAY_VIBE", parse_pool_table(tables[11])))
    totals["weather.json"] = write("weather.json", envelope("WEATHER", parse_pool_table(tables[13])))
    totals["countdown.json"] = write("countdown.json", envelope("COUNTDOWN", parse_pool_table(tables[15])))
    totals["progress.json"] = write("progress.json", envelope("TIME_PROGRESS", parse_pool_table(tables[17])))
    totals["magic8ball.json"] = write("magic8ball.json", envelope("MAGIC_8_BALL", parse_pool_table(tables[19])))

    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "locale": LOCALE,
        "source": {
            "document": BIBLE.name,
            "sha256": hashlib.sha256(BIBLE.read_bytes()).hexdigest(),
        },
        "generator": "tools/content/extract_content.py",
        "files": [{"name": name, "responseCount": count} for name, count in totals.items()],
        "equivalencyUnitCount": len(units),
        "jokeCount": len(jokes),
        "totalResponseCount": sum(totals.values()),
    }
    OUT_DIR.joinpath("manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    for name, count in totals.items():
        print(f"{name:20s} {count:4d} responses")
    print(f"{'equivalency units':20s} {len(units):4d}")
    print(f"{'jokes':20s} {len(jokes):4d}")
    print(f"{'TOTAL strings':20s} {sum(totals.values()) + len(units) + len(jokes):4d}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
