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

## Source provenance

All ports are manual adaptations, not whole-fork merges or claims of patch
identity. The existing AGPL-3.0 license and Xtra attribution remain.

- Live notifications: [thiscallnet/Xtra](https://github.com/thiscallnet/Xtra)
  `fbffe567381dfca9970860e2976ab0021c2602eb` persistence, fallback and dedup policy.
- Channel Points rewards: thiscallnet/Xtra `fbffe567381dfca9970860e2976ab0021c2602eb`
  custom reward flow.

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

Releasing remains a separate approval and verification step.
