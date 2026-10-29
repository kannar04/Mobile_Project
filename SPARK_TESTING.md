# ANILOVE Spark test build

The `spark` variant connects to **aniloveproject** using the ignored
`app/google-services.json`. The supplied `google-services1.json` was renamed.
The default `debug` variant still uses the isolated `demo-anilove` emulators.

Uploads and trusted client commands are disabled. The release variant remains
verification-gated. Firebase billing was checked as disabled on 2026-10-17.

Build and verify:

```powershell
.\gradlew.bat :app:assembleSpark :app:assembleSparkAndroidTest :app:testSparkUnitTest :app:lintSpark -PandroidTestBuildType=spark
```

App: `app/build/outputs/apk/spark/app-spark.apk`.
Tests: `app/build/outputs/apk/androidTest/spark/app-spark-androidTest.apk`.

After connecting an Android target, install both APKs with `adb -s SERIAL install -r`
(add `-t` for the test APK), then run:

```powershell
adb -s SERIAL shell am instrument -w -r -e class com.gmail.manhle240406.project.wiring.SparkWiringNativeTest com.gmail.manhle240406.project.test/androidx.test.runner.AndroidJUnitRunner
```

The five native tests do not create accounts, send recovery email or upload media.
They check the live project binding, paused uploads, unavailable managed routes,
invalid recovery input/recreation and reversible theme preference persistence.
They are compiled but were not run after the target disconnected.

`firebase.spark.json` selects the tested Spark Rules subset and its 14 query
indexes. `scripts/build_spark_rules.py` derives the subset from unchanged approved
canonical rule blocks; excluded feature paths remain denied. Regenerate and run
the isolated emulator tests before future source deployments. Full canonical
future-feature Rules/indexes remain separate from this live test bundle.

The explicit `firebase-tests/live-spark-smoke.mjs --run-synthetic-fixture` test
uses one fresh fixture identity and its minimal shell, checks access denials and
cleans only those records. It requires the deployed-source proof and existing
developer CLI login. It is not a general account-deletion operation.

Current integration evidence, feature matrix and unresolved UI/service/native
gates live in the planning root's
`implementation/reports/SPARK_WIRING.r1.md`. F39 rollout, privileged service
operations, full offline presentation and full bilingual UI are not declared
complete by this build.
