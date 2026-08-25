# CLAUDE.md

Working notes for future sessions. The README covers product and setup; this
file covers the conventions that are easy to break without noticing.

## What this is

**witget** — a native Android widget pack: eight Glance home-screen widgets that
interpret device data with personality, plus a Compose app for browsing and
customising them. The widget design system's codename is **Soft Dread**; the
brand (name, launcher icon, app chrome voice) comes from the logo at
`docs/references/witget_logo.png`. Package and class names keep the codename.

## Source-of-truth hierarchy

Decisions follow this precedence. Do not invert it.

1. **Visual design** → `docs/references/Soft_Dread_Design_Sheet.html`
2. **Copy, response pools, conditions, thresholds, equivalencies, trigger logic,
   anti-repeat rules, variables** → `docs/references/widget_pack_content_bible_v1.docx`
3. **Product functionality and technical architecture** → the repository itself

Both reference files are authoritative and **must not be edited**. The Bible in
particular is a source document, not implementation data.

Two conflicts are already resolved; do not "fix" them back:

- The sheet forbids all-caps body copy, but the Bible's Chaotic pool is
  all-caps. The Bible governs copy, so the all-caps stays.
- The sheet leads the Screen Time tile with the equivalency ratio, but the Bible
  requires the literal metric to stay visible. Both hold: the ratio is the hero,
  and the real usage rides in `TileContent.labelDetail` at every size.

## Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest          # 147 tests; keep this green
./gradlew :app:lintDebug                  # keep at 0 errors
./gradlew :app:assembleRelease            # verifies R8 rules
./gradlew :app:connectedDebugAndroidTest  # needs a device; none in CI so far
```

`local.properties` must contain `sdk.dir`. It is git-ignored, as is
`keystore.properties`.

## Content engine rules

Content lives in `app/src/main/assets/content/`, generated from the Bible by
`tools/content/extract_content.py`. **Never hand-edit those JSON files** — edit
the Bible, re-run the extractor, then run `ContentPackTest`.

- Response IDs follow the Bible's own pattern, `WIDGET_STATE_MODE_INDEX`
  (`BAT_B6_SARCASTIC_03`). IDs are the anti-repeat currency; changing one resets
  a user's history for that string.
- One asset file per widget, so a battery update parses ~50 KB, not 316 KB.
- Every state must carry a pool for all five personalities. `ContentPackTest`
  enforces this, along with unique IDs, balanced braces, known variables, and
  counts matching the Bible's inventory.

## Selection engine rules

`domain/selection/ResponseSelector` is the **only** place a response is chosen.
Do not add ad-hoc `random()` picks inside a widget.

- Anti-repeat behaviour is data: `AntiRepeatPolicy`, with the per-pool values in
  `AntiRepeatPolicies`. Add a policy rather than a branch.
- The filter chain is: resolvable variables → copy budget → anti-repeat →
  unseen preference. Each step relaxes in reverse if it would leave nothing, so
  the engine always returns copy when the pool is non-empty.
- A response whose variables cannot all resolve is **ineligible**. `Interpolation`
  additionally strips leftovers. An unresolved `{placeholder}` reaching a user is
  a bug, and there is a test for it.
- "Daily" content is stable because the chosen ID is recorded against a
  `periodKey` and reused within that period — not because the seed happens to be
  deterministic. Keep `reuseWithinPeriod` for anything daily.
- `ContentSession` batches one update's history reads and writes. Widgets should
  select through it, never touch `SoftDreadStore` history directly.

## Widget rules

- Every widget extends `SoftDreadWidget` and implements only `buildPayload`.
  Configuration, personality resolution, dark-mode resolution, history loading
  and persistence are handled by the base class.
- Widgets produce a `TileContent` per breakpoint. They do **not** write layout.
  `SoftDreadTile` renders it for Glance; `PreviewTile` renders the same model in
  Compose for the app. Changing one without the other creates drift.
- Configuration is keyed by `appWidgetId`, never by widget type. Multiple
  instances must stay independent — there are tests for this.
- Ids at or below `WidgetInstanceConfig.TEMPLATE_ID_BASE` (-100) are per-type
  **templates**, not placed widgets: they hold what the gallery edits before
  anything is placed, and `configOrCreate` seeds new instances from them.
  Anything that walks stored configs must use `placedConfigs()` unless it really
  means templates too — `pruneOrphans` and the gallery's placed count both do.
- `SizeMode.Exact`, not `Responsive`: the tile field is a bitmap, and Responsive
  would build one RemoteViews per declared size.
- A widget must never fabricate data. Missing permission or missing setup renders
  a setup state via `setupContent(...)`.
- **Refreshing a widget means `forceRefresh(context, glanceId)`, never a bare
  `update()`.** Glance keeps the composition session alive; the payload is
  rebuilt inside the composition when `REFRESH_TICK` changes, and a bare update
  just redraws the stale model (the 8 ball ignored taps for a whole session
  lifetime this way once).
- **Tiles are rendered by `TileRenderer` into one bitmap** — field, art and
  text — because RemoteViews cannot load the bundled Bricolage and Glance text
  cannot avoid the circles. `SoftDreadTile` is only the tap target and the
  spoken `contentDescription`; keep it that way. Text sizes multiply by the
  system font scale inside the renderer.
- **Every text block goes through `TileTextGuard`.** It is the reason copy no
  longer crosses a solid circle and disappears. Solid shapes are obstacles;
  tints resolved by `Contrast.tint` are opaque and count as solid.
- **`TileGeometry`'s 4x4 diameters are measured from the sheet** (largest is
  clay at 0.82 of the tile). An early transcription doubled them and buried the
  expanded tiles under their own circles — the `TileSnapshotDump` test renders
  real PNGs under Robolectric; look at them before trusting any geometry change.
- `buildFresh` builds content for **all five breakpoints every time** — resizing
  recomposes without re-running the build, so a partial map would render copy
  budgeted for the wrong size and clip.
- Hero values render through `GlanceType.heroFitted` (mirrored in `PreviewTile`);
  Glance has no text auto-sizing, so long values step down by length instead of
  clipping.

## Design rules

The four rules from the sheet that the renderer enforces centrally: one
permanent colour per widget; exactly one cropped oversized circle plus at most
one satellite; a copy pill on 4×4 only; lowercase everywhere except
micro-labels. Circle placement per widget and size lives in `TileGeometry` and
is transcribed from the sheet's widget matrix — treat those numbers as spec, not
as taste.

Material You is an opt-in override, never the default. The sheet is explicit:
"the pack keeps its own colour set."

**Colour is derived, not assigned.** Never hardcode an alpha for muted text or
pick a type colour by hand. `Contrast.legibleField` corrects a field's lightness
until type can reach AA on it, `Contrast.bestOn` picks ink or cream by
measurement, and `Contrast.muted` solves for the most muted tone that still
passes. `ContrastTest` asserts every pairing in both modes across all four theme
packs, and it is the reason the pack is readable — an earlier version failed 46
of 96 pairings because the alphas were eyeballed.

**The chrome accent is night, never clay.** Clay on cream fails AA and reads as
Anthropic's palette; `chrome.accent`/`onAccent`/`accentText` are the only accent
tokens. Clay belongs to the Screen Time tile.

**Two type voices.** Tiles and their previews are Bricolage Grotesque (the
sheet's scale); app chrome is the logo's rounded pairing — Baloo 2 for display,
Nunito for body (see design-system.md). Don't mix them, and don't reintroduce a
squarish grotesque into the chrome.

**The brand dot is decorative only.** `chrome.brandDot` (the logo's coral) marks
state — page dots, nav dots, the wordmark's full stop — and is contrast-tested
as a non-text element. It is never a text colour; the functional accent stays
night.

**Chrome uses the pack's controls, not Material's.** `ui/components/SoftDreadControls.kt`
holds the buttons, field, toggle, pills and rules; `Chrome.kt` holds `NavTabs`
and `ScreenHeader`. Adding a stock `Button`, `OutlinedTextField` or `Switch`
puts the Material look straight back. There are no icons anywhere — the sheet
forbids them and the icon dependency has been removed. Rows of pills go through
`PillGroup`, which owns both spacing axes; passing only a horizontal arrangement
to a bare `FlowRow` is what made wrapped rows collide.

## Things not to change casually

- **Glance version.** Pinned to stable `1.1.1` on purpose; `1.2.0` never left rc
  and `1.3.0` is alpha.
- **`TileArt.MAX_DIMENSION` (512).** It is the RemoteViews payload budget. Raising
  it risks the launcher dropping the widget.
- **The RGB_565 path.** Only valid because API 31+ hosts clip the rounded corners.
  If that assumption changes, the bitmap needs alpha again.
- **`minSdk 26`.** `LocalDate.ofInstant` (API 34) and
  `computeChargeTimeRemaining` (API 28) are both already guarded; new
  `java.time` calls need checking against API 26, not against the JDK.
- **The privacy posture.** No analytics, no ads, no accounts, no crash reporting.
  Weather is the only network call. Coordinates are rounded to ~1 km before they
  leave the device; keep it that way.
- **Response IDs and the `manifest.json` SHA.** They tie the shipped content to a
  specific version of the Bible.

## Brand assets

- The wordmark is *drawn*, not typed: `WitgetWordmark` renders Baloo 700 and
  paints the coral dot over the i's tittle and the amber counter under the g's
  bowl at anchors measured by `WordmarkCalibration` (a pixel-scanning test).
  If the Baloo file is ever updated, re-run the calibration and re-measure.
- The 8 ball deliberately deviates from the sheet's night tile: the ball is the
  tile's one circle. That was an owner decision; don't restore the field circles.

## Known gaps

Widget rendering has never been exercised on a real device or emulator — the
build environment had no KVM. Everything else (build, unit tests, lint, release
minification, manifest wiring) is verified. See the README's *Known limitations*
before claiming any device behaviour works.
