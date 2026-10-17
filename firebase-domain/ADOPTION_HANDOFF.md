# Approved r1 feature-code handoff — F11/F08/F10/F12/F13/F14/F15

Scope: domain/data code and tests/audit only, WORKFLOW v2.0. Native Java/XML
preserved. No presentation, Figma, navigation, main dependency wiring, deployment
or privileged account provisioning. Existing F06/F07 writes and protected
adoption/Admin permissions are preserved. New modules remain disconnected from
app screens. Approval sources are exact r1 receipts in the planning root.

| Feature | Callable code / current limits |
|---|---|
| F11 | `CatalogRepository`, `CatalogSelection`; `CatalogService` protected transaction kernel. Read active selectors50, retained exact IDs/revisions; parent compatibility, unknown/mixed, immutable revisions, labels/variant uniqueness fences and retirement/new-ID replacement. No mobile Admin capability or real operator authorizer is provisioned. |
| F08 | `PetCareContract`, `PetCareRepository`; private four-record revision/receipt protocol, source/identity/custody/session checks, unknown-aware dates/fields and separate private medication. Small commands pass; legitimate20 exact-date entries still exceed rule evaluation budget. This acceptance failure is deliberately retained, not converted to a passing denial test. |
| F10 | `ListingReviewRepository`; `ReviewService` protected submit/cancel/assign/decide/revoke kernel. Exact candidate fingerprint/version, independent reviewer, receipts, owner reason/history, hidden approval and atomic bridge revocation. Availability is read-derived; no reserve/place/custody setter. Default trusted authorizer/command port rejects. |
| F12 | `ListingDiscoveryRepository`, `DiscoveryProjection`, `DiscoverySession`: sanitized24-key DTO, signed-in bounded reader, fresh exact gate checks, single head listener, window100/anchor/generation, bounded live Host lookup. Production publication/read policy is absent/disabled; no source-writer integration performed. |
| F13 | `DiscoveryQuery`, deterministic `discovery-indexes.mjs`: ASCII accent-folded name prefix, two order modes, six finite optional filters,48 valid subsets/96 index definitions, compatible species/breed preflight, cursor/generation safety. No full-text/private scan/paid service. Deployed quota/index/source backfill verification remains pending. |
| F14 | `FavoritesRepository`: UID-private ID-only records, desired state, frozen base/digest/receipt, atomic count≤200, version conflict/reconciliation, safe restricted/unverified Remove, page20/head listener, five concurrent current detail resolutions. No title/photo/contact snapshots, reservation or Host alert. |
| F15 | `ShareLinks`, `ShareCoordinator`: strict HTTPS origin/UUID parser/formatter, generic text after fresh eligible detail, one safe sign-in destination, no URI actions/query/fragment/credentials. Origin defaults disabled; no actual chooser/clipboard/manifest/App Links/Hosting mutation. |

## Publication and source boundaries

`listingDiscoveryPolicy/current` is trusted-only/default-denied. Shared reads
require enabled + writerSetVersion1, which tests seed only in the isolated
fixture project. **Do not activate this flag** until every compatible F06/F07
hide/edit/identity/custody/hold/source writer is reviewed and atomically maintains
the sanitized projections. Real F39 reviewer capacity remains separately gated.
The synthetic open/shown source records used for reader tests are future adapter
fixtures, not an amendment to existing private F07 schema1 writers.

Firestore list rules must use queryable audience/state/availability predicates;
they cannot prove a full arbitrary field whitelist. Trusted writers must enforce
the exact schema before exposing any dataset. Exact reads recheck whitelist and
current canonical eligibility; the native repository rejects unsupported query
rows before handing them to consumers. That parser alone cannot prevent a leak
from an incorrectly privileged/imported public dataset. Inventory/sanitation and
every writer's privacy proof are prerequisites for policy activation, not a
claim that client filtering secures a public collection. No source writer or
activation command was added here.

Media descriptors remain empty and only bundled keys are supported. F09 token/
service/consumer gates, F29 listing-report adapter, public care/participant
consent and F18/F21 selection/transfer remain outside this delivery. Private
care cannot change a currently discovered bound listing or active obligation.

## F08 execution review required

Exact accepted analysis is F08 r1. Its20-entry client commit remains an open
acceptance failure (1000-expression evaluation budget); small private commands
and isolated date/byte predicates passed. The approved cap was not reduced and
authorization was not relaxed to hide the failure. Full delivery is **blocked**.

Review target: planning `analysted/F08-health-vaccination-sterilization-color-behavior-and-care-needs.r3.md`,
request r2. It proposes only managed authenticated SaveCare execution with the
same four-record schemas/20-entry/8KiB constraints and independently enforced
server UID/editor/lineage/date checks; direct client writes would be denied.
It passed fresh prepare/check eligibility but is **not approved or implemented**.
Earlier r1 approval cannot approve that gateway. Root accepted context/r1
receipt remains unchanged. Functions/service/billing/IAM/deployment still need
their own explicit authorization even after analysis approval.

## State/recovery and security

Use captured `SessionEpoch.Ticket`; repositories guard entry, transaction and
callback result. Close listeners/repositories and session-aware state on exit.
`DiscoverySession` offers a gateway-bound constructor that clears on invalidation;
the pure constructor is fixture/caller-lifecycle driven. Pending link destinations
must be cleared on logout/cancel; explicit bind-after-sign-in restores only the
identifier, never a pending action. No new durable SQLite/outbox/cache.

Favorites transactions never change savedAt/count for a no-op. Concurrent stale
commands may fail version/permission checks; reconcile current row/receipt before
explicit new intent. Retry an uncertain result with the original operation/base,
not an invented new success. Missing meta plus any existing namespace row triggers
the native bounded inventory/repair gate; unknown privileged imports need
operator inventory before Save activation. Rules prevent client meta deletion
and paired-count/multi-target bypass for namespaces established by this protocol;
they do not magically repair privileged corruption.

Trusted kernels are deployment-independent and require a real server-owned
authorizer/store transaction port. Default authorizers reject. Synthetic test
callbacks are not real F39 roles. No API/service credentials or private content
are printed; no external backend, live notifications or billing activation.

## Reproduce checks

From `firebase-tests` run `npm run test:adoption-domain` and
`npm run test:adoption-rules`. The latter currently returns failure for the known
F08 max-size acceptance scenario. The other16 scenarios are evaluated
against the actual source rules; no temporary predicate probe is injected in
the final suite. It uses isolated demo-anilove-feature-tests8280/9299 and clears
that fixture project only. No valid-target main-app demo data is seeded.

Do not run emulator suites concurrently: their discovery/websocket lifecycle
can conflict. Existing `npm test` clears demo-anilove8180/9199; export any retained
running demo dataset before using it. Builds/JVM/lint and earlier feature
regressions are recorded separately in planning implementation/evidence.

UI design/code/wiring and native runtime remain separately requested/unverified.
No Impeccable surface audit applies because this delivery changes no presentation.
Read the scoped implementation report for exact pass/failure counts and evidence.
