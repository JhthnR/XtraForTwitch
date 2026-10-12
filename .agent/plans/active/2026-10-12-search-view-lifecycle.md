# Finish issue 14: release Search pager views

## Goal

Release the destroyed Search pager hierarchy and preserve search/tab behavior
when returning to Search. Resolve the remaining inherited leak in issue #14.

## Non-goals

No player-session changes, updater/settings redesign, dependency update, broad
upstream sync, signing, tagging or publication in this PR.

## Current context

User requested resolving https://github.com/tzii/ThystTV/issues/14 alongside UI
redesign work. Base is master 71356a010. PagedListFragment already unregisters
its observer and clears RecyclerView.adapter on view-lifecycle destruction;
PagedListFragmentLifecycleTest covers that half of the issue. SearchPagerFragment
still clears binding without detaching ViewPager2.adapter; its mediator and page
callback are locally created and do not have explicit view teardown.

The earlier 1.4.0 signed RC remains a tested baseline, but publication was not
authorized. New fixes/design changes require a replacement freeze/build/review.
Keep issue #14, updater redesign and settings redesign in separate PRs.

## Files likely involved

- app/src/main/java/com/github/andreyasadchy/xtra/ui/search/SearchPagerFragment.kt
- app/src/test/java/com/github/andreyasadchy/xtra/ui/search/SearchPagerLifecycleTest.kt
- docs/TESTING.md

## Risks

Risk level: medium. Adapter teardown can affect retained fragment state and
search/tab restoration; keep callbacks scoped to the current view. It adds no
player or network lifecycle owner and touches no private crash log.

## Human approval

Required before implementation: no.
Reason: user explicitly requested resolving #14; narrow, reversible view teardown
is authorized. Closing the issue follows verified acceptance, not code alone.

## Implementation steps

1. Give mediator/page callback explicit view ownership and release them before
   setting the pager adapter to null and clearing binding.
2. Test the real SearchPagerFragment teardown against inflated Android views and
   FragmentStateAdapter lifecycle observers. Check recreated pager behavior.
3. Run focused tests and the required Gradle gates, then the API 36 navigation /
   LeakCanary sequence and player/Stats smoke checks when available.
4. Submit a focused PR; close #14 only when the observed signatures no longer recur.

## Verification

- [x] Focused paging and Search pager lifecycle tests.
- [x] assembleDebug, testDebugUnitTest (818 passed), lintDebug (0 errors,
  348 existing warnings). assembleRelease deferred to the next RC build.
- [ ] API 36 Popular → Search → channel → player → Back repetition; no recurrence
  of the six issue #14 signatures.
- [ ] Live/VoD, switching, minimize/restore, floating chat and Stats smoke.

Human QA completed: none for this change yet.

## Progress log

- 2026-10-12: Confirmed paging observer half already fixed; Search pager teardown
  still missing. Created scoped branch codex/fix-search-view-release.
- 2026-10-12: Fixed a late doOnLayout callback dereferencing a cleared binding
  (compare against the captured binding, not the throwing getter). Gate green.

## Decisions

- Retain existing paging state and preferences. Avoid replacing adapters or
  broad UI classes merely to solve their destroyed-view ownership.

## Final PR summary draft

Release Search pager adapter, mediator and page callback with its view, so the
fragment lifecycle does not retain a detached pager after leaving Search.
Validation and acceptance evidence will be added before issue closure.
