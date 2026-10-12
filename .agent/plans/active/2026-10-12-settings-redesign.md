# Settings Redesign: Sectioned Root And Grouped Screens

## Goal

Make Settings easy to scan, modelled on Continuum: a short root of icon +
summary rows in titled sections, with individual toggles moved into the screen
they belong to, grouped sub-screens, and shared-axis motion between screens.

## Non-goals

- No preference key, default or behavior change; no migration.
- No updater dialog work (separate PR), no release work.
- No Compose rewrite of Settings.

## Current context

Base is master 71356a010. The root mixed 26 rows: navigation rows, inline
switches (cutouts, notifications, PiP, background audio, stream proxy) and an
EditText for the proxy URL. Large screens (UI 25 rows, Chat 47) were flat
lists. Settings search only walked top-level preferences, so anything inside a
category was unsearchable.

## Files likely involved

- `app/src/main/res/xml/{root,ui,player,chat,proxy}_preferences.xml`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/SettingsActivity.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/settings/MaterialPreferenceFragment.kt`
- New `SettingsMotion.kt`, Material Symbols drawables, strings
- `app/src/test/java/com/github/andreyasadchy/xtra/ui/settings/SettingsStructureTest.kt`

## Risks

Risk level: medium. The transition bump (1.5.0 → 1.7.2) also affects any
other androidx transition in the app. Moving preferences moves their handlers: language and
cutouts to UI, the PiP availability check to Player. Hidden preferences inside a
category must not leave an empty header (Chat translation hides its category).
Search entries now navigate to the new screens.

## Human approval

Required before implementation: no. The user asked for a decluttered,
Continuum-style settings menu.

## Implementation steps

1. Root: update banner, then Appearance / Watching / Network & account /
   System & About sections; every row has a Material Symbols icon and summary.
   Live notifications stays as the single root switch.
2. Move language + cutouts → UI › General; PiP + background audio → Player ›
   Picture-in-picture & background; stream proxy + URL → Proxy › Playlist proxy.
3. Group UI, Player, Chat and Proxy screens into titled categories.
4. Search indexes preferences inside categories.
5. Shared X-axis transitions on every settings screen, including search,
   seekable so the predictive back gesture scrubs them.

## Verification

- [x] `SettingsStructureTest`: root shape, icons/summaries, moved keys, unique
  keys across section screens, nested search reach; renders dark/light.
- [x] assembleDebug, testDebugUnitTest (820 passed), lintDebug (0 errors, 348
  existing warnings).
- [x] API 36 emulator: root sections, UI › Language shows the current locale,
  shared-axis transition in and back, search "gesture" finds a categorised
  setting and opens Player scrolled to it.
- [x] `SettingsMotionTest`: seekable, fade-through alphas, slide direction.
  API 36 emulator, held back swipe in Player: screen fades and slides with the
  finger, cancel restores Player, commit lands on the root; forward still
  animates (823 tests, lint 0 errors).
- [ ] Device: every root row opens its screen; back/forward motion; language
  change; cutouts toggle recreates; PiP row hidden where unsupported; stream
  proxy toggle; search for "language", "picture", "translate" and open results;
  backup/restore; check for updates; Light/Dark/AMOLED; 2× font; tablet width.

## Progress log

- 2026-10-12: Implemented structure, motion, search recursion and tests.
- 2026-10-12: User saw no animation and two overlapping screens during the
  predictive back gesture. `MaterialSharedAxis` claims seeking support but its
  animators never followed the gesture, so both fragments stayed fully opaque
  until commit. Replaced with `SettingsAxisTransition` (keyframed slide + fade
  through) and made each settings root a transition group.
- 2026-10-12: Changing the update URL then pressing back left Settings blank
  (root STARTED, Updater still drawn). androidx routes even key-back through
  the predictive path; transition 1.5.0 starts `animateToEnd` before the seek
  controller is ready and the spring can finish first, freezing the animators
  at 0. The old `MaterialSharedAxis` hit the same race (root stuck STARTED) but
  stayed visible. transition 1.6.0 defers animate-to-end/start until ready;
  pinned it explicitly. 0 of 5 repro runs stuck after (before: 1 in 3-6).
- 2026-10-12: LeakCanary heap from the user's phone (old MaterialSharedAxis
  build) showed Settings fragments retained through androidx.transition's
  main-thread running-animators map. Seek-driven transitions never removed
  their animators there before transition 1.7.0 (`removeSeekingAnimators`).
  Bumped to 1.7.2; after the same emulator exercise (URL race, cancelled and
  committed gestures, key back) the map held 0 entries, down from 31, and
  shark found 0 retained fragments.
- 2026-10-12: New strings carry tools:ignore="MissingTranslation" like other
  ThystTV-only strings; emulator smoke passed.

## Decisions

- Keep existing (translated) row titles; only the English proxy title changes,
  because the screen now holds both proxies.
- Own keyframed transition instead of `MaterialSharedAxis`: same spec (30dp,
  fade through at 35%, standard easing), but seekable in practice.
- Pin `androidx.transition` 1.7.2: 1.6.0 fixes the not-ready freeze, 1.7.0
  stops seekable transitions leaking views; 1.7.2 is the latest stable.
- One axis for every settings navigation, including search, so both sides of a
  transition always move together.
