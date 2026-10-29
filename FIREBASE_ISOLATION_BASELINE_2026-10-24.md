# Firebase isolation and read baseline

Date: 2026-10-24 (Asia/Bangkok)

## Isolation boundary

- Project ID: `demo-anilove-isolation`
- Firestore emulator: `127.0.0.1:8186`
- Auth emulator: `127.0.0.1:9196`
- Functions emulator: `127.0.0.1:5006`
- Rules and indexes are loaded from the repository's canonical `firestore.rules` and `firestore.indexes.json`.
- Functions are loaded from the repository's `functions` codebase.
- Test data is synthetic. No production export, account, token, profile, post, image, or secret is copied.
- The measurement result confirms `productionTouched: false`.

Configuration: `firebase.isolation.json`  
Measurement test: `functions/test/read-budget-isolation.test.mjs`  
Machine-readable result: `build/read-budget-isolation.json`

## Measured baseline

The emulator fixture contained seven pending adoption delivery jobs, one 20-item listing page, and 20 distinct public profile documents.

| Path | Observed documents | Estimated read cost |
|---|---:|---:|
| One adoption recovery run | 7 pending jobs | 15 reads |
| Current hourly adoption recovery schedule | 24 runs/day | 360 reads/day |
| Historical one-minute recovery schedule | 1,440 runs/day | 21,600 reads/day |
| Listing page at the current 25-second foreground pulse | 20 listings | 2,880 reads/foreground hour |
| Uncached author resolution at the same pulse | 20 profiles | 2,880 reads/foreground hour |
| Listing plus uncached authors | 40 documents/pulse | 5,760 reads/foreground hour/device |

The adoption estimate includes one policy read, the pending query results, and one missing `pushUserBindings` point read per synthetic job. The listing estimate uses the current `BrowsePulse` interval of 25 seconds.

Firestore Emulator does not expose a billing counter. These estimates use the documents actually returned by the cloned queries and Firestore's document-read charging model. Listener reconnects, Security Rules dependent reads, retry traffic, and Firebase Storage downloads are excluded, so production totals may be higher.

## Verification result

```text
isolated clone measures foreground and recovery read amplification
PASS — 1 test, 0 failures
```

The Debug Android build also compiled successfully after adding configurable emulator host and port fields. Spark and Release continue to use their existing non-emulator configuration because emulator routing is guarded by `BuildConfig.FIREBASE_EMULATORS`.

## Two-device preparation

When both devices are connected by ADB, start the isolated emulators and build Debug for host loopback:

```powershell
cd firebase-tests
.\node_modules\.bin\firebase.cmd emulators:start --config ..\firebase.isolation.json --project demo-anilove-isolation --only auth,firestore,functions

cd ..
.\gradlew.bat :app:assembleDebug `
  -PfirebaseEmulatorHost=127.0.0.1 `
  -PfirebaseFirestoreEmulatorPort=8186 `
  -PfirebaseAuthEmulatorPort=9196 `
  -PfirebaseFunctionsEmulatorPort=5006
```

For each connected device, reverse all three ports before installing the Debug APK:

```powershell
adb -s DEVICE_SERIAL reverse tcp:8186 tcp:8186
adb -s DEVICE_SERIAL reverse tcp:9196 tcp:9196
adb -s DEVICE_SERIAL reverse tcp:5006 tcp:5006
```

This allows both devices to exercise the same isolated Firebase state without reaching the live project. Production rules, indexes, Functions, schedules, and data remain unchanged until a measured fix is reviewed separately.

