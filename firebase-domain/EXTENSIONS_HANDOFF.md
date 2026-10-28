# F32-F41 extension batch - scoped feature implementation r1

Date:2026-10-15. Requested F32/F33/F34/F35/F36/F37/F40/F41. All eight exact accepted r1 analysis hashes match their approval receipts and parent approval context21. Source request revisions differ (F34/F41 request r2); accepted analysis is r1. No approval record or parent approved context/index was changed.

Scope: non-UI Java/domain/data/rules/indexes/tests/code audit only, per implementation/WORKFLOW.md v2.0. UI/Figma/layouts/navigation/main app wiring, protected role grants, live data changes, service/billing activation, provider operations and deployment are not performed. No separate external backend or new dependency.

## Delivered and gated work

### F32 r1

Text-only public/private notice separation;30day expiry,5 open notice/200 claim/20daily claim bounds; private immutable answers/clarification, unique claimant link, selection/dispute/withdrawal, two own same-revision reunion reports and no custody award. Managed chronological page reader re-authorizes each source; raw SDK feed list is closed to avoid cross-parent rule-budget shortcuts. Assigned evidence mediation logs actual reads.

Remaining: Managed Auth/callable transport and query adapter deployment/IAM, compatible F29/F39 notice target/hold/independent dispute-resolution adapters and actual capacity; F09 private/public image binding/retention and selected-claim contact remain unavailable. No live lost-animal service or legal ownership proof.

### F33 r1

Request/offer ad contracts and date span validation; matched private terms/source hashes, exclusive booking/capacity kernel, own terms/revocation/no-intake/intake/return reports, paired temporary-care events, extension acknowledgement and nonpunitive due/review flag. Shared-source port is closed by default; test-only source collections demonstrate transaction atomicity without changing F06 custody/editor semantics.

Remaining: Reviewed F06/F07/F08 temporary-purpose/current physical caregiver/editor/custody amendments and real shared gate/source writer inventory are absent. Default source port refuses physical work. Current care after intake/return must be implemented by that compatible adapter. Independent support/safe receiver/contact/media/history/reminder adapters remain gated.

### F34 r1

Two distinct purpose-source proposal snapshot/link/quota, two own reservation consents, atomic two-source reservation interface, two role readiness/terms consents, four own directional physical reports, safe no-transfer cancellation, all-or-nothing two-placement interface and independently assigned partial-resolution guard. One-sided/disputed reports cannot create normal completion or release locks.

Remaining: Purpose-scoped listing/review/shared activity/custody/placement/enrollment/outcome amendments and actual independent partial-transfer/safe receiver capacity are absent. No real source finalizer is supplied. Full maximum real-core transaction footprint must be counted after source contracts exist; isolated test-port footprint is not that proof. No adoption-only listing reinterpretation or implicit role grant.

### F35 r1

Manual coarse region preference, existing authorized listing query reuse for bounded3-region expansion, explicit generation cancellation and default-unavailable Places provider. Pure approximate straight-line distance bands; transient origin expires after5minutes and has no durable coordinate path. No location/provider dependency, permission prompt or billing activated.

Remaining: Real foreground approximate-location permission/lifecycle adapter, region centroid source verification, and separately authorized Maps/Places keys/licensing/attribution/billing remain unavailable. Regional source policy/index deployment and native map/provider behavior need integration; no provider content cached.

### F36 r1

Own online reminder create/edit/snooze/done/undo/cancel/retire with explicit user attribution, UTC derivation/DST gap and ambiguity checks, current caregiver/custody witness,20/pet100/account caps, immutable revisions and versioned disabled scheduling intents. F08 is never updated. Per-pet current-source SDK page<=50 with owner/custody constraints.

Remaining: Current caregiverEntitlements source producers and invalidation hooks for every enabled custody path, managed transport and exact F36/F41 private scheduler persistence exception are absent. No WorkManager/AlarmManager work, notification permission/channel/push or external clinical schedule is enabled.

### F37 r1

Own optional explicit preferences/reset, current taxonomy validation, deterministic4/2/1 scorer with at most2 factual reasons, max60 newest candidates/20-page retrieval and opt-out/no behavior traces. Java adapter reuses current F12 projection; absent approved public care-interest tag adapter contributes zero rather than private inference.

Remaining: Actual consumer source/index/runtime verification and reviewed public care tag producer; transport/own preference wiring and per-consumer F41 persistence adapter remain gated. Scorer ranks the bounded snapshot only, never certifies suitability or global best match.

### F40 r1

Capability-scoped UTC<=90day managed reads, versioned definitions/source-type allowlists, coverage unavailable/partial semantics, current-authoritative source consumer, monotonic contribution/delta ledger, duplicate event dedup within/across batches and immutable event times. Negative invariants quarantine affected metric instead of clamping. No raw aggregate/contribution SDK access or PII response.

Remaining: Actual source outbox hooks/inventory, metric definitions/coverage/backfill, scoped operator provisioning/IAM/service binding and production counter contention budgets remain unavailable. ReconcilePartition deliberately refuses without source-inventory reconciliation adapter. Sensitive groups require separate capabilities; missing coverage never becomes zero.

### F41 r1

Java SQLiteOpenHelper core in noBackupFilesDir, Android Keystore namespace AES-GCM with AAD, same-boot elapsed<=24h leases, strict consumer interface/default exclusions,500records10MiB/20drafts64KiB/200thumbnail50MiB bounds, encrypted variant files, query membership/anchors/tombstones, compare-and-set drafts, minimal unknown-outcome metadata and generation-safe receipt acknowledgement. No automatic mutation dispatch; F07 store unchanged.

Remaining: Reviewed F12/F13/F31/F09 durable source/thumbnail/composer adapters are still absent; NO_CONSUMERS enables nothing. F07 lossless migration/ownership handoff, app-level namespace lifecycle registry and disposal of all closed namespaces on logout, observers/manual send/receipt transport, actual source invalidation hooks and background retention/cleanup need separate integration. Store-handle close is not account erasure; future owner must retain namespace and call closeNamespace before releasing it. No actual course reader offline UI, system-airplane-mode or provider-media proof; private medical/case/terms/chat/contact/Admin/reminder schedules excluded.

## Executed evidence

- Debug app/test APK build and lint pass.103 JVM tests pass; lint0errors/223warnings across the existing workspace.
-116 Node domain tests pass:33 extension tests and83 existing domain regressions.
-12 new isolated Firestore rule scenarios pass, including current-source/foreign/role spoofing/direct write denials.39 existing adoption/placement/safety emulator regressions pass. A combined50-case run and final affected12-case run are separate evidence, not51 unique tests executed in one invocation. Storage15/baseline134 are not rerun for this batch; earlier evidence is historical.
-13 native encrypted-store tests pass on connected Android API30, including actual SQLite/Keystore, nested allowlists, ciphertext at rest and tamper failure, leases/boot/rollback, tombstones, draft CAS, stale receipt generation, account namespace key disposal, unknown outcome metadata and encrypted fixture variants. Fixture media bytes are not an implemented F09 image consumer.
-Two-phase probe through approved F01 registration passes: write -> confirmed force-stop/process absent -> read in a new process with Firestore networking disabled. Cached fictional page readable/unseen page unavailable. Uses actual elapsed/boot marker; not system airplane mode or a real F12/F31 consumer.

Initial failures were fixed, not converted into blanket denials: query constraint proof for own reminders, self-only subject helper misuse for another notice author, ordinary-community vs protected-role separation, event dedup/immutable identity/quarantine. The initial restart probe used bare SDK anonymous sign-in; F01 correctly ended its visitor session on restart. The corrected probe establishes an app F01 account; no F01 safeguard was weakened.

The isolated emulator config uses8280/9299 and nonconflicting hub/logging/websocket ports; it does not stop the existing app emulators. Only demo namespaces/test-only source ports were used. No protected role/capacity is provisioned. Main F06/F07/F21 source writers, F09 Storage rules and Firestore SDK memory-only persistence remain unchanged. Root account shells gain no new fields.

## Status and audit

All eight feature_code statuses remain needs_input for the exact gates above. These are implemented local kernels/interfaces with tested scope, not eight integrated user features or a complete F41 course-offline milestone. F39 rollout remains closed. No UI was modified, so Impeccable UI audit is not applicable. See EXTENSIONS_CODE_AUDIT.r1.md, per-feature reports and app firebase-domain/EXTENSIONS_HANDOFF.md.

Evidence directory implementation/evidence/EXTENSIONS_BATCH.r1 contains exact approval/source hashes, logs, JUnit/lint XML and metrics.json. The final source hash manifest is the reviewable snapshot; the pre-existing dirty workspace was preserved and no commit made.

The extension-router authenticate default throws. Supply approved managed Firebase execution/Auth and typed transaction/query ports in separately authorized integration; no server/endpoint is registered. CLOSED_TRANSFER_SOURCE is not a permission service. testExtensionSources/testPetLocks/testCustody collections exist only in tests. EncryptedOfflineStore.NO_CONSUMERS must remain empty until exact consumer amendments; do not register excluded kinds or replace F07 writer without lossless handoff.
