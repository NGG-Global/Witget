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

Three conflicts are already resolved; do not "fix" them back:

- The sheet forbids all-caps body copy, but the Bible's Chaotic pool is
  all-caps. The Bible governs copy, so the all-caps stays.
- The sheet leads the Screen Time tile with the equivalency ratio, but the Bible
  requires the literal metric to stay visible. Both hold: the ratio is the hero,
  and the real usage rides in `TileContent.labelDetail` at every size.
- The Bible's Battery row says "avoid timer polling", but with only the four
  manifest-declarable battery broadcasts the tile sat on a stale percentage for
  hours at a time. `widget_battery_info.xml` therefore also carries the
  platform's own `updatePeriodMillis` (30 min, the platform minimum), which is
  batched and does not wake the device — the thing the Bible's line is guarding
  against. The Bible's own Weather row, "data values can still update freely",
  is the precedent: the anti-repeat rules govern copy, not readings.

## Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest          # 181 tests; keep this green
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
- **Motifs are curated, never guessed.** Every joke carries a glyph + retint
  colour and every equivalency unit a glyph, hand-assigned per ID in the
  extractor's `JOKE_MOTIFS`/`UNIT_GLYPHS` tables (blue paint → `PAINT`+`SLATE`,
  toaster → `TOAST`). The names must exist in `MotifGlyph`/`ColourRole`;
  `ContentPackTest` fails on any drift or gap. No keyword matching at runtime.

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
- **One history entry per pool per update.** An update builds every breakpoint
  but the user sees one, and the Bible's rule is "keep the last 4 response IDs
  per trigger/state". Recording all six pushed the shown line out of a four-deep
  window inside a single refresh. `ContentSession` keeps the first write per
  pool, and `SoftDreadWidget` orders `breakpoints` so the breakpoint on screen
  is selected first — read from `AppWidgetManager.getAppWidgetOptions`. Change
  either half and the other stops meaning anything. `ContentSessionTest` guards
  it.

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
- **Every receiver extends `SoftDreadWidgetReceiver`, never `GlanceAppWidgetReceiver`
  directly.** Glance's own `onUpdate` calls a bare `update()`, which inside a
  live session redraws the stale model — so `updatePeriodMillis` ticks did
  nothing at all. The base class force-refreshes on update, handles the extra
  broadcasts a widget declares (`refreshActions` — `AppWidgetProvider.onReceive`
  drops every action it does not recognise, so a manifest `intent-filter` alone
  is inert), and arms the rollover and weather schedules in `onEnabled` so a
  widget placed from the launcher picker is scheduled even if the app is never
  opened.
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
- **The expanded/hero bottom stack is measured before it is placed.** On
  short-wide tablet tiles it used to rise straight through the label; now it
  gives things up in a fixed order (statement lines, a hero size step, the
  second subhead line, the call to action, chips, strip) and the pill is never
  dropped because it carries the literal metric. `drawBottomStack` in
  `TileRenderer` owns this; the `regress_*` snapshot dumps are its receipts.
- **Every text block goes through `TileTextGuard`.** It is the reason copy no
  longer crosses a solid circle and disappears. Solid shapes are obstacles;
  tints resolved by `Contrast.tint` are opaque and count as solid.
- **The motif plate is content, drawn by the renderer.** Jokes and Screen Time
  carry a curated `TileContent.motif` pictogram (drawn once, in `MotifArt`, for
  both renderers) and jokes may carry a `fieldRole` that retints the whole tile
  through `SoftDreadTiles.colours` — same pipeline, same contrast guarantees.
  The plate takes the satellite's slot when that slot is in the tile's upper
  half, else the top-end slot under the label; it is always an obstacle. Plate,
  ink and accent colours are measured against the actual backdrop
  (`perceptibleShape`/`bestOn`), never assigned.
- **Nothing is ellipsized before it has been shrunk.** Every text block goes
  through `Pass.fitted` (or `rowBlock`/`fitStatement`, which wrap it), which
  steps the type size down until the copy fits or hits that role's floor. This
  is what keeps a hero numeral, a micro-label, a metric and a punchline whole at
  a 1.6x system font scale and in a column narrowed by a field circle. Adding a
  `layout(...)` call that draws user-visible copy directly re-opens the hole;
  `TileMatrixDump` is how you check.
- **Copy always outranks the pictogram.** The renderer holds a fixed surrender
  order before any text is cut: the expanded statement steps its size down
  (`STATEMENT_STEPS`), the compact tile gives up its chip and then steps the
  voice down (`VOICE_STEPS`), and if a text block would still be cut or would
  cross the plate, the pass sets `copyLostToMotif` and the tile re-renders
  without the plate (the retint stays). Never let a motif truncate a punchline
  or a metric.
- **`TileGeometry`'s 4x4 diameters are measured from the sheet** (largest is
  clay at 0.82 of the tile). An early transcription doubled them and buried the
  expanded tiles under their own circles — the `TileSnapshotDump` test renders
  real PNGs under Robolectric; look at them before trusting any geometry change.
- `buildFresh` builds content for **all breakpoints every time** (six, with
  the tablet `HERO`) — resizing
  recomposes without re-running the build, so a partial map would render copy
  budgeted for the wrong size and clip.
- Hero values render through the renderer's fitted scale (mirrored in
  `PreviewTile`); long values step down by length instead of clipping.
- The renderer's `unit` is fit-based and capped at 1.25× density: oversized
  tiles gain room, never magnified glyphs. `HERO` (≥520×300dp) is the tablet
  canvas; when adding a breakpoint, every exhaustive `when` and the widgets'
  `isLarge` checks are the compiler-guided checklist.

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
- **Where the weather request happens.** `WidgetRefreshWorker` owns it, under a
  `CONNECTED` constraint. A widget build passes `allowNetworkWhenCached = false`
  because it can be running inside a broadcast — the platform's periodic update
  or a tap — where waiting on a slow request holds the receiver open. The cache
  holds one entry per place, not one overall, so two Weather widgets set to
  different cities do not evict each other. `WeatherRepositoryTest` guards both.
- **Response IDs and the `manifest.json` SHA.** They tie the shipped content to a
  specific version of the Bible.

## First-run flow

`ui/onboarding/SetupFlow.kt` implements the owner's motion concept
(`docs/references/witget_setup_concept.html`): a skippable brand splash, then
five steps over a circle field whose four circles re-anchor per step and
re-skin with the chosen pack. Everything previewed is real — the voice step
speaks actual `BAT_B6` Bible lines per personality, the look step resolves
through `SoftDreadTiles`, the access step asks only for what was picked, and
the home step drives the real pin flow one widget at a time. Reduced motion
skips the splash and snaps the field.

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
minification, manifest wiring) is verified, and `TileMatrixDump` renders the
real pipeline to PNG under Robolectric's native graphics, which is as close to
seeing the tiles as this environment gets. See the README's *Known limitations*
before claiming any device behaviour works.

Two content-cadence questions are open and belong to whoever owns the Bible:

- **Screen Time re-rolls its equivalency on every update.** `ScreenTimeWidget`
  seeds `chooseEquivalency` with the current time, so the unit behind the hero
  ratio changes every refresh. The Bible's "do not reuse the same equivalency
  for 7 days" only makes sense at a roughly daily cadence — at 30-minute
  intervals the 50-unit library is exhausted within a day and the cooldown
  permanently relaxes. The fix is the Daily Joke pattern (seed from local date +
  instance, record against a `periodKey`, `reuseWithinPeriod`), but pinning a
  unit for a whole day can push its ratio out of the preferred window as usage
  grows, so it is a product call rather than a bug fix.
- **Countdown updates every 30 minutes**, where the Bible asks for daily above
  24 hours. Harmless as it stands — `updatePeriodMillis` does not wake the
  device — but it is a deviation, not an implementation of the row.
