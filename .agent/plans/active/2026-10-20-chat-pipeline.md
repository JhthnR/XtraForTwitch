# Chat Pipeline Smoothness

## Goal

Busy chats scroll and update smoothly. Messages arrive in short batches instead of one
RecyclerView notification per message, and the message list can never disagree with the
adapter. Emote and 7TV user lookups no longer scan whole lists for every word. Late emote
loads never paint an old message into a recycled row.

## Non-goals

- No visual change to chat rows, emotes, badges, name paints or translations.
- No chat v2 rewrite (thiscallnet's `ui/chat/v2`). This keeps the current adapter and
  `ChatAdapterUtils.prepareChatMessage`.
- No change to message limits, recent-message loading, replay timing or the
  message/reply dialogs beyond feeding them the same messages.
- The existing per-callback `addTranslation` behavior in `ChatAdapterUtils.loadImages` is left as is.
- Autocomplete and `allEmotes` de-duplication stay as they are (follow-up).

## Current context

- `ChatViewModel.onMessage` added messages to `chatMessages` on the chat socket's IO
  thread. The adapter heard about it later through `newMessage: MutableSharedFlow`, and
  the fragment removed evicted messages in its collector. Layout could run in between,
  which risks RecyclerView inconsistency crashes.
- The collectors only run while the view is STARTED. `stopLiveChat()` runs from
  `ChatFragment.onStop`, after collection stopped, so its clear was emitted to nobody and
  the adapter was never told the list was emptied.
- Each message caused its own `notifyItemInserted`, layout and scroll.
- `prepareChatMessage` looked up every word with `thirdPartyEmotes.find { it.name == word }`
  (thousands of 7TV/BTTV/FFZ emotes), and each message's 7TV user with `stvUsers.find`
  over every 7TV user seen in the session.
- `ChatAdapterUtils.loadImages` rebound the whole row after every image, including images
  served synchronously from Coil's memory cache during `onBindViewHolder`. Callbacks
  captured the holder, so a slow image could bind an old message into a recycled row.
  `fragment.resources` in the callback could throw after the fragment detached.

## Files likely involved

- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatMessageStore.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/EmoteNameIndex.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatRowImageBinder.kt` (new)
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatFragment.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ChatAdapter.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/MessageClickedChatAdapter.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/ui/chat/ReplyClickedChatAdapter.kt`
- `app/src/main/java/com/github/andreyasadchy/xtra/util/chat/ChatAdapterUtils.kt`
- Unit tests for the three new classes

## Risks

- Ordering: a clear, recent messages and live messages arriving close together must keep
  their order. `ChatMessageStore.drain` applies queued changes in order, and the tests
  cover mixed batches.
- Latency: messages can wait up to one batch interval (100 ms) before showing.
- Auto-scroll: the list scrolls to the end after a batch only when the user is not
  dragging and the jump-to-bottom button is hidden, as before.
- Emote priority: the name index keeps the first emote in the sorted list, like `find`.
- Floating chat and dialogs share the adapter code, so both need QA.

Risk level: medium

## Human approval

Required before implementation: no

Reason: Chat-only, reversible, no player lifecycle, release or upstream sync. The user
asked for the agreed step list to be implemented as local stacked commits.

## Implementation steps

1. Add `ChatMessageStore`. The ViewModel queues changes from any thread and drains them
   on `Dispatchers.Main` at most every 100 ms. `ChatFragment` sets a synchronous
   `messageListener` from view creation to `onDestroyView` and applies the drained
   changes as range notifications. Replace the three SharedFlows.
2. Add `EmoteNameIndex`, rebuilt inside every `thirdPartyEmotes` mutation. Store 7TV users
   in a map keyed by user id, and read personal emote sets by key.
3. Add `ChatRowImageBinder` to the three chat adapters. Load images with the row's
   context and resources instead of the fragment's.
4. Unit tests, CHANGELOG, then `assembleDebug`, `test` and `lintDebug`.

## Verification

Automated checks:

- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew test`
- [ ] `./gradlew lintDebug`

Human QA required:

- [ ] Busy live chat (10k+ viewers) for 5 minutes: smooth scroll, no gaps, no duplicates, no crash.
- [ ] Scroll up in busy chat: the list stays put and the jump-to-bottom button works.
- [ ] Recent messages appear above live ones when joining.
- [ ] Minimize/background with "keep chat open" on and off, then restore: chat is consistent and continues.
- [ ] Close and reopen a stream, and switch streams: chat clears and refills.
- [ ] VoD chat replay: seek back and forward; chat clears and refills in order.
- [ ] 7TV/BTTV/FFZ emotes, zero-width overlay emotes, animated emotes, 7TV paints, badges and personal emotes render as before.
- [ ] Fast fling through 600 messages with many emotes: no row shows another message's text.
- [ ] Floating chat over video, message-clicked and reply dialogs update while open.
- [ ] Deleted (CLEARMSG) messages are still marked, including right after they arrive.

Human QA completed:

- [ ] Not yet performed.

## Progress log

- 2026-10-20: Created plan as step 3 of the performance stack (`perf/3-chat-pipeline`).

## Decisions

- Decision: Batch on the main thread with a plain listener, not a SharedFlow.
  Reason: The list and the adapter notification must change in the same main-thread
  task, also while the view is stopped.
  Alternatives considered: `ListAdapter`/`AsyncListDiffer` (diffing 600 rows per batch
  is wasted work for append-only chat), thiscallnet's chat v2 rewrite (too broad).
- Decision: 100 ms batch interval, with the first message after a quiet period drained on
  the next main-thread turn.
  Reason: Quiet chats show messages immediately. Busy chats get at most ten layout
  passes a second.

## Final PR summary draft

Summary: Batch chat list changes on the main thread, index third-party emotes and 7TV
users, and coalesce emote rebinds per row.
Tests: `ChatMessageStoreTest`, `EmoteNameIndexTest`, `ChatRowImageBinderTest`, full `test`, `lintDebug`.
Human QA: See the list above.
Risks: Up to 100 ms extra chat latency under load. Ordering and auto-scroll need QA in busy chats.
