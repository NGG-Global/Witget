# witget — a personality-driven Android widget pack

> Your phone already has the data. These widgets give it a personality.

Eight native Android home-screen widgets that interpret the information your
phone already holds, instead of just displaying it. The widget design system is
codenamed **Soft Dread**; the product brand is **witget**, from the logo in
`docs/references/witget_logo.png`. Screen time becomes
*"0.7 Lord of the Rings trilogies"*. Eighteen per cent battery becomes
*"optimism is no longer appropriate"*. The number you actually need never
disappears — the joke sits beside it, never on top of it.

---

## Contents

- [What is in the pack](#what-is-in-the-pack)
- [Design](#design)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Android requirements](#android-requirements)
- [Setup and build](#setup-and-build)
- [Permissions](#permissions)
- [Weather provider](#weather-provider)
- [Privacy](#privacy)
- [Offline behaviour](#offline-behaviour)
- [Accessibility](#accessibility)
- [Testing](#testing)
- [Reference materials](#reference-materials)
- [Known limitations](#known-limitations)
- [Next steps](#next-steps)

---

## What is in the pack

| # | Widget | Colour | Reads | Needs |
|---|--------|--------|-------|-------|
| 01 | **Screen Time Translator** | clay `#C65B3C` | `UsageStatsManager` | Usage Access |
| 02 | **Daily Joke** | amber `#F0B23F` | bundled 120-joke library | nothing |
| 03 | **Battery Prognosis** | sage `#7F9C7A` | `ACTION_BATTERY_CHANGED` | nothing |
| 04 | **Day Vibe** | cream `#F2E7D8` | Calendar Provider | `READ_CALENDAR` |
| 05 | **Weather, Translated** | ember `#E0762E` | Open-Meteo | a city, or coarse location |
| 06 | **Countdown in Perspective** | slate `#5F7FA8` | its own configuration | a title and a date |
| 07 | **Time Progress** | ink `#3B3128` | the clock | nothing |
| 08 | **Magic 8 Ball** | night `#34435C` | a tap | nothing |

Every widget supports five personality modes — **Neutral, Friendly, Dry,
Sarcastic, Chaotic** — set globally and overridable per placed widget. Each
widget can be placed more than once, and every instance keeps its own
configuration and its own anti-repeat history: a Japan countdown and a birthday
countdown, or a sarcastic 8 ball beside a chaotic one.

Settings chosen in the gallery before a widget is placed are kept as a per-type
template that new instances inherit, so nothing you set there is discarded.
Editing a widget that is already on the home screen changes only that one.

Widgets adapt their content, not just their scale, across six breakpoints —
2×1, 2×2, 4×2, 5×2, 4×4 and a tablet hero canvas (≥520×300dp). A compact
battery tile shows the percentage and one line; the large ones add the charge
ring, the estimated remaining time and a copy pill; on tablets the tile gains
copy and breathing room rather than magnified glyphs. The app itself is
window-size aware: phones get bottom tabs, tablets get a left rail, a
list-detail gallery and side-by-side onboarding.

### Screenshots and previews

No rendered screenshots are committed. The most accurate preview of the design
is the reference sheet itself, which renders in any browser:

```
docs/references/Soft_Dread_Design_Sheet.html
```

Inside the app, the gallery renders every tile through the real widget pipeline
using the bundled typeface, so it is a live preview rather than a mock-up.

---

## Design

The visual language comes from `docs/references/Soft_Dread_Design_Sheet.html` and
is transcribed into native tokens in `app/src/main/kotlin/.../design/`. The full
mapping, including every adaptation Glance forced, is in
[`docs/design-system.md`](docs/design-system.md).

The four rules the sheet says must not break, and how they are enforced:

| Rule | Where it lives |
|------|----------------|
| One colour per widget, never changing across sizes, states or modes | `WidgetType.colourRole` → `SoftDreadTiles.colours()` |
| Exactly one oversized circle per tile, cropped by at least one edge; optional single satellite | `TileGeometry` → `TileArt.background()` |
| Copy sits in a cream pill on 4×4 only | `TileContent.pill`, rendered only by the expanded layout |
| Lowercase everywhere except micro-labels | `SoftDreadTile` uppercases the micro-label and nothing else |

---

## Architecture

Pragmatic layering, no framework ceremony:

```
data/      content pack loading, DataStore persistence, device and network sources
domain/    pure Kotlin — models, band logic, selection engine, formatting
ui/        Compose app: gallery, detail, settings, onboarding, configuration
widgets/   Glance widgets, the shared tile renderer, and the bitmap art
work/      WorkManager rollover and refresh scheduling
```

Four decisions worth knowing about:

**One tile renderer, eight widgets.** Every widget produces a `TileContent` — a
size-independent description of what to show. `SoftDreadTile` (Glance) and
`PreviewTile` (Compose) both render it. Responsive behaviour is therefore a
content decision made once, not five hand-built layouts per widget, and the
in-app preview cannot drift from the placed widget.

**One selection engine.** `ResponseSelector` is the only place a response is
chosen. Anti-repeat rules are data (`AntiRepeatPolicy`), not `when` branches
scattered across widgets. See [`docs/content-model.md`](docs/content-model.md).

**Previews run the real pipeline.** `WidgetPreviewer` builds a `WidgetEnvironment`
with a throwaway history session and calls the widget's own `render()`. Browsing
the gallery never consumes a widget's anti-repeat pool.

**Content is generated, not typed.** All 1 500 strings are extracted from the
Content Bible by a script into versioned JSON assets. Nothing is hardcoded in a
composable.

---

## Project structure

```
app/src/main/
  assets/content/          generated content pack (9 JSON files, ~316 KB)
  kotlin/com/softdread/widgets/
    core/time/             injectable Clock, so every date calculation is testable
    data/
      content/             content pack models, asset loading, in-memory cache
      device/              battery, usage stats, calendar
      prefs/               DataStore: global prefs, per-instance config, history
      weather/             provider abstraction, Open-Meteo, cache, geocoding
    design/                palette, typography, theme, shape and spacing tokens
    domain/
      logic/               the eight band/threshold calculators + formatting
      model/               Personality, WidgetType, WidgetBreakpoint
      selection/           interpolation, anti-repeat policies, ResponseSelector
    ui/                    gallery, detail, settings, onboarding, configuration
    widgets/
      common/              TileArt, TileGeometry, SoftDreadTile, base widget
      <eight widget packages>
    work/                  refresh scheduling and the midnight rollover
  res/font/                Bricolage Grotesque (variable) + IBM Plex Mono
app/src/test/              147 JVM and Robolectric unit tests
app/src/androidTest/       Compose and on-device pipeline tests
docs/
  design-system.md         design token mapping and Glance adaptations
  content-model.md         how the content pack is derived and how selection works
  references/              the original design sheet and Content Bible, unmodified
tools/content/             the Content Bible → JSON extractor
```

---

## Android requirements

| | |
|---|---|
| **min SDK** | 26 (Android 8.0) |
| **target SDK** | 37 |
| **compile SDK** | 37 |
| **Kotlin** | 2.2.21 |
| **AGP** | 8.13.2 |
| **Gradle** | 8.14.3 |
| **JDK** | 17 |

Major AndroidX versions: Glance `1.1.1`, Compose BOM `2025.09.01`,
DataStore `1.1.7`, WorkManager `2.10.5`, Navigation Compose `2.9.5`,
Lifecycle `2.9.4`, Core KTX `1.17.0`, kotlinx.serialization `1.9.0`.

Glance is pinned to **1.1.1**, the current stable release — `1.2.0` never
progressed past `rc01` and `1.3.0` is alpha. Nothing in the pack needs an API
added after 1.1.1.

---

## Setup and build

```bash
git clone <this repo> && cd Witget

# Point the build at your SDK (or set ANDROID_HOME / ANDROID_SDK_ROOT)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # 149 unit tests
./gradlew :app:lintDebug            # Android lint
./gradlew :app:assembleRelease      # minified release APK (unsigned by default)
```

A clean checkout builds with no further configuration: there is no API key, no
`secrets.properties`, and no account to create.

### Release signing

Release builds are unsigned unless you create `keystore.properties` at the
repository root (git-ignored):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

If the file is absent the release build still succeeds, unsigned. No credential
is ever read from source control or from the environment.

### Regenerating the content pack

The generated assets are committed, so this is only needed after editing the
Content Bible:

```bash
python3 -m pip install python-docx
python3 tools/content/extract_content.py
./gradlew :app:testDebugUnitTest --tests '*ContentPackTest'
```

The extractor asserts the Bible's table shapes before it writes anything, and
`ContentPackTest` validates the result.

---

## Permissions

Nothing is requested at first launch. The user can browse the whole gallery,
read what each widget does and see what it needs before granting anything. Each
permission is explained at the point of use, on the detail screen of the widget
that needs it, and denying one affects only that widget.

| Permission | Widget | Behaviour if denied |
|---|---|---|
| `PACKAGE_USAGE_STATS` (Usage Access, granted in Settings) | Screen Time | The tile says "usage access needed" and deep-links to Settings. It never shows a zero. |
| `READ_CALENDAR` | Day Vibe | The tile says "calendar access needed". Read-only; the pack never writes to a calendar. |
| `ACCESS_COARSE_LOCATION` | Weather | Optional. Naming a city gives full weather with no location permission at all. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Weather | Only the weather widget uses the network. |
| `RECEIVE_BOOT_COMPLETED` | scheduling | Re-arms the midnight rollover after a reboot. |

Usage Access cannot be granted by a runtime dialog. The app detects the real
state via `AppOpsManager`, deep-links to the per-app Settings screen (falling
back to the general list on OEM builds that do not implement it) and re-checks
on return.

---

## Weather provider

V1 uses **[Open-Meteo](https://open-meteo.com)**. It was chosen because it needs
no API key, no account and no payment during development, and its free tier is
licensed CC BY 4.0 for non-commercial use — so there is no secret to leak and
nothing to configure before the app builds. Attribution is shown in the app's
settings screen: *"Weather data by Open-Meteo.com (CC BY 4.0)"*. Commercial
distribution would require checking Open-Meteo's current commercial terms.

Widgets depend on the `WeatherProvider` interface, never on Open-Meteo directly.
Swapping in a keyed provider means adding one class and changing one
construction site; if that provider needs a key, read it from
`local.properties` or an environment variable through `buildConfigField` — never
from a committed file.

Two coordinate sources, in priority order:

1. a city the user chose by name, via Open-Meteo's geocoding API;
2. the platform's *last known* coarse location, if that permission is granted.

The widget never requests active location updates — a forecast does not justify
waking the GPS. Coordinates are rounded to two decimals (roughly 1 km) before
leaving the device.

---

## Privacy

There is no account system, no analytics SDK, no advertising SDK, no crash
reporter and no cloud database. Nothing is transmitted anywhere except the
weather request described above.

| Feature | Data accessed | Where it goes |
|---|---|---|
| Screen Time | Today's foreground usage total via `UsageStatsManager` | Aggregated on device to a single minute count. Never transmitted. |
| Day Vibe | Today's calendar events: times, all-day flag, attendee presence, status | Reduced on device to counts and a busy score. Titles are read only so the app can offer an opt-in "show titles" setting, and never appear in commentary otherwise. Never transmitted. |
| Battery | Level and charging state from a sticky broadcast | Never transmitted. |
| Weather | Latitude and longitude, rounded to ~1 km | Sent to Open-Meteo to fetch a forecast. Nothing identifying is sent — no device ID, no account, no user agent beyond the platform default. |
| Countdown | The title and date you type | Stored on device in DataStore. Never transmitted. |
| Magic 8 Ball | Nothing. The home-screen widget never stores a question. | — |

All persisted state lives in two app-private DataStore files, included in
Android's backup rules so a device transfer keeps your widgets configured.

---

## Offline behaviour

Seven of the eight widgets are fully local and work with no network at all —
Screen Time, Day Vibe, Battery, Daily Joke, Countdown, Time Progress and Magic
8 Ball. The entire 1 500-string content library ships in the APK; no AI service
or API is involved in generating any response.

Weather degrades in stages rather than failing: a successful fetch is cached per
location and reused for an hour; a failed fetch falls back to that cache and the
tile labels itself "last known"; with no cache at all it asks to be set up. A
raw network error is never shown.

---

## Accessibility

- Every tile carries a `contentDescription` assembled from the real values, not
  the humour — TalkBack reads *"Battery 23 percent, about 2 hours 10 minutes
  remaining"* before the punchline.
- All widget text is real text, never baked into a bitmap, so system font
  scaling applies. Only the flat colour fields, circles, rings, bars and strips
  are drawn.
- Body copy never drops below the design sheet's 13sp / 500 floor.
- Every text colour the tiles render meets WCAG AA (4.5:1 for body, labels and
  metrics), in both light and dark and in all four theme packs. It is not
  hand-tuned: field lightness and muted tones are solved for at resolve time and
  asserted by `ContrastTest`.
- Every tap target in the app is at least 48dp.
- Status is never carried by colour alone: a "needs setup" card says so in text,
  the battery ring is paired with the percentage, and Day Vibe's dots sit beside
  a written meeting count.
- Humour never replaces a useful value. Where the design leads with an
  interpretation — Screen Time's ratio — the literal metric rides in the
  micro-label row at every size.

---

## Testing

```bash
./gradlew :app:testDebugUnitTest          # 149 tests, JVM + Robolectric
./gradlew :app:connectedDebugAndroidTest  # requires a device or emulator
./gradlew :app:lintDebug                  # clean: no issues found
```

| Suite | Covers |
|---|---|
| `ContentPackTest` | Validates the whole generated dataset: counts against the Bible's own inventory, every state present for every personality, globally unique IDs matching `STATE_MODE_INDEX`, balanced braces, no unknown variables, joke category balance, weightings summing to 100. |
| `ResponseSelectorTest` | Unresolvable responses filtered, no unresolved placeholder ever emitted, immediate-repeat protection, cooldowns, coverage preference, adjacent-period exclusion, deterministic daily reuse, copy budgets, bounded history, weighted distribution. |
| `BatteryLogicTest` | Every band boundary from both sides (5/6, 14/15, 24/25, 39/40, …) and charging priority. |
| `ScreenTimeLogicTest` | Band boundaries, ratio maths, the never-two-decimals rule, category and cooldown handling. |
| `CountdownLogicTest` | Today, tomorrow, elapsed, month and year boundaries, a leap day, all eight distance bands, and two zones agreeing on one instant. |
| `TimeProgressLogicTest` | Real period lengths, leap years, 28/29/30/31-day months, week-start setting, DST-shortened days, rollover. |
| `DayVibeLogicTest` | The busy-score formula, both penalties, all five bands, back-to-back detection, lunch-gap detection, tasks-are-not-meetings. |
| `WeatherLogicTest` | Every threshold and the full priority ordering. |
| `Magic8BallLogicTest` | 40/20/40 sentiment weighting and the 12-tap repeat rule. |
| `WidgetInstanceConfigTest` | Two instances of one type staying independent, per-instance history, template inheritance, deletion cleanup, orphan pruning. |
| `WidgetBreakpointTest` | Breakpoint selection including off-by-a-few-dp launcher sizes. |
| `ContrastTest` | Every text and non-text pairing the tiles render, in light and dark, across all four theme packs, against WCAG AA. |
| `GalleryFlowTest`, `WidgetRenderTest` (device) | Compose flows, and every widget × personality × size producing resolved copy. |

---

## Reference materials

Both source documents are preserved unmodified in `docs/references/`:

- `Soft_Dread_Design_Sheet.html` — authoritative for visual design
- `widget_pack_content_bible_v1.docx` — authoritative for copy and logic
- `OFL-Bricolage-Grotesque.txt`, `OFL-IBM-Plex-Mono.txt` — font licences

`app/src/main/assets/content/` is generated implementation data derived from the
Bible. Its `manifest.json` records the SHA-256 of the source document it was
built from.

---

## Known limitations

**Verification.** The build environment had no emulator and no attached device
(no KVM, no hardware virtualisation). Everything statically verifiable was
verified: the debug and minified release APKs build, 149 unit tests pass, lint
reports no issues, all eight widget receivers and providers are present in the
merged manifest, and the instrumented tests compile. **Not yet exercised on a
device:** rendering in a real launcher, the pin-widget flow, granting Usage
Access, a live calendar read, a live weather fetch, and widget deletion cleanup.

**Tap behaviour.** The five data widgets (Battery, Screen Time, Weather, Day
Vibe, Time Progress) rebuild in place when tapped — the anti-repeat engine
guarantees the personality line actually changes, which is what makes a tile
feel alive. The Magic 8 Ball draws a new answer. Daily Joke and Countdown open
the app (the joke is deliberately stable all day; the countdown's tap leads to
its configuration), as do all setup states.

**Widget text is rendered, not composed.** Tiles are drawn to bitmaps by the
app's own renderer so the home screen gets the real Bricolage Grotesque and so
text can be laid out around the tile art (copy never crosses a solid circle).
The trade-offs are deliberate and mitigated: the tile stays a single tap target
with a full spoken description assembled from the real values, and all text
sizes multiply by the system font scale. What is genuinely lost is per-word
text selection on the tile, which home-screen widgets do not offer anyway.

**No tap animation on the 8 ball.** The sheet asks for a 120 ms crossfade; Glance
has no animation API, and the sheet's own platform note says so. The answer
swaps instantly.

**Tile art is drawn to a bitmap capped at 512 px.** RemoteViews payloads are
size-constrained, so the flat field, cropped circle and satellite are rendered
at up to 512 px and stretched. On a 3× display at 4×4 that is roughly a 2×
upscale, softening the circle edge by about two display pixels. On API 31+ the
host clips the rounded corners, so the bitmap needs no alpha channel and is
drawn as `RGB_565` at half the size.

**No discharge-time estimate.** Android exposes `computeChargeTimeRemaining`
(API 28+) for charging only, and no public discharge estimate. The Content Bible
forbids fabricating one from percentage, so the widget shows a remaining-time
figure only while charging on API 28+.

**Two conflicts between the source documents**, resolved by the stated
precedence and recorded in [`docs/content-model.md`](docs/content-model.md): the
design sheet forbids all-caps body copy while the Bible's Chaotic mode is
all-caps (Bible wins — it governs copy); and the Bible's stated total of 1 450
strings disagrees with its own line items, which sum to 1 500 (line items win).

**Countdown's 4×4 progress bar** shows elapsed time since the countdown was
created, which is real data. Countdowns created before that field existed show
no bar rather than a decorative one.

**English only.** Everything is structured for localisation — UI strings in
resources, content in per-locale asset files, `Locale`-aware formatting, plurals
where needed — but only `en` ships.

---

## Next steps

- Run the instrumented suite on a physical device and across launchers
  (Pixel, One UI, Nova) to confirm size reporting and the pin flow.
- Capture screenshots for this README once device rendering is confirmed.
- Add a `content-pack-<locale>.json` set and a second language to prove the
  localisation path end to end.
- Ship the sheet's three alternate theme packs as a proper picker with live tile
  previews, rather than the accent-level swap V1 implements.
- Add Baseline Profiles for widget update latency.
- Extend `WeatherProvider` with a second implementation to validate the
  abstraction against a keyed API.
