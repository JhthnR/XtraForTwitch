# Player Architecture And Regression Notes

## Why This Area Is High Risk

The player is ThystTV's most important UX surface. Small lifecycle changes can cause double audio, stale fragments, broken minimize/restore, PiP regressions, gesture conflicts, black surfaces during transitions, or unreadable overlay controls.

## Staleness Note

The paths below are a map, not a guarantee. If code has moved, search for the current file/function and update this doc when useful.

## Bounded live recovery

Live recovery in the default Media3 service is bounded: three retries at
1s/2.5s/5s for transient connection/timeouts, HTTP 408/429/5xx and
behind-live-window errors. Continuous buffering is checked after 15s. A retry
waits at most 60s for validated connectivity, without repeatedly fetching while
offline. Only 30s of continuous actual playback or an explicit source command
resets the budget. Brief ready/error flaps do not.

Recovery retains the configured source, selected tracks, volume and play intent.
It avoids clearing media/surface; last-frame retention still needs device QA.
Behind-live-window failures seek to live. Pause, stop, source replacement, close,
Cast and service destruction cancel callbacks. Live network restoration no longer
rebuilds a paused player. Permanent/auth/404 errors remain visible; the existing
one-way custom-proxy fallback is guarded against stale or paused players.
Exhaustion asks the viewer to retry or reopen. The optional legacy backend and
ordinary VoD recovery are unchanged.

## Core Invariants

- Only one active player session should exist.
- Starting a new player should close or safely replace the old player.
- Closing a player should release playback resources.
- Minimize/restore must not leave stale fragments.
- Sleep timer should close only the current player.
- PiP auto-enter should be enabled only while a player is open.
- Stream switching must not leave old audio playing.
- UI controls should stay responsive during live/VoD transitions.
- Overlay surfaces should remain readable over arbitrary video content.
- Quality, Speed, Stream volume, and More share one player-owned popup host; only one
  can be attached at a time, and teardown/minimize/PiP must remove it with the player view.

## Pinch and double-tap chat ownership

A pinch can supersede a double tap when its first finger is interpreted as the
second tap. Capture the chat flags and saved-open preference before the accepted
double tap cycles chat. On pinch takeover, restore that snapshot, cancel the chat
fade, reparent the existing chat view and restore sidebar/video geometry and the
chat button icon. Another cycle cannot undo hidden/sidebar/floating's three-state
transition. Clear the snapshot at pointer-sequence boundaries and view destruction.

`PlayerPinchChatTest` exercises the real fragment transitions, listener, arbiter,
Android containers and preferences with fragment attachment/network chat mocked.
Device gesture timing and playback still need the manual matrix in `MANUAL_QA.md`.

## Half-folded layout

Half-opening a foldable does not change the configuration, so `PlayerFragment`
follows Jetpack Window Manager's `FoldingFeature` while the player view is
started and splits the maximized player at a `HALF_OPENED` hinge:

- Tabletop (hinge across the window): video above the hinge, chat below. Floating
  chat is docked below the hinge and the chat cycle skips floating, because a
  floating window would cover the video pane. Docked chat floats again once the
  posture ends, unless the viewer changed the chat mode meanwhile. Hidden chat
  leaves the lower pane empty rather than drawing video across the hinge.
- Book (hinge down the window): video and chat side by side only while chat is
  showing; hidden or floating chat keeps the whole screen for video.

`PlayerFoldLayoutPolicy` maps the hinge and the sliding layout's content area
(both in window pixels, so status-bar and cutout padding are excluded) to pane
sizes, and returns no split when the hinge does not cross the whole window, as in
split screen, or a pane would be under 160dp. With the keyboard open, a smaller
tabletop chat pane is allowed: chat reserves 120dp above the keyboard where the
window has room, even if that covers the bottom of the video. IME inset changes
reapply the split, including keyboard height changes without a window resize.
Minimize, PiP, an unfold, or the "Half-folded layout" setting being
turned off restore the normal portrait or landscape geometry. Every chat
transition, orientation change, minimize/maximize, PiP change and sliding layout
resize re-runs the split; geometry changes are idempotent, so repeated calls do
not relayout.

`PlayerFoldLayoutPolicyTest` covers the pane math and `PlayerFoldLayoutTest`
drives the real fragment geometry and chat transitions with a simulated hinge.
Hinge timing and real postures still need the device matrix in `MANUAL_QA.md`.

## Android TV remote input

Television UI mode enables remote focus routing; phone/tablet input is unchanged.
The empty activity player container is not a focus target. On browsing roots,
Back returns to the selected bottom-navigation item; UP returns to the current
page, without scrolling to the end of a paged feed. Focus rings are separate
from the selected destination indicator. TV toolbars, tabs and Stats ranges stay
visible, including on TV images that also advertise touchscreen support. Profile
headers can collapse to their pinned toolbar/tab area so video lists retain space.
If that collapse covers the initially focused profile action, a one-shot layout
check moves focus to the selected visible tab or toolbar action. It preserves
focus that has already moved into page content or the player.

A first D-pad/Enter press reveals hidden player controls and focuses play/pause
when visible, otherwise the first available action such as Quality;
subsequent keys use native control handling. Remote input renews the hide timer.
Visible controls and player popups contain directional focus above the browse UI.
Back dismisses a popup, then hides controls, then uses existing minimize behavior.
A focused mini-player restores with CENTER/Enter; a TV-only Close action invokes
the existing player close path. Speed/volume slider changes persist from remote
input. Speed labels round the slider's float to two decimal places before compact
formatting, so a 1.05x setting cannot appear as 1.04x. Existing media-session
hardware keys keep their original handling.

See `TESTING.md` for emulator evidence and its limits. Real live/VoD decoding,
switching, minimize/restore, close/reopen, PiP/background, speed/quality, gestures
and floating-chat behavior remain the player regression checklist.

## High-Risk Files

Update this list as the code evolves.

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/main/MainActivity.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/player/`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/`
- `app/src/main/java/com/github/andreyasadchy/xtra/player/lowlatency/`
- player-related layouts in `app/src/main/res/layout/`
- player-related strings in `app/src/main/res/values/`
- player settings/preferences in `app/src/main/res/xml/`
- Media3 / ExoPlayer dependency declarations in Gradle catalogs/build files

## Recent Lessons From ThystTV Fixes

- Media3 1.11's old `Callback.onConnect` default is a placeholder. The default
  session service negotiates through `PlaybackSessionCallback.onConnectAsync`
  with controller-aware defaults; copying the old default loses surface, tracks
  and playback permissions even while the service itself continues playing.
  Keep existing custom commands for trusted controllers and Media3's read access
  for untrusted controllers. A dependency update must test visible live/VoD video
  and usable quality controls, not merely an advancing playback position.
- For issue #5, logs did not show a clean app `FATAL EXCEPTION`; the safer fix was the narrow Media3/ExoPlayer rollback and HLS parser compatibility change, not broad WIP player sync.
- Quality menu labels can regress to HLS variant indices after chat-only -> auto transitions. Preserve readable labels derived from labels, format height/frame rate, or URL path.
- Mixed-codec Quality choices keep resolution as the primary label and show a standardized
  codec name as secondary metadata. Audio-only and Chat-only stay outside the scrolling
  video grid so they remain reachable in compact landscape players.
- Selected Quality/Speed chips render as solid primary pills with luminance-matched
  content color (`PlayerPanelColors.onSelected`); do not revert to low-contrast blends.
- Quality chip column counts are derived from measured bold label widths so labels such
  as "1080p60" never truncate; do not return to fixed 4-column grids with silent clipping.
- VoD and live menus may legitimately differ. Do not add chat-only to VoD unless the data/model supports it.
- Floating chat should use a dark translucent video-overlay palette in both app themes; background opacity remains user-controlled.
- Stats/player overlay controls need compact and landscape checks because label clipping and stacked graph labels have happened before.
- Any view adapter tied to fragments can leak destroyed views. Clear listeners/adapters in `onDestroyView()` when changing RecyclerView or fragment lifecycles.

## Player System-UI Listener Ownership

The landscape decor-view system-UI listener is owned by `PlayerSystemUiListener`
and the player's **view** lifecycle. Detach it on portrait layout and explicitly
at the start of `onDestroyView`, as well as on the view lifecycle's destroy event.
Its per-decor registration replaces and invalidates the previous callback; old
player teardown must not clear a newer player's registration. Detach drops the
callback, lifecycle and decor references, including on replacement. Do not install
a raw fragment-capturing callback on the activity window: the decor can outlive
the player and retain a closed fragment after rotation/stream switching.

## Player Popup Ownership

Quality uses the same 48dp gear button on compact, landscape, tablet and resized
surfaces. Never replace it with the selected-quality text based on width or label
availability: rotation callbacks can still see the previous layout's width, and
replacing the trigger also changes its popup anchor. The menu retains the selected
quality; the gear's accessible description includes it when available.

`PlayerFragment` owns the full-player dismissal host and delegates each popup's controls
to a short-lived binder. Quality, Speed, Stream volume, and More use the same bounded,
inset-aware, trigger-relative placement and alpha/scale motion. The host consumes panel
and outside touches, keeps controls visible while open, closes on back/outside tap, and
restores focus plus the normal control auto-hide timer on dismissal.

Popup motion animates the bounded container in both directions (220ms eased reveal,
140ms exit), with its pivot clamped toward the cached trigger. Never animate the inner
card on entry and the viewport on exit: scrolling makes their geometry differ. The
scrim fades during dismissal, and replacement/teardown cancels pending motion. Platform
animator duration settings apply. Quality/Speed chips provide bounded ripple feedback.
Selected chip foregrounds use the higher-contrast black/white color for the accent.
Quality binds text colors immediately because inherited drawable state can leave the
first, unattached frame using the unselected text color. Speed uses a short visual
heading with a full playback-speed accessibility label, keeping 200% text readable.

Cards close via outside tap or back. Quality, Speed, and More also have persistent
header close buttons with 48dp touch targets; Stream volume keeps its auto-dismiss timer.
In portrait a light scrim dims video/chat and passes taps to the outside-dismiss handler.
Taps in the card itself do not dismiss it unless an option's action requests dismissal.

More keeps its existing grouped action order and preference gates. Its display-mode row
intentionally opens the existing single-choice alert above the embedded host; the four
top-level player popups themselves must not return to DialogFragment or bottom-sheet
ownership.

Placement rules: popups prefer the space above their trigger, fall back to below it, and
when the surface is too short for either they pin to the edge nearest the trigger
(bottom-bar buttons such as Stream volume stay bottom-anchored; top-bar triggers such as
Quality, Speed, and More stay top-anchored). Horizontally, left-side controls align the
panel's left edge and right-side controls align its right edge before bounds clamping, so
placement remains visibly tied to the button rather than drifting around the surface.
The host is a sibling of `slidingLayout`, above floating chat, **not a child of the
16:9 `PlayerLayout`**. Portrait may use the visible fragment space over chat; landscape
is bounded by the video's visible screen area. Surface and window rectangles use screen
coordinates consistently; trigger coordinates are relative to the host. Width classes
use the available video width, not the full window width including side chat.

`PlayerPopupContent` preserves the card and its first/header row, moving only options
into `playerPopupViewport`. More's old inner scroll view is unwrapped so only one scroll
view handles options. Natural content height is measured at final width, then the card
height is bounded and its weighted viewport receives the remaining space. Never wrap
the whole card in a scroll view: that hides the rounded outline, title, and close button.
Quality chips keep single-line Auto vertically centered beside codec pairs, and chip
heights grow when enlarged fonts need more room.
Popup content is bound before the host is shown, and `showPlayerPopup` places the
container (explicit measure, no layout pass needed) before the host becomes visible,
so the reveal animation's first frame is already at the anchored geometry. Placement
application is idempotent (geometry written only on change), host-bound changes trigger
repositioning, and the first valid trigger rect is cached as the popup's anchor
for its whole lifetime: while the cache is empty the trigger is re-read (a popup that
opened from fallback geometry corrects itself when the trigger gains bounds, for
example controls were GONE at open time), but once cached, later control-bar reflows —
quality-label or viewer-count text changes — must not drag a visible popup around.
Do not remove these guards; stale inherited margins, reveal-before-place frame races,
and untracked trigger moves caused jump/multi-press misplacement reports.

## Saved resume and headset fallback

`PlayerViewModel` resolves automatic saved positions for all player backends. A
position at or beyond a known positive duration restarts at zero when a network
VoD has final remote thumbnail metadata. A processing thumbnail, absent thumbnail
or locally cached bookmark image cannot establish completion: the broadcast may
still be live and growing, so its saved position is retained. Unknown/invalid
durations and unfinished positions are also retained. Network argument durations are
seconds converted to Long milliseconds; downloaded videos use their own duration,
not the source VoD offset. No eager database reset is needed; normal playback saves
the new position. Explicit timestamp requests bypass automatic resume, and existing
active-session restoration remains unchanged. Do not apply the completion rule to
quality changes or minimize/restore of an active player.

The optional legacy `ExoPlayerService` supplements Android media-session dispatch
only for unhandled previous/next key-down events. It checks seek command availability
and ignores cancelled/release events. The default Media3 path and platform
MediaPlayer path are unchanged; never introduce a second handler that repeats an
already-handled seek. Test actual headset and background dispatch on devices.

## Side gesture feedback

Brightness and device-volume feedback use larger 64 × 216dp edge pills on player
surfaces at least 600dp wide, with 32dp icons and 8dp level tracks. Icons use
explicit AppCompat image views with white tint for readability over video. Surface classification
uses the measured video/player width, so side chat and resized windows can return
to the unchanged compact horizontal treatment. Edge pill height is capped to 45%
of the measured player height; padding, icon and spacing shrink together below
96dp pill height to keep a visible level track in short windows. Returning to a
compact or non-side gesture resets icon size, spacing and pill corners.

`PlayerGestureFeedbackRenderTest` checks native Android layout, edge placement,
progress/accessibility updates, very short players and compact restoration, and
renders phone/tablet feedback previews. Device gesture timing, playback and actual
window insets still require the human player matrix below.

## Live rewind controls

The live-rewind button is available for maximized live streams in portrait and
landscape. Portrait uses a separate stats row below the actions so uptime/viewer
count do not overlap optional volume/audio controls. The center play/pause shifts
up only as needed to clear that row. Rotation restores the original landscape
rules; minimized players and recording replays do not get a live-rewind button.
`PlayerLiveRewindControlsTest` renders compact/RTL/200% text and landscape controls.

## VoD seek previews

`SeekPreview` draws a frame from a Twitch storyboard sprite sheet while the seek
bar is dragged; it never seeks the decoder during the drag. `VideoStoryboard`
derives the optional index from the existing animated-preview URL and validates
the grid before mapping positions to cells. Missing or processing storyboards
silently leave normal seeking available. Online finished VoDs with preview
metadata are supported; clips, downloads, live rewind and spoiler-free mode do
not request previews.

The preview belongs to the player view. Hiding controls, minimizing, stopping,
PiP and destroying the view cancel its sheet request and drop bitmap references.
Request generations reject late callbacks, including a second drag on the same
sheet. Drawing uses a source rectangle rather than creating a bitmap per frame;
Coil owns cached sheet bitmaps, so they must not be recycled by the view.

`VideoStoryboardTest` checks index parsing and sheet boundaries. `SeekPreviewTest`
checks real Android controls, source-cell rendering, placement, cancellation and
late callbacks. Native renders are under `app/build/ui-previews/seek-preview-*`.
They do not establish actual Twitch availability, touch timing or playback.

## Required Checks For Player Work

Run:

```bash
./gradlew assembleDebug
./gradlew test
```

Also run when resources/UI are touched:

```bash
./gradlew lintDebug
```

For release-risk player work, also verify:

```bash
./gradlew assembleRelease
```

## Optional Google Cast (default Media3 backend)

Enable Google Cast in Player settings, then reopen playback. The route button is
inflated only when enabled; devices without Google Play services keep local
playback. Custom ExoPlayer and platform MediaPlayer backends are unchanged.

Media3 CastPlayer owns the local/receiver switch and stops the inactive player.
ThystTV retains only the current configured MediaSource for a return to local:
VoDs preserve receiver position, speed and pause; live returns to the live edge.
Downloads remain local, including a service-side rejection while casting. Close
stops playback and ends the Cast session. Backgrounding the phone leaves receiver
playback alone. Receiver quality is automatic; phone-only audio processing,
track controls, live rewind discovery and system PiP are unavailable remotely.

The receiver fetches the media URL directly. Phone HTTP headers/proxies are not
forwarded. Receiver codec/network compatibility needs physical Cast QA; enabling
the sender and passing state tests do not establish receiver playback support.

## Optional chat in system PiP

Player settings → Chat in picture-in-picture is off by default. A maximized local
live stream with visible, ready portrait chat requests a 9:16 system PiP window:
video above a read-only message list. VoDs/clips, landscape, minimized, floating
or hinge-split chat, TV and Cast keep the normal video-only behavior. No extra
player or overlay permission is involved.

The window keeps the shape requested before entry. Layout and preference changes
refresh the next request, including when Settings covers a running player. Chat
containers measure/draw only the message path during PiP, so async polls, status,
scroll buttons, emotes and composer cannot cover messages. Normal visibility and
draft text remain intact on return; new messages follow the newest item in PiP.
The composite source hint replaces the video-only hint tracker, retains system
play/pause actions and disables seamless resize for non-video chat content.

## Human QA Handoff

Agents should not claim physical-device QA unless it was actually performed. Instead, list the required human QA from `docs/MANUAL_QA.md`.

At minimum, player work should request human verification for:

- live stream opens
- VoD opens
- live -> live switching
- live -> VoD switching, if touched
- VoD -> live switching, if touched
- minimize / restore
- close / reopen
- orientation change
- PiP/background behavior where relevant
- playback speed menu
- quality menu
- gestures
- floating chat overlay

## Common Regression Smells

- old stream audio continues after opening a new stream
- player view is black after minimize/restore
- controls show stale speed/quality
- more than one player popup is visible, or popup content survives minimize/PiP/rotation
- controls auto-hide while a popup is open or fail to resume auto-hide after dismissal
- sleep timer closes the wrong player
- PiP remains enabled after player close
- orientation recreates UI with stale player state
- gestures conflict with chat or controls
- lifecycle callbacks apply to an old fragment
- quality menu shows raw numeric HLS variants
- floating chat is unreadable in light mode or on bright video

## PR Expectations For Player Changes

A player PR must include:

- changed files
- risk areas
- Gradle checks run
- human QA required
- human QA completed only if actually performed
- screenshots/video when UI changed
- known risks or follow-up issues
