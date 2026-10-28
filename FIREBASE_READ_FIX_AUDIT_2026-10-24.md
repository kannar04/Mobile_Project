# Firebase read fix — focused correctness audit

## Audited paths

- `activityPushOutbox` creation, immediate delivery, retry recovery and terminal state.
- Notification payload privacy and recipient/source authorization.
- Discovery cached rows, lifecycle revalidation and grid/header position mapping.
- Direct and adoption conversation inbox refresh behavior.
- Favorites lifecycle refresh after removing periodic polling.
- Android build, resources, rotation survival and uncaught-crash logging.

## Confirmed defects and fixes

### Concurrent activity delivery could reopen a sent job

Two create-trigger executions could capture the same pending query. Attempt leases prevented a duplicate push, but the slower execution could write `state=pending` from stale job data after another execution wrote `state=sent`.

The immediate trigger now processes only its created document. Terminal updates run in a transaction and update only a document whose current state is still `pending`. A concurrent emulator test runs two deliveries from the same snapshot and verifies one send and final state `sent`.

### Direct inbox could remain stale after polling removal

The normal chat inbox performed only its screen-entry load and ignored the already active conversation-index listener while the direct tab was selected.

The index snapshot now requests one bounded direct-inbox refresh when the direct tab is visible. Adoption inbox refreshes use the same signal. A monotonically increasing request generation prevents an older adoption-inbox response from replacing a newer response.

### Discovery visible-window positions included the ConcatAdapter header

Recycler positions included the search header, while listing rows did not. Revalidation could request one row beyond the visible window and omit the intended preceding row.

The position mapping now subtracts the header offset before selecting up to 20 listing IDs.

### Favorites no longer refreshed after timer removal

The favorites screen registered for the former pulse but did not perform a lifecycle refresh itself. It now refreshes on start while retaining bounded pagination and session-generation checks.

## Verification

- Firebase emulator notification/data tests: 11 passed.
- Concurrent delivery regression: passed; one send, terminal `sent` state.
- Isolated read-budget regression: passed.
- Debug app compilation and packaging: passed.
- Spark app compilation and packaging: passed.
- Debug instrumentation APK compilation and packaging: passed.
- Debug JVM unit tests: passed.
- Spark Android lint: passed with no fatal/error result.
- NE2211 launch/rotation/250-event package monkey: no `AndroidRuntime` crash or native fatal log. One synthetic flip event was rejected by the device with `EINVAL`; the app continued.
- Production `activityDelivery` and `activityDeliveryNow`: deployed with identical source hash `03fc3314c9732ae45a63b0a90840b38be0af4a21`; both report `ACTIVE`.

## Runtime coverage limit

The NE2211 opened the newly installed build at the signed-out authentication screen. Authenticated discovery, favorites and conversation visual screenshots could therefore not be truthfully inspected on that device. The second connected Xiaomi device did not accept the updated APK during this run, so its existing authenticated session was not used with the changed binary. Compilation, lint, isolated data tests, lifecycle source audit, rotation, and unauthenticated crash testing completed.
