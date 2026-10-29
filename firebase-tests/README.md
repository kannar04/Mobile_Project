# F42 foundation checks



Accepted contract: planning `analysted/F42-firebase-services-trusted-operations-cost-and-security.r1.md`.

These Node packages are local testing tools, not an app server. The Android app

remains Java/XML. No live deployment or billing activation is part of setup.



Debug uses the checked-in synthetic `app/src/debug/google-services.json`, project

`demo-anilove`, and fixed Android-emulator host `10.0.2.2` (Auth9199/Firestore8180).

Emulator listeners bind host loopback only. Release uses the ignored existing

course configuration, but `COURSE_PROJECT_VERIFIED=false` blocks initialization.

Automatic Firebase initialization is removed in the merged manifest. A course

operator must verify project ownership/package, Firestore Standard/location,

plan, providers, protected fixtures and deployed rules before that gate changes.

Do not enable a cloud provider or deploy rules merely to run these local checks.



From this folder:



```powershell

npm.cmd ci --no-audit --no-fund

npm.cmd test

node verify-build.mjs

```



Use Node20+ compatible pinned rules-test5/Firebase12 packages and CLI15; Java21+

for the Firestore emulator. `package-lock.json` records resolved dependencies.

Rule fixtures bypass rules only during operator seeding. Tested commands use
normal user/visitor contexts. A separate test authenticates through the actual

Auth emulator; no Google/provider login feature is implemented here.

On this workspace the current emulator download stalled. The verified run used
the existing Firestore1.19.8 cache with CLI's supported version override:

```powershell
$env:CI = 'true'
$env:FIRESTORE_EMULATOR_VERSION = '1.19.8'
npm.cmd test
```

This is older-emulator evidence; repeat against the current emulator when its
download is available. It does not prove deployed cloud rules or indexes.


From app root:



```powershell

.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest

.\firebase-tests\node_modules\.bin\firebase.cmd emulators:exec --project demo-anilove --only auth,firestore "gradlew.bat :app:connectedDebugAndroidTest"

```



The second command requires a running Android emulator with Internet access to

host loopback through `10.0.2.2`. Instrumentation covers Application startup,

Activity recreation/SavedStateHandle, memory cache, Android repository retries,

account changes and offline recovery. Compiling its APK is not runtime evidence.

Real phones require a separately reviewed host configuration; arbitrary endpoints

are intentionally not user configurable.



The app container is `AniloveApplication.foundation()`. It exposes initialization

status, session gateway and repository. Initialization performs no diagnostic

writes and adds no user route. F42's ViewModel retains one explicit intent ID,

avoids duplicate submissions during recreation, and clears private result/ID on

session invalidation. Other domain collections are denied, pending their own

feature approvals. Cloud media, jobs, notifications and App Check are absent.



Private reads and commits are server-only. Public config can explicitly use

memory cache and returns CACHED rather than SERVER. Commands time out after15s

and transactions attempt at most3 times. A timeout reports CHECKING_OUTCOME and

does not cancel the underlying commit: retain/retry the same operation ID.

Deletion is active-owner-only, online, single-record and idempotent. Query pages

are20 records with a timestamp/ID cursor; no Firestore snapshot listeners exist.

Auth's persisted preferences are excluded from backup/device transfer; logout

does not claim to erase all previously downloaded SDK information.



No real personal data belongs in these fixtures. Do not print config keys,
credentials, tokens or provider exception payloads in evidence.

## F03 profile checks and operator boundary

`npm test` runs foundation/auth regressions and profile rules tests sequentially
because both suites clear the same synthetic emulator database. F03 checks private
ownership, strict schemas, verified active submission, public allowlist guards,
hidden default, restricted-owner hide, stale versions, retry IDs, owner history
pages, unchanged approved snapshots, review/hide races and cache limits.

`profile-publisher.mjs` is emulator-only trusted synthetic-fixture tooling, called
under the test environment's rules-disabled operator context. It requires the
fixed demo-anilove project and loopback emulator. It reads the exact submitted
revision, protected subject and current controls in one transaction, constructs
the public whitelist, appends immutable private review and never changes owner
visibility. Repeated publication checks the matching immutable review. This
does not create a cloud operator service or mobile reviewer privileges.

For a future verified course operator: inspect the exact submitted revision and
public audience; correct contact/address content; verify current active authority,
reviewer identity/capacity and version. Use a trusted transaction with the same
whitelist/history contract and current controls. Changes-requested or withdrawal
history remains private. Never publish a stale screenshot, copy private maps,
write owner visibility or overwrite a prior review. Cloud execution/access is
unverified and no cloud publication or deployment has been performed here.

Native entry: Main → Tài khoản → Hồ sơ của bạn, after account-shell setup.
Unverified/restricted owners can edit private data and hide; submission/reveal
have separate gates. If visibility has not been saved, explicitly save hidden
before revealing an existing approved profile. Instrumentation includes private
field persistence flags, rotation keeping memory edits without writing contact,
and logout clearing the profile ViewModel. These require a device and are not
proved by Node tests or test APK compilation. Airplane-mode/unknown-write outcome,
TalkBack, large text, dark appearance and actual conflict dialog Back remain
required runtime checks.

## F06 identity, claim and operator checks

npm test includes pet.test.mjs sequentially with foundation/profile suites. pet-protocol.mjs exercises seven linked writes, revisions and immutable receipts through normal clients; pet-operator.mjs uses emulator-only trusted fixtures for exact review/hold/archive/duplicate/private-response decisions. Explicit independent/current-verified inputs are synthetic assumptions, never live verification. A temporary in-memory rules eligibility probe is absent from production rules. Own page20 indexes are declared, not deployed. Ready-member Android happy path needs a synthetic verified Auth token and operator-active subject; F06 instrumentation compiles private-field/rotation/logout checks but no device execution is claimed. Keep release cloud gate closed and do not enable services/deploy incidental to tests. See planning implementation/reports/F06.r1.md for acceptance/runtime/operator blockers.

## F07 private listing checks

npm test runs listing.test.mjs sequentially with foundation/profile/pet suites. listing-protocol.mjs exercises four-write create, immutable material edit, hide, terminal/slot transitions and explicit working-copy versions using normal client SDKs. Final92 scenarios include29 F07 tests; trusted setup is emulator-only. The reservation race is a labelled future-writer simulation, not F18/F21 implementation. All public/review/participant paths remain denied in this approved private slice. Closed metadata archive checks current protected gates in rules and preserves later slots without granting broader client access; zero domain versions mean not observed/non-mutated. Three F07 Android SQLite/lifecycle tests compile but need a connected target. See planning implementation/reports/F07.r2.md; keep release cloud gate closed.

## F31 private community checks

npm test includes community.test.mjs sequentially: final107 scenarios,15 F31;49 JVM including8 F31. Private atomic draft/revision/receipt, exact retries, readiness, Unicode/allowlist, immutable chronology/hold, terminal archive, owner privacy/cursors and projection cleanup are checked. Public feed/review/like/count/comment/report/news remain denied pending exact F29/F39 contracts. Seeded future projection removal is not a publisher implementation. Two F31 lifecycle tests compile only; device/runtime/course verification outstanding. No SQL social cache/outbox/Storage/backend/deployment. Planning implementation/reports/F31.r1.md contains scope and audit evidence.


## Accepted daily-use patch (2026-10-13)

Current inputs: F42/F01/F03/F31 r2 + F29 r1, parent context14; earlier counts/contracts above are historical. `npm test` includes daily.test.mjs sequentially. Final134 local emulator scenarios pass, including27 new daily cases, plus57 JVM/debug/test APK compilation. Native tests compile only; no device/AVD/system image/driver is available. Cached Firestore1.19.8 is explicitly disclosed.

Production app uses Java/XML/Views and shared Firebase/session services. Basic membership never replaces protected subject/offer authority; old54 pet/listing cases are retained. Report/post snapshots/receipts/counts/versioning/rate/dedup are tested through normal contexts. Enabled rollout/intake policies and operator restrictions are rules-disabled local fixture setup, not a user's permission or F39 capacity proof. No cloud migration/deployment ran; see MIGRATION.md and guarded read-only schema-inventory.mjs.

Ordinary release package is blocked by the missing app/google-services.json. Release Java source compilation explicitly excludes processReleaseGoogleServices; do not call it a configured release APK. COURSE_PROJECT_VERIFIED remainsfalse. Native and real Google/course/provider/rules/index checks plus F39 approval/implementation remain gates. Current coordinated evidence: planning implementation/reports/DAILY_USE.r1.md.
