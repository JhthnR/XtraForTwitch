# October 2026 selective feature ports

These ports, based on ThystTV 1.3.1, ship in 1.4.0. They do not change minSdk
23, Hilt, WorkManager, Media3 1.9.3, release signing or distribution.

New feature copy currently uses English fallback; existing translations are
unchanged. The intentional fallback is scoped to the new strings, following the
existing ThystTV feature-copy convention.

## Viewer behavior

### Live notifications

The existing periodic WorkManager implementation remains in use. Each poll asks
Twitch once for the account's live follows, as 1.3.1 did, rather than paging the
whole follow list. Helix's followed-streams endpoint is the fallback when the
authenticated GQL request fails. The notification preference each live follow
reports is cached per account, separately from local notification opt-ins.
Helix cannot report preferences, so after a failed GQL request only channels last
reported with notifications enabled may alert; failed results never erase the
cache or turn unknown/muted notifications on. An account without a GQL token
alerts only local opt-ins, as in 1.3.1. Account changes and worker cancellation
are checked before posting.

Turning notifications on acknowledges broadcasts that are already live. If that
setup poll is incomplete, later polls finish it quietly, but a broadcast that
started after setup still alerts.

Deduplication keeps the newest acknowledged broadcast start per channel and
retains it across temporary offline or failed polls. A stream is acknowledged
after a successful notification post; permission-denied or failed delivery must
not consume it. An incomplete entry in a response is skipped like an offline
channel instead of failing its whole batch. A failed poll gets a few quick
WorkManager retries, then returns to the normal 15-minute period. This is
polling, not an always-running instant notification service: Android scheduling
and battery restrictions still apply.

### Channel Points rewards

The chat's rewards entry opens custom rewards for the current channel. The flow
shows availability, cost and required text input, then requires an explicit
cost-labelled redemption confirmation. It rechecks the selected reward and
account immediately before submitting. Repeated taps cannot submit duplicate
redemptions. An uncertain network result is displayed as uncertain and is never
automatically retried; users should check their points/reward history before
starting another redemption. A request Twitch refuses before running it (invalid
sign-in, rate limit or failed integrity check) is shown as rejected instead.
See [Channel Points rewards](CHANNEL_POINT_REWARDS.md) for the full policy.

This port excludes built-in reward emote unlocks, Drops, polls/prediction redesigns,
and the donor's full chat/interface rewrite. Existing polling/prediction behavior
is unchanged.

### Quality and metered Wi-Fi

Quality choices keep their complete variant identity (resolution, frame rate,
codec and bitrate where supplied), including duplicate human-readable names.
Selection and downloads must not silently substitute a same-named variant.
An additive nullable Room migration (database version 36) preserves recording
codec and bitrate identity when downloads are stopped and resumed; existing
download rows retain their name-based fallback. Variant playlist URLs are
session-specific and are not persisted. Test upgrades on a backed-up database:
installing an older app afterward is not an automatic schema rollback.
Fallback picks the best resolution, then frame rate, at or below the saved
choice; saved bitrate and codec only choose among variants of that resolution
and frame rate.

Streams that Twitch also delivers in vertical format list portrait variants;
these are not offered, since the player shows landscape video. Clips request the
codecs allowed by the existing codec preference, so HEVC/AV1 clip qualities can
appear, and use the landscape asset when a portrait crop also exists.

A separate metered-Wi-Fi preset supports hotspots/data caps. Its default follows
the mobile-data preset; unmetered Wi-Fi retains its existing preset. Network
capability changes reapply automatic policy for live streams without overriding a user's explicit
quality choice during the session. An audio-only default chosen by the network
policy switches back to video on Wi-Fi; during background audio, restore uses the
new network's default. VoD and recording playback use the current network preset
on initial selection; capability changes do not interrupt their active
scrubbing/playback. Audio-only and chat-only transitions retain ThystTV's
existing controls and readable labels.

### Recording-backed live rewind

Open **More → Rewind live stream**, or the ⏪ button next to volume in the
landscape full-screen player, and choose the beginning or the last 30 seconds,
minute, or five minutes of the available recording. Live playback continues while
ThystTV discovers the current broadcast, matches a recording, obtains playback
access and preflights its playlist. No suitable recording, subscriber restrictions,
an API failure, timeout or a changed broadcast leaves the live player untouched.
If the player is minimized or the app is backgrounded before the search finishes,
the pending search is canceled and live playback remains unchanged. A completed
result dropped while the player is inactive shows a one-time cancellation notice
when the full player returns; a search canceled before completion has no return notice.

A successful handoff uses the existing single-active-player VoD path. It supplies
normal seeking, playback speed, recording chat replay and the existing floating
chat/TV controls. The title labels it as a recording. **Go live** is available on
the toolbar and in More. It replaces the recording through the same guarded
single-player entrypoint; stale callbacks cannot replace a newer user-selected
player or reopen one after Close.

This is a server recording, not local DVR. Availability, indexing delay, duration
and newly appended portions depend on Twitch. The options are measured from the
recording's own media playlist, falling back to Twitch's reported length, and
can lag the live stream. This first port uses the ordinary VoD timeline rather
than the donor's continuously predicted live-edge UI; Go Live remains the
reliable way to return to current live playback.

Faster playback catches up with the recording. When a recording played faster
than 1x comes within a few seconds of its recorded edge, it drops back to 1x and
a "Caught up · back to 1x" pill says so. The edge is checked every 500 ms, also
with the controls hidden, and the window grows with speed so the reset lands
before playback stalls. Speed changes during a rewind are not saved, so the
viewer's VoD speed is unchanged afterward.

Errors and timeouts during the initial handoff return to live. Once the recording
has started, later errors use normal VoD recovery so a network blip does not
discard the viewer's position. If the player is minimized or the app is
backgrounded, recovery waits for restore/resume instead of expanding playback
over browsing. Reattachment checks the actual ready recording source, so a READY
event missed while backgrounded cannot cause a false timeout. Explicit rewind
startup offsets are consumed once and do not override later saved progress.

## Source provenance

All ports are manual adaptations, not whole-fork merges or claims of patch
identity. The existing AGPL-3.0 license and Xtra attribution remain.

- Live notifications: [thiscallnet/Xtra](https://github.com/thiscallnet/Xtra)
  `fbffe567381dfca9970860e2976ab0021c2602eb` persistence, fallback and dedup policy.
- Channel Points rewards: thiscallnet/Xtra `fbffe567381dfca9970860e2976ab0021c2602eb`
  custom reward flow.
- Quality identity: [crackededed/Xtra](https://github.com/crackededed/Xtra), inspected at
  `9f8043e8bdf45e8a2b2bf4cecfa74dc1de8ae0a3`: resolution-first ordering `a70b0897`,
  exact quality/bitrate `2c582bb83621881dd36c4c7f6632587d38e633cb` and duplicate
  quality labels `703437feee29435f9aa341dac17ad1358d059286`; then, at
  `a3cbb0325`, hidden portrait variants `01f981ab0` and clip codecs `edd8ae257`.
- Metered Wi-Fi: thiscallnet/Xtra [PR #433](https://github.com/thiscallnet/Xtra/pull/433),
  `42ab1d2cb0a07842748c278dfd874e0fddb43208`.
- Live rewind: thiscallnet/Xtra `fbffe567381dfca9970860e2976ab0021c2602eb`
  `ui/player/LiveRewind.kt` matching and failure-recovery concepts.

The donor's new minimum SDK, GeckoView, Cronet/service rewrites, EventSub,
multiview, local clips, captions and dependency upgrades are not included.

## Verification and remaining gates

Local debug/release assembly, the configured unit suite and debug lint passed
after independent correctness/lifecycle reviews and the October 9 and 10 review fixes. See the
[validation report](FEATURE_PORTS_VALIDATION.md) for exact results and limits;
source review and unit tests are not device playback evidence.
An API 36 emulator smoke test covered logged-out playback, quality, metered Wi-Fi,
rewind, notification setup and the database upgrade; the
[port tracker](FEATURE_PORTS_VALIDATION.md#port-tracker) lists what each change
still needs. No physical-device playback or authenticated Twitch mutation has been
performed.

Required device matrix before merge/release:

- Android 6 / API 23 and a current Android phone; compact and 200% fonts
- Tablet/wide/resized layouts and Android TV D-pad/Center/Back
- Logged-out local follows, authenticated account follows, muted channels, account
  changes during a poll, permission denied/re-enabled, API outages, an expired
  GQL token with working Helix, and duplicate workers/stream restarts
- Reward text required/optional, insufficient points, disabled/cooling down/full
  rewards, changed cost, rapid taps, cancel/back/rotation, account logout/change,
  failure before send, refusal before execution and uncertain response after send
- Upgrade a database with existing downloads/follows/stats from schema 35 to 36;
  stop/resume a new exact-variant recording and reopen the app
- Live, VoD, clips, all three player backends; quality names shared across codecs
  and bitrates; 1080p30 versus 720p60; audio/chat-only and restore; a stream
  that also broadcasts vertically; HEVC/AV1 clips and clip downloads
- Wi-Fi ↔ metered hotspot ↔ mobile; metering changes on the same network; VPN;
  manual-quality override and background reconnection
- No overlapping old audio; minimize/restore, close/reopen, PiP, sleep timer,
  gestures, floating chat, replay chat timing and local viewing totals
- Rewind absent/stale/subscriber-only recording, stream restart during discovery,
  source/child-playlist failure, background READY, interrupted startup, minimize
  failure then restore, mid-recording network loss, Go Live, close or choose
  another stream during discovery
- Full-screen ⏪ button next to volume, distinct from restart; catching up at 2x/4x
  drops to 1x with the pill before playback stalls, with controls shown or hidden;
  the saved VoD speed is unchanged after Go Live

Releasing remains a separate approval and verification step.
