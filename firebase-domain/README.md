# F09 r2 / F30 r1 domain handoff

Requested scope is **feature code**, under WORKFLOW v2.0. These modules are
deployment-independent trusted logic for future managed Firebase adapters.
They do not initialize services, register triggers, activate producers, create
an external server, or change F31/F07 source schemas. No credentials are needed
for local tests. Native repositories are callable data interfaces, unbound to UI.

## Current callable interfaces

- `media/MediaContract`, `CandidatePreparer`, `MediaRepository`: worker-only,
  app-private/no-backup JPEG candidates; pixel/encoded/selection caps;
  order/move validation; own paged metadata; stable ticket/removal intents.
  The preparer is bound to one session, ends on invalidation, owns its input
  stream and deletes candidates on release/close. No restart/outbox upload.
- `notifications/InboxRepository`, `InboxWindow`, `InboxContract`: page20,
  at most three empty candidate pages per automatic load, raw cursor, window100,
  one detachable head listener, explicit refresh, account isolation, pending
  memory read intents, up-to20 transactional read marks, monotonic dismiss,
  versioned default-off preferences, unread dot, fresh target authorization.
  Route availability performs no navigation or domain mutation. Future wiring
  must treat network failure as unknown/unavailable, never restore stale access.
- `PushBindingRepository`: typed register/confirm/renew/revoke callable port;
  no token acquisition, OS service, permission declaration or notifications.
  A4096-character token input cap is this prototype's defensive protocol bound,
  **not a guaranteed Firebase token-size specification**; real SDK/device
  compatibility remains unverified.

Java command ports have explicit failing `UNAVAILABLE` implementations. Future
wiring must supply only separately authorized authenticated managed transports;
it must not replace those errors with fixture success or use Admin keys.
Repositories guard session tickets on entry, transaction and result callbacks.
No live storage/download/callable/FCM SDK adapter is installed or activated.

## Trusted kernels / adapter contract

`MediaService` implements transaction ticket/receipt/quota reservation, bounded
outstanding ledger, finalize generation/lease/CAS, readiness/restriction recheck,
idempotent quarantine deletion, versioned owner removal and generation-checked
unattached cleanup. `ImageSanitizer` injects sharp and an object port; checks
encoded bytes/hash/magic/MIME, single image/pixels, bounded decode timeout,
orientation, fresh JPEG metadata stripping and immutable display/thumbnail
outputs. Tests use real sharp0.35.5. Do not expose raw arbitrary object paths.

`NotificationService` reads actual committed F31 comment revision/receipt and
current parent, derives recipient, excludes self, validates source watermark/
seven-day replay, deduplicates contributions, groups ten-minute activity with
immutable chronology/read state, summarizes overflow after50 new rows/day,
and queues optional generic jobs. Own F29 intake derives only real `received`
status; no reviewer event exists. Sender leases, current policy/preferences/
source/binding checks,20-attempt/day cap, five-attempt/24-hour TTL, bounded
backoff and current-epoch token invalidity handling are independent of domain
commit. Provider acceptance is not delivery/read. Crash-after-send can duplicate.

`BindingService` sends a nonce ONLY through the claimed token; register response
contains challenge ID/expiry. Auth UID+secret+nonce confirmation transaction
owns the token uniquely, caps active installations at3, requires replacement
confirmation, revokes old bindings and guards versions. Hashes/nonces/tokens
are never in inbox or logs. Identical registration returns its existing pending
challenge; a crash before challenge send may require an explicit new intent
after reconciliation/expiry, not a silently activated binding.

Store port is `transaction(async tx => ...)`, with async `tx.get(path)` returning
document data/undefined and `tx.set(path,data)` buffering writes. Dates must be
stored as Firestore timestamps. All reads precede writes; production adapter
must use a real Firestore transaction, server-derived UID/claims and trusted
server time. Local actor objects are test authorization fixtures, not tokens.

Object port must generation-bound `readGeneration`, immutable/reconciled
`createImmutable`, and conditional `deleteGeneration`; it must record unfinished
output generations for bounded orphan repair, enforce byte limits before
buffering, and write **no download tokens/public ACLs**. Retention/consumer
writers must synchronize against cleanup leases and deny new references/holds
after an irrevocable deletion claim; that production protocol is not activated
or certified here. No shared attach/manifest/access adapter or sweeper exists.
Dispatcher orchestration, per-installation job fanout, cloud-safe challenge
delivery, cleanup/checkpoints and runtime IAM are pending managed-service work.
Do not call these modules an operational background service.

## Security blocker: Firebase upload bearer tokens

The installed Storage emulator reproduces Firebase API token creation: a
synthetic admin upload gets a bearer URL that reads bytes anonymously even
while staging read rules deny SDK access. A strict read rule cannot fix a minted
token. Consequently `storage.rules` keeps **all client staging creates closed**,
including apparently valid tickets. Draft two-document predicate and explicit
overwrite guard are retained for review, not activated. Authenticated private
final generation checks are tested with synthetic objects, but no live no-token
write/read transport or IAM guarantee is claimed. A reviewed/verified no-token
transport solution is required before uploads can be enabled; merely changing
the gate is unsafe. No new feature-policy amendment was assumed.

## Local checks and remaining scopes

From `firebase-tests`, run `npm run test:feature-domain` and
`npm run test:feature-rules`. Rules suite uses **separate** project
`demo-anilove-feature-tests` on8280/9299/9298 and clears only those fixtures.
Storage emulation is local, not billing activation. Existing `npm test` uses
demo-anilove8180/9199 and clears that dataset; preserve/export retained data
before using it. Indexes are committed configuration; emulator acceptance does
not certify deployed indexes/IAM.

UI design/code/main wiring remain not_started. Device image preparation,
stream revocation/interruption, actual FCM/challenge delivery, future managed
transports/services/billing, consumer media amendments, F39 reviewer/public
rollout and production project checks remain gated/unverified. No push payload
contains source text/media/contact data, and no adoption/Admin privilege changed.
