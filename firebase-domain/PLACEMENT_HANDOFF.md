# F16?F22 r1 feature-code handoff

Scope: domain/data/rules/indexes, tests and code audit under WORKFLOW v2.0.
Native Java/XML retained. Exact seven approved r1 artifacts/receipts verified.
No UI/Figma/layout/navigation/main dependency wiring, service activation,
deployment, real adopter/operator provisioning or external contact launch.

| Feature | Callable interfaces |
|---|---|
| F16 | `QuestionnaireContract.answers`, `validateTemplate/validateAnswers`; protected exact templates/bindings; immutable submitted/amended answer revisions and own questionnaire receipts. Max8 questions/6 required/400 characters each/4KiB total, uncertainty and first-time experience allowed. No score/invasive custom fields. |
| F17 | `PlacementCommandRepository` submit/amend/withdraw/request_cancel/read/own page20, `PlacementService`. Actual pet/UID slot; cap5 active,10 accepted grants/rolling24h,30s spacing; frozen operation digest/receipt; exact sanitized immutable offer/care/template snapshots; selected cancellation is an intent. |
| F18 | Managed select/reject/preparation acknowledgment/extension/two-party cancel commands. One accepted F06 gate; seven elapsed days, peer-acknowledged deadline version/horizon30days; expiry protective review, no auto-release/next winner. Own declarations, reported possession, holds/confirmation govern cancellation. |
| F19 | `ParticipantCommunicationRepository`: preview/version-bound own contact consent/revoke, own block, bounded text send/read/head/inbox/read marker. Pair derives from reservation; fresh phone/source/no block/current grant/hold/expiry guards; linked message/counter/rate/receipt/two private indexes;2000 chars,1000 messages,2s spacing. No attachments/deletion/moderator membership/typing/read-pressure or external launch. |
| F20 | `PreparationContract`, managed meeting/terms/own checklist/ack/accept/revoke. UTC+IANA display timezone/private location/horizon within reservation; fixed fictional terms, follow-up none_demo, no payment/retrieval clauses. Independent current hashes/source versions; no editable ready flag or peer consent. |
| F21 | Managed own confirmation/correction and fixed atomic finalizer kernel. Independent given/received reports/current readiness; deterministic placement/enrollment/parent outcome; custody/editor increment preserves registrar; blocks prior authority; closes listing/grants/projections. `ApplicationClosureWorker` explicit bounded20 checkpoint repair; no scheduled worker. Post-completion own discrepancy is a private intent, not a provisioned case or rollback. |
| F22 | `MemberHistoryRepository`, `HistoryMaterializer`: owner-only role/type/outcome single-equality/date queries/page20, private source/timeline/snapshot; deterministic monotonic source-derived views. No public reputation, location/phone/allegations/current caregiver notes, exchange/return/fostering adapters or new owner inheritance. |

`PlacementSession` supplies UID/query generations, one fetch/cursor, memory
window100, stable reading anchor, explicit new-head refresh, chat bottom
behavior and16KiB maximum memory draft. Gateway-bound lifecycle clears on
logout; pure constructor is for fixtures/caller lifecycle. Caller must bind
composer drafts to exact source/application intent and clear pending state on
exit. No SQLite/private outbox, automatic reconnect/reboot submit or analytics.

## Ports and activation boundary

`PlacementCommandRepository.Commands.UNAVAILABLE` rejects by default; no real
callable transport/auth handler/Functions deployment or client Admin is added.
`PlacementService` requires an injected **trusted server-owned** transaction
port and clock. Authentication actor must be derived from verified callable
context, never submitted request JSON. It independently reads accounts/current
protected source and owns version/hash/party checks. Trusted worker/materializer
authorizers default-deny and require explicit true. Unit callbacks are synthetic
fixtures, not actual F39 permissions.

`placementWorkflowPolicy/current` is absent/default-denied. Enabling it requires
reviewed compatible F06/F07/F08/F10 publication, identity/care/account/hold,
reservation and transfer writer adapters, source inventory/migrations, permitted
managed-service/IAM/billing scope and independent operator/runtime proof. This
code tests future open/visible source fixtures; existing F07 native writers
remain private and are not changed. No second gate or protected client write
permission is introduced. Core finalizer retains F07's no_longer_offered closure
reason; authoritative placement event, never listing closure alone, determines
completed history. Gates/registrar/claim/history/holds are not cleared as a
shortcut. Any recipient protected-domain readiness is independently checked;
no handover grants member/Admin/reviewer roles.

Communication capability flags are server-owned grant fields and default
**false** for both contact and text when selecting. Contact consent remains
separately off until explicit exact preview/version sharing. Synthetic emulator
grants set flags true only in isolated fixtures. Real text rollout needs the
accepted F29 private-message evidence/report adapter and F39 response capacity;
message withdrawal is denied until retention/evidence policy exists. External
contact can be separately activated after its compatible source/consent adapter
without activating chat. Block/help history is independent of cancellation.

Handover's own confirmation implementation currently has a managed command port
and trusted kernel; direct mobile confirmation/control writes remain denied.
A native direct-confirmation writer with proven linked-rule budgets is an
unimplemented execution adapter. No architecture permission is inferred from
the port, and no deployed endpoint is claimed. Managed intake/reservation/
preparation/finalization transports, auth/App Check/rate monitoring, production
IAM/indexes and actual operator/course/device tests remain unavailable.

`ApplicationClosureWorker` requires `store.pageApplications` to implement
listing equality, document-ID ascending cursor and limit20. Each child checks
parent outcome/version and matching own slot before release; a newer slot is
never cleared, and already withdrawn/rejected/completed records are preserved.
Explicit course worker checkpoints may lag; parent closes competing permission
immediately. At most5 active quota entries are reconciled on intake. No scan,
guessed counter reset or unbounded transaction. Real scheduled/automatic repair
is not activated; missing query adapter returns an explicit unavailable error.

Sanitized source snapshots copy only exact supported offer/identity/care fields;
identity observation time becomes an explicit millisecond value. No raw private
medication, chip, phone, exact meeting place or vet evidence is copied. Typed
unknowns/precision/self-report source persist. Materialized history keys stay
stable, recorded time does not jump, unsupported return/exchange/case sources
reject. Trusted imports still require schema/lineage/privacy inventory; client
parsing or an emulator fixture does not secure an incorrectly privileged dataset.

## Verification and known blockers

Run `npm run test:placement-domain` and `npm run test:placement-rules` from
firebase-tests. The latter starts only isolated Auth/Firestore demo project
8280/9299 and clears its fixtures; never point these tests at live data. Run
emulator suites sequentially to avoid discovery/websocket conflicts.

Android debug/JVM/lint, changed-behavior Node tests, real Firestore client
communication/permission cases and a trusted-port core transaction are locally
verified. Evidence/metrics/code audit are in planning implementation reports.
Native SDK execution/device/rotation/IME/a11y, deployed services and indexes,
production source writers and real human operations are not verified. No
Impeccable surface audit applies because no presentation is modified.

Prior F08 r1 maximum20 exact-date client SaveCare still exceeds Firestore's
rule budget; its existing acceptance test remains failing. That problem and
its pending execution analysis are preserved, not silently fixed/approved by
this batch. Source care consumers reject missing/unsupported snapshots.
F09 media/privacy and F39 rollout gates stay closed. F23/F24 obligations remain
unapproved/inactive: exactly one awaiting_policy enrollment intent, no90day
schedule, tasks, surveillance or future contact grants. Case/appeal/return,
retention and durable private cache remain separately scoped. Status is a
locally delivered feature-code slice with operational/adapter gaps, never an
integrated or real adoption service.
