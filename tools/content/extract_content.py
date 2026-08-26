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

# ---------------------------------------------------------------------------
# Motifs: a hand-curated visual for every joke and every equivalency unit.
#
# The pack renders these as flat geometric pictograms on the tile (the glyph)
# and, for jokes, may retint the tile's field to a palette role (the colour) —
# a joke about blue paint gets a slate tile, the toaster joke gets a toast
# glyph. Curated per ID rather than keyword-matched at runtime: the libraries
# are fixed, so every assignment below was reviewed against the actual text.
# Glyph names must exist in MotifGlyph (Kotlin); colours are ColourRole names.
# ContentPackTest enforces both.
# ---------------------------------------------------------------------------

JOKE_MOTIFS = {
    "J001": ("CALENDAR", "SLATE"), "J002": ("BROOM", "SAGE"), "J003": ("PLATE", "EMBER"),
    "J004": ("ARROW", "SLATE"), "J005": ("WIFI", "SLATE"), "J006": ("CHAIR", "SAGE"),
    "J007": ("BROOM", "AMBER"), "J008": ("TOAST", "AMBER"), "J009": ("BULB", "AMBER"),
    "J010": ("FRIDGE", "SLATE"), "J011": ("BOOK", "EMBER"), "J012": ("SOCK", "SLATE"),
    "J013": ("SPINNER", "SLATE"), "J014": ("MIRROR", "SLATE"), "J015": ("DOC", "AMBER"),
    "J016": ("PENCIL", "AMBER"), "J017": ("CLOCK", "NIGHT"), "J018": ("KETTLE", "EMBER"),
    "J019": ("DROP", "NIGHT"), "J020": ("UMBRELLA", "SLATE"), "J021": ("CLOCK", "EMBER"),
    "J022": ("BOOK", "SAGE"), "J023": ("BATTERY", "SAGE"), "J024": ("PHONE", "NIGHT"),
    "J025": ("LAPTOP", "SLATE"), "J026": ("LOCK", "NIGHT"), "J027": ("BUG", "SAGE"),
    "J028": ("LAPTOP", "NIGHT"), "J029": ("CLOUD", "SLATE"), "J030": ("WIFI", "NIGHT"),
    "J031": ("SPINNER", "SLATE"), "J032": ("BUG", "NIGHT"), "J033": ("GRID", "SLATE"),
    "J034": ("BULB", "SLATE"), "J035": ("LAPTOP", "EMBER"), "J036": ("PENCIL", "SLATE"),
    "J037": ("GRID", "EMBER"), "J038": ("GRID", "SAGE"), "J039": ("PLANE", "SLATE"),
    "J040": ("WIFI", "EMBER"), "J041": ("SPINNER", "AMBER"), "J042": ("BRANCH", "SAGE"),
    "J043": ("LOCK", "SLATE"), "J044": ("LAPTOP", "CLAY"), "J045": ("HOUSE", "SAGE"),
    "J046": ("CLOUD", "SAGE"), "J047": ("ALERT", "CLAY"), "J048": ("BATTERY", "SAGE"),
    "J049": ("DOC", "AMBER"), "J050": ("DOC", "SAGE"), "J051": ("MAIL", "SLATE"),
    "J052": ("SPINNER", "NIGHT"), "J053": ("BROOM", "SLATE"), "J054": ("CALENDAR", "EMBER"),
    "J055": ("MAIL", "CLAY"), "J056": ("CLOCK", "AMBER"), "J057": ("CALENDAR", "CLAY"),
    "J058": ("MAIL", "SAGE"), "J059": ("SPINNER", "EMBER"), "J060": ("DOC", "EMBER"),
    "J061": ("CLOCK", "SAGE"), "J062": ("CUP", "EMBER"), "J063": ("BULB", "CLAY"),
    "J064": ("CLOCK", "SLATE"), "J065": ("CALENDAR", "AMBER"), "J066": ("GRID", "SAGE"),
    "J067": ("CLOCK", "EMBER"), "J068": ("MOON", "SLATE"), "J069": ("CALENDAR", "SAGE"),
    "J070": ("PLATE", "AMBER"), "J071": ("BELL", "AMBER"), "J072": ("MOON", "SLATE"),
    "J073": ("DROP", "SLATE"), "J074": ("BIRD", "EMBER"), "J075": ("HOUSE", "AMBER"),
    "J076": ("PAINT", "SLATE"),  # blue paint: the canonical example — a blue tile.
    "J077": ("BOX", "EMBER"), "J078": ("DOOR", "SAGE"), "J079": ("BONE", "AMBER"),
    "J080": ("BOOK", "SLATE"), "J081": ("DOOR", "EMBER"), "J082": ("PHONE", "SAGE"),
    "J083": ("PLANT", "SAGE"), "J084": ("BELL", "SAGE"), "J085": ("DOC", "NIGHT"),
    "J086": ("BOX", "AMBER"), "J087": ("BELL", "NIGHT"), "J088": ("FRIDGE", "SAGE"),
    "J089": ("SUN", "AMBER"), "J090": ("SPINNER", "EMBER"), "J091": ("DOC", "SLATE"),
    "J092": ("MIRROR", "AMBER"), "J093": ("BONE", "NIGHT"), "J094": ("ARROW", "SLATE"),
    "J095": ("PHONE", "EMBER"), "J096": ("CLOCK", "SLATE"), "J097": ("BIRD", "SLATE"),
    "J098": ("SOCK", "CLAY"), "J099": ("MAIL", "NIGHT"), "J100": ("MOON", "NIGHT"),
    "J101": ("WIFI", "EMBER"), "J102": ("PLANT", "SAGE"), "J103": ("SPOON", "NIGHT"),
    "J104": ("MIRROR", "EMBER"), "J105": ("MICRO", "CLAY"), "J106": ("LAUNDRY", "SLATE"),
    "J107": ("DOOR", "SLATE"), "J108": ("POTATO", "EMBER"), "J109": ("CHAIR", "NIGHT"),
    "J110": ("DOOR", "AMBER"), "J111": ("PHONE", "SLATE"), "J112": ("HOUSE", "NIGHT"),
    "J113": ("FRIDGE", "NIGHT"), "J114": ("CLOCK", "CLAY"), "J115": ("SOCK", "SAGE"),
    "J116": ("TOAST", "CLAY"),  # the toaster: the other canonical example.
    "J117": ("CLOUD", "AMBER"), "J118": ("DOOR", "NIGHT"), "J119": ("BOX", "SLATE"),
    "J120": ("DOC", "EMBER"),
}

UNIT_GLYPHS = {
    "ST01": "FILM", "ST02": "FILM", "ST03": "FILM", "ST04": "TV", "ST05": "TV",
    "ST06": "FILM", "ST07": "FILM", "ST08": "TV", "ST09": "FILM", "ST10": "BALL",
    "ST11": "BRIEFCASE", "ST12": "DUMBBELL", "ST13": "MOON", "ST14": "MOON",
    "ST15": "BUS", "ST16": "LAUNDRY", "ST17": "PLATE", "ST18": "DROP", "ST19": "CUP",
    "ST20": "WALK", "ST21": "TOMATO", "ST22": "TOMATO", "ST23": "MAIL", "ST24": "BULB",
    "ST25": "BROOM", "ST26": "SPEECH", "ST27": "BOOK", "ST28": "BOOK", "ST29": "CUP",
    "ST30": "MICRO", "ST31": "TOAST", "ST32": "FRIDGE", "ST33": "SPIRAL",
    "ST34": "MAGNIFIER", "ST35": "BELL", "ST36": "BROOM", "ST37": "TV", "ST38": "PLATE",
    "ST39": "NOTE", "ST40": "MIC", "ST41": "NOTE", "ST42": "NOTE", "ST43": "DOC",
    "ST44": "DOC", "ST45": "BOOK", "ST46": "BOOK", "ST47": "PHONE", "ST48": "PHONE",
    "ST49": "GAMEPAD", "ST50": "GAMEPAD",
}

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
        if unit_id not in UNIT_GLYPHS:
            raise ValueError(f"Equivalency unit {unit_id} has no motif glyph")
        units.append(
            {
                "id": unit_id,
                "category": EQUIVALENCY_CATEGORY_KEYS[category],
                "minutes": float(minutes),
                "label": label,
                "glyph": UNIT_GLYPHS[unit_id],
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
        if joke_id not in JOKE_MOTIFS:
            raise ValueError(f"Joke {joke_id} has no motif")
        glyph, colour = JOKE_MOTIFS[joke_id]
        jokes.append(
            {
                "id": joke_id,
                "category": JOKE_CATEGORY_KEYS[category],
                "text": text,
                "glyph": glyph,
                "colour": colour,
            }
        )
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
