# Local demo dataset

This is synthetic emulator data for `demo-anilove`, not live Firebase data.
The debug Android app uses `10.0.2.2:8180` (Firestore) and
`10.0.2.2:9199` (Auth), which reach these host services from an Android emulator.

Sign in with `mai@demo.anilove.test`, `an@demo.anilove.test`, or
`linh@demo.anilove.test`. All three use the **demo-only** password
`AniLoveDemo2026!`. They have verified local email accounts and ordinary
community membership; none receives adoption approval or Admin privileges.

The seed includes 42 historical public post fixtures, three private drafts,
three comments and likes on the first fixture, two public profiles, one
unpublished profile, and a private `received` report for Mai. Public posts are
trusted synthetic fixture setup, not evidence of approved publication or
moderator capacity. `system/communityPolicy.enabled` stays false: F39 still
blocks new public publishing. Reporting has no simulated reviewer or resolution.

The current exported snapshot is `app/build/demo-data` (ignored build output).
After stopping existing emulator services, restore it from the app root:

```powershell
firebase-tests\node_modules\.bin\firebase.cmd emulators:start --config firebase.json --project demo-anilove --only auth,firestore --import app/build/demo-data --export-on-exit app/build/demo-data
```

To seed an already-running local emulator, from `firebase-tests`:

```powershell
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:8180'
$env:FIREBASE_AUTH_EMULATOR_HOST='127.0.0.1:9199'
npm run seed:demo
```

Seeding never clears collections, rewrites existing fixture posts, resets holds,
or enables public rollout. Stable fixture IDs and receipts make retries safe;
unexpected collisions or changed fixture invariants stop verification rather
than repair user data. Existing policies are retained, so disabled report intake
can prevent creating the sample report. Only missing policies are initialized.

The seed verifies actual Auth credentials and Firestore rules: two distinct
20-item feed pages, consistent likes, denied new publication, denied Admin
escalation and denied cross-account report access. Permission-denied log entries
from these negative checks are expected. It does not change UI or rules.

**Do not run the existing rule-test suite against this retained dataset:** its
test setup clears `demo-anilove`. Export first and restore the snapshot afterward.
No Android runtime check is claimed; the workstation has no attached device/AVD.
