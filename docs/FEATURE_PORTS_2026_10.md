# October 2026 selective feature ports

This is an unreleased development change based on ThystTV 1.3.1. It does not
change versionCode/versionName, minSdk 23, Hilt, WorkManager, Media3 1.9.3,
release signing or distribution.

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
cache or turn unknown/muted notifications on. Account changes and worker
cancellation are checked before posting.

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

A separate metered-Wi-Fi preset supports hotspots/data caps. Its default follows
the mobile-data preset; unmetered Wi-Fi retains its existing preset. Network
capability changes reapply automatic policy for live streams without overriding a user's explicit
quality choice during the session. An audio-only default chosen by the network
policy switches back to video on Wi-Fi; during background audio, restore uses the
new network's default. VoD and recording playback use the current network preset
on initial selection; capability changes do not interrupt their active
scrubbing/playback. Audio-only and chat-only transitions retain ThystTV's
existing controls and readable labels.

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
  quality labels `703437feee29435f9aa341dac17ad1358d059286`.
- Metered Wi-Fi: thiscallnet/Xtra [PR #433](https://github.com/thiscallnet/Xtra/pull/433),
  `42ab1d2cb0a07842748c278dfd874e0fddb43208`.

The donor's new minimum SDK, GeckoView, Cronet/service rewrites, EventSub,
multiview, local clips, captions and dependency upgrades are not included.

## Verification and remaining gates

Local debug/release assembly, the configured unit suite and debug lint passed
after independent correctness/lifecycle reviews and the October 9 review fixes. See the
[validation report](FEATURE_PORTS_VALIDATION.md) for exact results and limits;
source review and unit tests are not device playback evidence.
No physical device/emulator playback or authenticated Twitch mutation has been
performed for this change.

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
  and bitrates; 1080p30 versus 720p60; audio/chat-only and restore
- Wi-Fi ↔ metered hotspot ↔ mobile; metering changes on the same network; VPN;
  manual-quality override and background reconnection
- No overlapping old audio; minimize/restore, close/reopen, PiP, sleep timer,
  gestures, floating chat, replay chat timing and local viewing totals

Releasing remains a separate approval and verification step.
