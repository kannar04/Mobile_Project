# Managed media operations

Approved inputs: F01 r4, F03 r4, F09 r3, F31 r6, F42 r4, planning context27. Implementation/evidence live in the planning root, `implementation/reports/MEDIA_BLAZE.r1.md`.

The installed `spark` build now uses the real Blaze project `aniloveproject`. The variant name is historical. Debug/emulator and release builds keep their existing media gates. Native Java/XML is retained.

## Payload allowance

The dedicated bucket `aniloveproject.firebasestorage.app` has a conservative application media allowance of **1,000,000,000 bytes**. This is not a provider-native bucket quota, a whole-Firebase cap or a spending limit. Firestore, requests, egress, Functions, logs and build artifacts are outside this allowance. Privileged out-of-band bucket writes bypass the application guard and must not be used for app media.

Each occupied asset reserves 8,388,608 bytes transactionally before any object write. At most119 reservations fit. Candidate/display/thumbnail have fixed create-only paths and individual bounds; no versions, copies or soft-deleted payloads. Acknowledged completion plus generation-checked deletion and confirmed absence are required before capacity is returned. An uncertain writer retains its reservation even after expiry; never manually free it based only on elapsed time.

## Runtime and access

Firebase Functions v2 `mediaApi`, asia-east2, Node22, scoped runtime identity, min0/max2 instances and concurrency1. Routes: reserve, receive, read, post, avatar, google, remove, saveProfile, publishProfile. Direct client Storage access is denied. The function validates Firebase ID tokens, account/membership/restrictions and current source/audience on each read; no download-token URLs or public objects are produced. Photo posts require verification; own private avatar selection does not. Public profile publication retains explicit consent and its approved verification requirement.

Posts allow up to10 ordered images and show at most4 tiles, with the fourth displaying +N for the remaining images. Tap opens the full manifest. Native Photo Picker avoids broad media permissions. Media uploads are foreground actions; they do not enter the durable text outbox. Google photo import uses the authenticated provider record, controlled HTTPS hosts, bounded decoding and a private default only for a newly created account. Returning choices are preserved; explicit replacement is available.

## Maintenance

Run host scripts from `firebase-tests` with Node22.12+ and an already authorized Firebase CLI account; credentials remain in memory. This session used an ignored local Node22.22 installation because the host's22.11 cannot load current Admin SDK dependencies.

`node maintain-media.cjs` is a bounded dry run. `node maintain-media.cjs --apply` performs approved orphan cleanup, at most20 candidates per invocation. There is no Scheduler. Unknown writers and retention holds remain occupied. Do not reset quota/receipt documents or release reservations manually to bypass capacity.

To pause new uploads, set `mediaPolicy/current.enabled=false` with an authorized owner transaction. This does not revoke existing reads; source moderation/hide/archive does that. Re-enable only after deployment and invariant checks. Preserve direct Storage deny rules and the fixed runtime identity. Do not redeploy all project rules/services from an unrelated config.

Other listing, adoption, evidence, chat attachment, retention and durable-image-cache consumers remain separately gated. This implementation grants no Admin/adoption permissions.
