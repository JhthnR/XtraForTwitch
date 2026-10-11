# Manual QA Handoff Checklist

Use this checklist for PRs that affect player behavior, UI, layouts, release builds, or visual presentation.

Agents should mark **Required** when a human should test an area. Mark **Completed** only when a real human/device check was actually performed.

## Build Under Test

- Branch:
- Commit:
- APK/build:
- Device/Android version, if actually tested:
- Tester, if actually tested:
- Date, if actually tested:

## Checklist

### Website presentation

- [ ] On a physical phone and laptop, accept the visual layout in both themes;
  verify readable text and no horizontal overflow, including enlarged text.
- [ ] Select Floating chat, Player controls, Local stats, and Room to watch.
  Open the full-size control previews; confirm original images stay readable.
- [ ] Use keyboard arrows/Home/End and check focus. Open/close the demo by its
  close button, Escape, and the backdrop; focus returns to its trigger.
- [ ] Play the demo with native video controls. Closing it or hiding the page
  pauses playback. Confirm touch and audible playback on a real browser/device.
- [ ] Pause decorative motion, reload, and check persistence. System reduced
  motion stops animation; theme choice persists. Check JavaScript/storage denied.
- [ ] Follow GitHub Releases and the APK verification guide. Check downloads on
  the actual device; website browser QA is not Android installation evidence.

These checks are separate from player/device QA. Website sample-data previews
and pre-1.3 footage are labeled and do not certify current app behavior.

### October 11 1.4.0 candidate

On October 11 the maintainer installed the device-test debug build
(`com.tzii.thysttv.debug`, `1.3.1-DEBUG`, code 13, local merge `368426903` of
#30-#38) over the previous debug build on a Honor Magic V5 (Android 16). They
went through the supplied checklist (pinch pill and Fill outline, full-screen
rewind button and 1x catch-up, spoiler-free VoDs, free zoom across rotate, fold
and PiP, settings drag, gesture guide in PiP, quality, notifications, rewards)
and reported that everything seemed fine. No per-item breakdown was supplied.
LeakCanary caught a closed-player leak during that session; its fix (#39) was not
in that build. The exact signed 1.4.0 upgrade and release matrix remain pending.

### September 25 large-screen and upstream update

On September 27 the maintainer reported that the delivered debug build seemed
fine. No device/backend/scenario breakdown was supplied, so the detailed rows
below remain a regression reference rather than a claim that every case passed.

On September 30 the maintainer also reported that the latest debug APK with TV
support seemed fine and proposed preparing 1.3.1. The supplied artifact identifies
as `com.tzii.thysttv.debug`, version `1.3.0-DEBUG`, code 12. No additional device or
scenario breakdown was provided. Record this as general debug smoke evidence;
the exact signed 1.3.1 upgrade and release matrix remain pending.

- [ ] On tablet and landscape phone, brightness/volume side feedback is easy to
  see over bright/dark video, with larger icons and a thicker level track. Check
  both ends of each level; its pill must stay within the video and system insets.
- [ ] Resize to short split-screen and narrow side-chat video; return to full
  screen. Compact feedback, seek/speed/pinch indicators and chat touches remain
  correct. Repeat with large fonts and RTL.
- [ ] Live/VoD playback, stream switching without old audio, minimize/restore,
  close/reopen, PiP/background, speed/quality, all gestures, floating-chat
  open/drag/resize, and Stats load/rotate/range smoke.
- [ ] Refresh individual and multiple saved bookmarks, reopen Saved/relaunch and
  confirm updated title/duration/art persists without duplicate entries.
- [ ] Resume a VoD while its broadcast is still live, with a saved position past
  the listed duration. It must retain position. Repeat via cached bookmarks;
  completed VoDs with final metadata still restart, explicit timestamps win and
  downloaded segments retain their own duration behavior.
- [ ] With no API tokens but a retained account ID, player/channel buttons reflect
  local follows. Repeat signed in through GQL and Helix fallback.
- [ ] Open chat profiles using moderator permissions and Helix fallback; follow
  dates belong to the selected viewer. Ordinary accounts without follower permission
  still show profiles. Check profiles in floating chat and ordinary followed lists.
- [ ] `/color` displays the current color through HttpEngine and legacy Cronet
  where supported; existing OkHttp/Cronet behavior remains intact.
- [ ] German/Japanese/Spanish/Russian UI: inspect changed settings, quoted labels,
  plural/count values, formatted percentages and Russian ThystTV login text.

### September 27 Android TV remote navigation

The maintainer has no TV available. Codex checked an isolated Android TV
emulator and native Android view/key tests. Physical TV/OEM remote and codec
behavior remain unverified; emulator results are recorded separately in
`TESTING.md`.

- [ ] Remote-only launch: visible focus, left/right across enabled bottom items,
  CENTER selects; UP enters content and Back at a root page returns to navigation.
- [ ] Popular, Following, Games, Saved and Stats; tabs, long feeds, empty pages,
  search, settings and dialogs; nested Back returns normally and repeated Back exits.
- [ ] Following/Saved tabs, Stats ranges and channel Videos/Chat/Clips tabs remain
  fully visible while focused, after selection and after scrolling the page.
- [ ] Channel entry shows visible focus; profile details collapse while toolbar
  and tabs remain available and leave room for video cards.
- [ ] Live/VoD controls: reveal with D-pad, play/pause, seek, quality, speed,
  volume, More, popup Back/focus restoration; keyboard slider changes persist.
- [ ] Minimize, focus/restore mini-player, close/reopen, stream switching,
  PiP/background and chat controls. No focus reaches covered browse content.
- [ ] Phone/tablet gestures and floating-chat interaction remain regression areas.

### Following view cleanup

- [ ] Open available Following tabs, leave and return repeatedly, then rotate or
  recreate the screen. Tabs and lists still load; first insertion keeps position
  and later prepends still scroll to the top.
- [ ] After leaving Following, inspect a fresh LeakCanary result from the rebuilt
  APK. The previous destroyed pager/RecyclerView retention groups must not recur.
  Keep heap dumps and private analysis out of public issues and commits.

### Pinch free zoom

- [ ] Near-square foldable inner screen (or a tablet in landscape): pinch out
  partway, release, and confirm the picture stays centered at that zoom. The pill
  reads "Zoom N%" between Fit and Fill, with a haptic tick at each end when
  enabled. Open another stream or VoD: the zoom is kept.
- [ ] Landscape phone: a short pinch still reaches Fill and back to Fit; ends snap
  within about 5%. Pinching past either end resists and settles back.
- [ ] The pill keeps one width through Fit, Zoom N% and Fill, its bar moves
  smoothly, and reaching Fill flashes a white outline once. Brightness, volume,
  seek and speed pills keep their usual font and size.
- [ ] While zoomed, rotate, fold/unfold, open/close side chat, resize split screen,
  minimize/restore, enter/leave PiP and close/reopen. Portrait, the mini-player and
  PiP show plain Fit; full-screen landscape returns to the zoom, never past Fill.
- [ ] More → Display mode shows the zoom as the current choice; choosing Fit, Fill
  or Stretch clears it. Stretch and Android 6 keep the Fit/Fill toggle.
- [ ] Live, VoD and clips on all three backends; a double tap followed by a pinch
  still restores chat.

### Half-folded layout

- [ ] Foldable inner screen, landscape, side chat open: half-fold like a laptop.
  Video fills the top half above the hinge, chat the bottom half; unfolding
  returns to the side chat without a flash of the wrong size.
- [ ] Tabletop with floating chat: chat docks below the hinge and floats again
  in its old place after unfolding. If the chat button is used while docked
  (it cycles hidden and docked only), unfolding keeps the sidebar.
- [ ] Tabletop with chat hidden: video stays above the hinge. Portrait tabletop
  behaves the same instead of the 16:9 stack.
- [ ] Tabletop keyboard: tap the chat input; the input and a few chat lines stay
  above the keyboard; switch to its emoji panel or change keyboard height and
  confirm the input stays reachable. Closing it restores the split. Repeat in
  a resized window; video stays above the hinge while typing.
- [ ] Book posture (hinge down the screen) with chat showing: video on one side,
  chat on the other, nothing across the hinge; with chat hidden or floating the
  video keeps the whole screen. Check an RTL language puts chat on the left.
- [ ] While half-folded: minimize/restore, PiP and back, rotate, close/reopen,
  switch streams, pinch zoom, double-tap chat, quality/speed/More popups.
- [ ] Settings → Player settings → Half-folded layout off: postures keep the
  normal layout. Phones without a hinge and Android TV are unchanged.

### Spoiler-free VODs

- [ ] Off (default): VoD seek bar, length, Past Broadcasts/Saved/Downloads
  durations and watched bars look as before.
- [ ] On: a VoD and a downloaded VoD hide the seek bar, position and length;
  swipe-to-seek shows only the new position; double-tap seek still works.
- [ ] On: Past Broadcasts, Saved and Downloads thumbnails hide the duration and
  watched bar. Live streams and clips play as before.
- [ ] On: the VoD download dialog shows no duration or end-time hint, and an end
  time past the VoD still shows the error; the player games list shows game
  names without positions or durations, and tapping a game still seeks.
- [ ] Known limit: the media notification and lock-screen controls can still show
  the length.

| Area | Required | Completed | Notes |
| --- | --- | --- | --- |
| App launches | [ ] | [ ] | |
| Navigation smoke test | [ ] | [ ] | |
| Live stream opens | [ ] | [ ] | |
| VoD opens | [ ] | [ ] | |
| Player closes/reopens | [ ] | [ ] | |
| Live -> live switching | [ ] | [ ] | |
| Old audio does not continue after switch | [ ] | [ ] | |
| Mini-player -> new stream | [ ] | [ ] | |
| Minimize / restore | [ ] | [ ] | |
| PiP/background behavior | [ ] | [ ] | |
| Playback speed display/control | [ ] | [ ] | |
| Quality menu/selection | [ ] | [ ] | |
| Chat-only/audio-only restore | [ ] | [ ] | |
| Gestures: brightness/volume/seek | [ ] | [ ] | |
| Floating chat: open/drag/resize | [ ] | [ ] | |
| Floating chat readability over video | [ ] | [ ] | |
| Portrait phone layout | [ ] | [ ] | |
| Landscape phone layout | [ ] | [ ] | |
| Wide/tablet layout | [ ] | [ ] | |
| Split-screen/resized window | [ ] | [ ] | |
| Stats screen | [ ] | [ ] | |
| Stats filters/charts | [ ] | [ ] | |
| Updater/changelog Markdown and actions | [ ] | [ ] | |
| README/site screenshots render | [ ] | [ ] | |
| Icon/banner assets look correct | [ ] | [ ] | |
| No private account data exposed | [ ] | [ ] | |

## Evidence

Screenshots, videos, logs, or APK links:

-

## Known Issues / Deferred QA

-


## 1.3 release checks

- [ ] Landscape, maximized player, double-tap chat and floating chat enabled: from
  each of hidden, sidebar and floating chat, tap once and immediately begin a
  two-finger pinch (first finger lands within the double-tap interval). After the
  scale threshold is crossed, chat must return to its starting mode, in the same
  container, with the correct player width and chat icon. Verify it stays restored
  after animations settle and both fingers lift; repeat with a cancelled pinch.
- [ ] Repeat from hidden/sidebar with floating chat disabled. A normal double tap
  must still advance one mode; a normal pinch must leave chat unchanged. Reopen
  playback and confirm the saved chat-open choice was not changed by the pinch.

- During an updater download, cancel/retry and rotate/background/restore; one current
  attempt should remain and stale work must not launch an installer.
- Interrupt a transfer and verify retry/browser recovery. Deny/grant install permission;
  cancel/confirm the OS installer without an automatic reopening loop.
- Check Markdown, action reachability and scrolling at 200% text and in resized windows.
- Process death requires a fresh explicit attempt; do not expect download resumption.
- Test an exact signed 1.3 APK upgrading official 1.2.1, retaining data and migrating
  landscape display mode. Repeat the player, gestures, floating chat, Stats and launcher
  matrix on that same candidate. See RELEASE_1_3_REVIEW.md and RELEASE_PROCESS.md.
