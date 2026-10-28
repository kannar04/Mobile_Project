# Firebase read-amplification fix — 24 October 2026

## Scope

This patch was implemented and verified against the isolated Firebase project `demo-anilove-isolation`. No production Firebase data, rules, indexes, functions, or schedules were changed.

## Changes

- Replaced the 25-second `BrowsePulse` timer with a process-local invalidation registry. Home, discovery, adoption detail cards, favorites, and saved pets retain their existing initial/lifecycle loads but no longer poll while the app is idle.
- Kept the currently rendered discovery cards on screen during lifecycle revalidation. Rows are replaced only after a successful response, avoiding the loading-state flicker and preventing a temporary failure from wiping cached UI.
- Removed the direct-message inbox's 60-second timer. The inbox loads when it becomes visible and when the user explicitly pages or retries.
- Added an `activityPushOutbox` create trigger for immediate activity notification delivery.
- Reduced the activity notification recovery schedule from every minute to every 60 minutes. The schedule remains as recovery for retryable or interrupted delivery.
- Added configurable Firebase emulator endpoints to the debug build so isolated Android/backend verification does not require changing production configuration.

## Isolated measurement

Synthetic fixture: 7 pending adoption outbox rows, 20 visible listings, and 20 distinct public profiles.

| Path | Before | Patched model | Change |
|---|---:|---:|---:|
| Idle foreground listing/profile refresh | 5,760 documents/hour/device | 0 documents/hour/device | 100% recurring reduction |
| Initial listing/profile screen entry | 40 documents | 40 documents | Preserved bounded load |
| Activity recovery executions | 1,440/day | 24/day | 98.33% fewer scheduled scans |
| Adoption recovery with 7 pending jobs | 21,600 reads/day at legacy minute cadence | 360 reads/day at hourly cadence | 98.33% modeled reduction |

Firestore Emulator does not expose billing counters. The figures combine the actual returned document counts from the isolated fixture with the configured invocation intervals. Listener reconnects, security-rule dependent reads, retries, and Storage image traffic are excluded.

Raw output: `build/read-budget-isolation.json`.

## Verification

- `:app:assembleDebug`: passed.
- `:app:assembleSpark`: passed.
- Firebase activity notification behavior tests: 9 passed.
- Isolated read-budget test: 1 passed.
- JavaScript syntax check for `functions/index.mjs`: passed.

## Production deployment

Deployed to Firebase project `aniloveproject` after the isolated verification:

- `activityDeliveryNow` — created in `asia-east2`; Firestore document-created trigger on `activityPushOutbox/{job}`; retry enabled; state `ACTIVE`.
- `activityDelivery` — updated in `asia-east2`; hourly recovery schedule; state `ACTIVE`.

The deployment used an explicit two-function allowlist. Firestore rules, indexes, Storage configuration, other Functions, and production documents were not deployed or modified. Post-deployment startup probes passed and the inspected logs contained no runtime errors.
