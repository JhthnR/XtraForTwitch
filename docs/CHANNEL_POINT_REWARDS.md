# Custom Channel Points rewards

The live-chat composer has a 48dp, labelled rewards button. It opens a bounded,
scrollable Material dialog that retains standard surfaces in floating chat. The
existing chat-bar visibility preference is respected. Rewards require a web/GQL
account and account identity; Helix-only authentication cannot redeem them.

Select a custom reward to inspect its title, exact point cost, prompt and required
message (up to 500 characters). Selection and typing do not send anything.
Only **Spend N points** confirms a redemption. A fresh catalog is checked before
sending: a price/title/prompt/input change, missing or unavailable reward, insufficient
balance, or an account change requires another review. No locally guessed balance
is applied after success; use Refresh for the current server balance.

## Failure and lifecycle policy

- Repeated clicks/key repeats share one in-flight attempt.
- Cancelling or dismissing during the preflight read cancels that attempt.
- Once submitted, closing the dialog does not promise cancellation. The parent
  chat ViewModel owns the request and its result across dismissal/reopening.
- Rotation/backgrounding retains the ViewModel draft and in-flight work. A restored
  process never automatically submits a reward.
- Cancellation after dispatch, malformed/absent success payloads, transport and
  server (5xx) errors, and other top-level GraphQL errors have an **unknown**
  outcome, not a retryable failure.
  The user is told points may have been spent and to inspect Twitch.
- Unknown reward IDs are blocked for that account/channel in the current chat's
  ViewModel and restored SavedState. This is not a global transaction ledger:
  leaving that chat and deliberately starting a new one permits a new explicit
  action. The app never retries an uncertain transaction itself.
- An explicit mutation error payload is a rejection. So is a refusal Twitch
  returns before running the mutation: HTTP 400/401/403/429 or a failed integrity
  check. Retrying still requires a refresh, re-selection and another
  cost-labelled confirmation.
- Error details, authentication headers and typed reward messages are not logged.

## Transport and provenance

Manually adapted from `thiscallnet/Xtra` snapshot `fbffe567381dfca9970860e2976ab0021c2602eb`:
`ChannelPointsDialog`, `model/ui/ChannelPoints`,
`model/gql/chat/ChannelPointContextResponse`, and the custom reward mutation in
`GraphQLRepository`. The catalog uses its `ChannelPointsContext` persisted-query
hash and the custom mutation uses `RedeemCommunityPointsCustomReward` with an
explicit transaction ID. These are Twitch private web operations and may change.
No real account spending was performed to validate them.

Catalog reads use ThystTV's configured backend. Only the spend mutation uses an
isolated OkHttp client with connection retry and redirects disabled and a one-shot
request body. This prevents transport follow-ups from silently replaying a spend;
Hilt, minSdk 23 and existing transports for other features are unchanged.

Automatic/emote-unlock rewards, highlighted/subscriber-only message purchases,
watch streaks, polls, predictions, Drops and donor chat/player rewrites are outside
this port.

## Verification

Focused tests cover catalog/input/availability parsing, mutation request fields,
single-shot transport, explicit consent, fresh-price validation, rapid confirmation,
preflight cancellation, post-dispatch uncertainty, account changes, chat-scoped
restoration, draft retention, and labelled scrollable layouts at enlarged fonts.
A direct JDK 21/Kotlin 2.3 compilation of the production DTO/model passed 21
constructor-based catalog/outcome assertions on October 4. This does not run the
serialization parser, ViewModel, transport, Android resources or JUnit suites.
All four touched/new XML resources parse, and `git diff --check` passes. The 35
focused JUnit tests and Android assembly/lint remain pending the environment's
SDK setup and user-approved license acceptance; the parent implementation task
records subsequent results centrally.

Required human QA: authenticated plain/text rewards; no/insufficient balance;
paused/out-of-stock/changed-cost rewards; login expiry; offline preflight; uncertain
post-submit connection loss; Cancel/Back/reopen; rotation/background/process restore;
long prompts at 200% text; D-pad focus/Center/Back; phone/tablet/resized windows;
floating chat show/hide/drag/resize; live/VoD, stream switch, minimize/restore,
close/reopen, PiP/background, speed/quality and gesture regression smoke.
Native layout tests do not certify physical-TV focus or live Twitch behavior.
The draft-retention unit test calls the ViewModel directly; actual Hilt
DialogFragment rotation/dismiss/reopen/restoration and remote navigation need the
integration pass above. They have not been exercised on an Android runtime.
