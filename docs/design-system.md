# Design system

How `references/Soft_Dread_Design_Sheet.html` became native Android code, and
every place the platform forced an adaptation.

The sheet is a design reference, not production markup. Nothing in it was
converted mechanically: the tokens below were read off the sheet and rebuilt
with Compose and Glance primitives.

---

## 1. Palette

The sheet's first rule — *"one colour per widget; it never changes across sizes,
states or modes"* — is enforced structurally: `WidgetType` carries a
`ColourRole`, and the only way to obtain a tile's colours is
`SoftDreadTiles.colours(role, pack, dark)`. A widget cannot pick its own colour.

### Light — "palette · one owner per swatch"

| Token | Hex | Owner |
|---|---|---|
| clay | `#C65B3C` | Screen Time |
| ember | `#E0762E` | Weather |
| amber | `#F0B23F` | Daily Joke |
| sage | `#7F9C7A` | Battery |
| slate | `#5F7FA8` | Countdown |
| night | `#34435C` | Magic 8 Ball |
| ink | `#3B3128` | Time Progress · type |
| cream | `#F2E7D8` | Day Vibe · pills |

Supporting: type on colour `#FFF6EC`, light wallpaper `#E7DED3`, label on cream
`#A2664A`, label on amber `#7A5410`, secondary type `#8A7666`, type inside pill
`#7A2F1C`. Pill type colours read off the individual 4×4 tiles: sage `#3D4F3A`,
ember `#8A3D0E`, slate `#2F4A68`.

### Dark — "same hue, lower lightness"

Tiles drop 22–26% lightness, all type goes cream, the cream tile inverts.

| Light | Dark |
|---|---|
| clay | `#A34630` |
| ember | `#8F4A1C` |
| amber | `#96701F` |
| sage | `#4E6349` |
| slate | `#3F5674` |
| night | `#232D3D` |
| ink | `#14110E` |
| cream | `#302921` |

Type `#F2E7D8`, circle fill `#E2D3C1`, wallpaper `#1C1814`.

### Legibility correction

The sheet's swatches are kept verbatim in `SoftDreadPalette`, but four of the
light fields cannot carry readable body copy at those exact values — measured
against WCAG AA, the best either type colour manages is clay 3.96:1, ember
4.11:1, sage 4.19:1 and slate 3.86:1, and the sheet's own contrast note already
concedes the point for sage ("large type only, >= 19px 600"). Amber's dark
variant has the same problem against cream type.

`Contrast.legibleField` therefore nudges a field's **lightness only** — hue and
saturation untouched — until one of the two type colours clears 4.5:1. In the
default pack the corrections are 2-5%:

| Field | Sheet | Rendered | Type |
|---|---|---|---|
| clay | `#C65B3C` | `#B95336` | cream |
| ember | `#E0762E` | `#E38240` | ink |
| sage | `#7F9C7A` | `#87A282` | ink |
| slate | `#5F7FA8` | `#55749C` | cream |
| amber (dark) | `#96701F` | `#85631C` | cream |

Amber, night, ink and cream are unchanged. Because the correction is computed
rather than hand-written, the three alternate theme packs — whose colours nobody
hand-checked — are legible by construction too.

Secondary tones follow the same principle. The first implementation muted text
with a fixed opacity per tile, which read comfortably on ink and was unreadable
on ember: 46 of 96 pairings failed AA. `Contrast.muted` now *solves* for the
most muted tone that still clears the ratio, so a tile with headroom gets a
genuinely soft secondary and a tight one barely mutes at all — hierarchy there
comes from size and weight instead. `ContrastTest` asserts every pairing, in
both modes, across all four packs.

### Two exceptions the sheet calls out

- **Amber** is *"the only tile with ink type on colour"*.
- **Cream** is *"the one light tile"* and carries a 1px inset stroke so it holds
  an edge on pale wallpapers. Its circle is slate, not cream, or it would
  vanish — that is what `TileColours.contrastCircle` is for.

### Theme packs

The sheet's identity note explains the pack re-skins by swapping seven hex
values, which is why `ThemePack` exists. All four sets from the sheet are
implemented; ink, night and cream keep their structural roles across packs so
the dark anchor tile stays dark and the light tile stays light.

**Contrast.** The sheet records cream on clay at 5.1:1, ink on amber at 7.9:1,
ink on cream at 11.2:1, and cream on sage at 3.4:1 — *large type only, ≥19px
600*. After the legibility correction above, every tile clears 4.5:1 for normal
text in both modes, so the "large type only" caveat no longer applies to any
tile in the pack.

---

## 2. Typography

Bricolage Grotesque (variable, OFL) with IBM Plex Mono for micro-labels and
spec-style metadata. Both are bundled; licences are in `references/`.

| Sheet role | Spec | Compose token |
|---|---|---|
| HERO 4×4 | 800 · 116/.84 · −.05em | `SoftDreadType.Hero4x4` |
| HERO 4×2 | 800 · 46/.9 · −.03em | `Hero4x2` |
| HERO 2×2 | 800 · 38/.9 · −.04em | `Hero2x2` |
| SUBHEAD 4×4 | 600 · 23/1.1 | `Subhead` |
| VOICE 4×2 | 600 · 16/1.2 | `Voice` |
| VOICE 2×2 · min | 500 · 13/1.22 | `VoiceCompact` |
| MICRO-LABEL | 600 · 10.5/1 · .14em · UC | `MicroLabel` |

Material 3's whole type scale is remapped onto these, so any stock M3 component
inherits the brand voice instead of Roboto.

### Glance adaptations

Glance's `TextStyle` supports colour, size, weight, style, alignment,
decoration and family. It has **no letter-spacing and no line-height**, and its
`FontFamily` resolves a *system* family name through RemoteViews — it cannot
reference a bundled font resource.

| Sheet | Glance | Why |
|---|---|---|
| Bricolage Grotesque | platform sans-serif | RemoteViews cannot load an app font resource by name. Rendering copy into bitmaps would restore the face but break font scaling and TalkBack, so hierarchy is carried by size, weight and case instead. |
| weights 400/500/600/800 | Normal / Medium / Bold | Glance exposes three weights; 800→Bold, 500→Medium. |
| −.05em tracking | omitted | No API. Approximated by size. |
| 116/.84 line height | omitted | No API. |

The app itself has none of these constraints, so the gallery and detail previews
render the real typeface at the real tracking — which makes them the most
faithful representation of the sheet in the product.

---

## 3. Shape, spacing and the circle rule

Straight from the sheet's spec panel:

```
RADIUS ....... 34dp all tiles (28dp at 2×1)
PADDING ...... 2×2 14/15 · 4×2 18/22 · 4×4 20/22
TILE GAP ..... 10dp
CIRCLE Ø ..... 0.55–1.7× tile short side, always cropped
SATELLITE .... max one, Ø ≤ 0.4×
STROKES ...... none, except 1px inset on cream tiles
SHADOW ....... none. flat fields only
MOTION ....... none, except 8 ball tap swap
```

These live on `WidgetBreakpoint` (radius and padding per size) and
`SoftDreadShape` (radii and circle ratios). Circle diameter is measured against
the tile's **short side** rather than its width, so a wide 4×2 tile gets the same
visual circle weight as a square 2×2 one.

`TileGeometry` holds the per-widget, per-size circle placement, transcribed from
the sheet's widget matrix — anchor corner, diameter ratio and overhang for all
eight widgets at all three core sizes, plus the four satellites the sheet places
on 4×4 tiles. Battery and Time Progress carry no field circle at 2×2, because in
the sheet those tiles lead with a data circle, which is the one circle they are
allowed.

### Why the field is a bitmap

The tile is a flat colour field with one oversized circle cropped by an edge.
Glance has no primitive that can position an overflowing shape, and its
`cornerRadius` modifier is a no-op below API 31, so `TileArt` draws the field to
a bitmap. That is the supported route, not a workaround.

Two things keep it cheap:

- bitmaps are capped at **512 px** on the longest side and stretched with
  `FillBounds`; because the bitmap carries the tile's aspect ratio, circles stay
  circular. On a 3× display at 4×4 that is roughly a 2× upscale, softening the
  circle edge by about two display pixels;
- on API 31+ the host clips the rounded corners, so the field needs no alpha and
  is drawn as **RGB_565**, halving its size. Below 31 the corners are baked in
  and the bitmap is ARGB_8888.

Everything is cached in a 6 MB `LruCache` keyed by size, colours and geometry, so
repeated widget updates redraw nothing.

**Text is never drawn into these bitmaps.** Only fields, circles, rings, bars,
strips and pill backgrounds. All copy stays real `Text`, which is what keeps font
scaling and TalkBack working.

### The ring

The sheet draws the data circle as a `conic-gradient`. Glance cannot express one,
so `TileArt.ring` draws a stroked arc — same two-tone ring, crisp boundary,
starting at twelve o'clock and running clockwise. Below 10% and discharging, the
ring turns clay while the tile stays sage, exactly as the sheet's battery note
specifies.

### The segmented strip

The sheet's weather tile carries a five-segment precipitation strip. `TileArt.strip`
draws it with real hourly rain probability from the provider, varying each
segment's **opacity** rather than its length — the sheet's own treatment, which
reads as a shape first and a chart second. When the provider returns no hourly
series the strip is omitted rather than drawn as decoration.

---

## 4. Responsive behaviour

The sheet's grid note gives cell 84.5 × 84.5 with a 10dp gutter, so 2 cells =
179dp and 4 cells = 368dp. Five breakpoints, including the two extension sizes
from the sheet's scaling panel:

| Breakpoint | Size | Copy budget | Carries |
|---|---|---|---|
| `TINY` | 179 × 85 | 45 | label + value only, radius 28, no circle |
| `COMPACT` | 179 × 179 | 34 | label, hero, one voice line |
| `STANDARD` | 368 × 179 | 62 | leading visual beside label, hero, metric, voice |
| `WIDE` | 462 × 179 | 78 | standard rules, circle +20% |
| `EXPANDED` | 368 × 368 | 96 | adds subhead, bars, chips and the copy pill |

Adaptation is by **content density**, not scale. A widget produces a different
`TileContent` per breakpoint; the compact battery tile drops the remaining-time
metric that the large one keeps.

The copy budgets are the sheet's own limits. The Content Bible's compact-layout
rule requires selecting a shorter eligible string rather than truncating a
punchline, which `ResponseSelector` implements against exactly these numbers.

Selection is threshold-based rather than exact, because launchers report sizes a
few dp off the grid.

---

## 5. Brand, app icon and chrome

The product's brand is **witget** — lowercase, from the supplied logo
(`references/witget_logo.png`), which supersedes the sheet's provisional
two-circle mark for everything brand-facing. Soft Dread remains the widget
design system's codename; the tiles are untouched by the rebrand because the
logo draws from the same palette family.

What the logo contributes, and where it landed:

| Logo element | In the app |
|---|---|
| The quartered squircle with the clay circle and speech-bubble "w" | The adaptive launcher icon (background = quadrants and circle, foreground = bubble and "w" inside the 66dp safe zone, plus a monochrome layer) and `WitgetMark`, the Compose mark in the masthead and onboarding. Both use the logo's sampled colours — clay `#CC6848`, sage `#94A082`, slate `#4C5C71`, amber `#F0B058`, panel `#F8E3C8`, ink `#3B2521` — because they render the brand asset itself. |
| The pale warm cream ground | The light wallpaper, now `#FCEEDC`. Measured before adopting: every chrome text tone gains contrast on it (ink 11.1:1, secondary 5.4:1, accent 8.8:1). |
| The chunky rounded wordmark | Chrome display type is **Baloo 2**; body and labels are **Nunito**, the same rounded family at reading sizes. Space Grotesk is retired. Tiles keep Bricolage Grotesque — the sheet governs them. |
| The coral i-dot and amber g-bowl accents | `chrome.brandDot`: the coral appears as decorative marks only — the wordmark's full stop in the masthead, page indicators, the nav's state dot. Never as text: as a mark it clears the 3:1 non-text bar (3.5-3.7:1 light, 3.9-4.7:1 dark on `#CC6848`), which clay-as-text never could. The functional accent stays night. |
| The soft, friendly geometry | Chrome radii rounded up: pills 24dp (stadium at control height), cards 22dp, copy pills 18dp. Tile radii stay the sheet's 34/28dp. |

App chrome reuses the sheet's own layout language: a clay eyebrow label above
every section, a 2px ink rule under the masthead, cream cards at 18dp radius,
and IBM Plex Mono for metadata.

Material 3 supplies layout and behaviour, but its default components read as a
Material app in a custom palette, so the controls are the pack's own
(`ui/components/SoftDreadControls.kt`):

| Material default | Soft Dread control |
|---|---|
| `NavigationBar` with icons and a pill indicator | `NavTabs` — micro-labels over a 2px ink rule, state carried by the pack's circle |
| `TopAppBar` | `ScreenHeader` — a typographic `←` back, headline title, mono spec, hard rule |
| `Button` / `OutlinedButton` (stadium, elevated) | `SoftDreadButton` / `SoftDreadOutlinedButton` — flat blocks at the 16dp pill radius |
| `OutlinedTextField` (floating label) | `SoftDreadField` — eyebrow label above a flat cream well |
| `Switch` | `SoftDreadToggle` — a flat track and a circle |
| ad-hoc `FlowRow` | `PillGroup` — owns both spacing axes at the sheet's 10dp tile gap |

The icon library was removed outright: the sheet's fourth rule is "no icons, no
illustration, no gradients", so a dependency that only supplies icons had no
business in the build. The date and time pickers remain Material's, themed
through the palette — rebuilding a calendar would cost far more in usability and
accessibility than it would gain in style.

### The chrome accent is night, not clay

Clay as an app accent failed twice over: it cannot carry normal-size text on
cream (3.5:1), and cream-plus-coral is Anthropic's own product identity, so the
chrome read as a Claude skin. The accent is now night `#34435C` — 7.5:1 on the
wallpaper, 8.2:1 on cards, 9.3:1 under cream type as a fill — inverting to the
light circle-fill tone in dark mode, the same move the sheet's cream tile makes.
Clay remains exactly what the sheet made it: Screen Time's colour.

### Two type voices

The tiles — and their in-app previews — keep **Bricolage Grotesque**: they are
the sheet's artefacts and the sheet's scale is law there. The app chrome around
them is set in **Space Grotesk** (variable, OFL, bundled), at the owner's
direction: the warm grotesque against this palette read too close to Anthropic's
styling, and the chrome needed its own voice. IBM Plex Mono still carries every
spec-style annotation. The pairing is deliberate — chrome speaks Space Grotesk,
specimens speak the sheet — and `docs/references/OFL-Space-Grotesk.txt` carries
the licence.

### Motion

The sheet's motion row ("NONE, EXCEPT 8 BALL TAP SWAP") governs the tiles, where
RemoteViews cannot animate anyway. The app around them moves, by three rules,
all in `design/Motion.kt`:

- **springs for anything the user causes** — pill selection, presses (a physical
  squeeze, not a ripple), the page dot's stretch;
- **one stagger rhythm** — every group entrance fades up 22dp at 60 ms per item;
- **drift, not spectacle** — the pack's circles float behind onboarding and the
  gallery on 22-34 s cycles, translation only, no per-frame allocation.

Screens slide as blocks through the NavHost; the detail preview morphs between
sizes and cross-fades when a control changes its copy, so customisation is
visibly live. When the system's "remove animations" setting is on, the ambient
drift and breathing hold still; entrances and presses remain, as they carry
state.

---

## 6. The tile renderer

Every widget tile — field, art and text — is drawn into a single bitmap by
`TileRenderer` and hosted by a one-image Glance layout. Two problems forced
this, and both were user-visible: RemoteViews cannot reference a bundled font,
so widgets rendered in the platform sans while every preview showed Bricolage;
and Glance text has no idea where the tile art is, so copy could run across a
solid cream circle and lose its contrast entirely.

What the renderer guarantees:

- **The real face.** Bricolage Grotesque, with the sheet's weights and
  tracking, on the home screen itself.
- **Geometry-safe text.** `TileTextGuard` computes each block's safe span
  against the actual circle and satellite rectangles; label beats detail when
  both cannot fit, and blocks clamp their width rather than crossing a shape.
- **Accessibility intact.** The tile is one tap target carrying a full
  `contentDescription` built from the real values, and every text size is
  multiplied by the system font scale — the fitted hero steps absorb the growth.
- **Bounded payloads.** Long side capped at 1024px, RGB_565 where the host
  clips corners (API 31+), all layers cached by content hash.

`TileSnapshotDump` renders representative tiles to PNG under Robolectric's
native graphics; it exists because looking at the output caught a doubled
circle-ratio transcription that had made expanded tiles unreadable. Keep it.

## 7. Adaptation summary

| Sheet element | Native treatment | Fidelity |
|---|---|---|
| Flat colour field | bitmap (`RGB_565` on API 31+) | exact |
| Oversized cropped circle | drawn into the field bitmap | exact geometry, ~2px edge softening at 3× |
| Satellite circle | same bitmap | exact |
| Conic-gradient ring | stroked arc | visually equivalent |
| Rounded progress bars | bitmap with rounded caps | exact |
| Segmented strip | bitmap, opacity per segment | exact |
| Cream copy pill (4×4 only) | bitmap background + real text | exact |
| Metadata chips | bitmap background + real text | exact |
| 1px inset stroke on cream | stroked into the bitmap | exact |
| 34dp corner radius | baked in below API 31, host-clipped above | exact |
| Bricolage Grotesque | exact everywhere — tiles are rendered to bitmaps by `TileRenderer` with the bundled face | exact |
| Letter-spacing / line height | tracked in the renderer's paints | exact |
| 8 ball 120ms crossfade | instant swap via `ActionCallback` | **not reproduced** |
| No shadows, no gradients, no icons | never introduced | exact |
