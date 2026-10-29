# Schema2 transition and rollback

The approved patch introduces schema2 for new social commands. Schema1 roots,
immutable revisions, operation receipts and any historical thread documents are
preserved. The new UI reads old private stories as legacy private content; it
does not silently publish, convert, copy private information or union old likes.
Unknown schemas and direct downgrade attempts fail closed.

Before any migration, inspect the actual authorized environment. The current
course binding/credentials are unavailable, so **no live inventory or migration
has run**. A clean local fixture inventory cannot establish that cloud data is
absent. `schema-inventory.mjs` is a bounded, read-only demo-emulator tool; it emits
counts/coverage only, no UID/body/phone/report narrative. Run with the demo
emulator host and fixed project, never point this tool at cloud.

For real records, an authorized operator must inventory roots, projections,
revisions, counts and per-version interactions; identify unsupported/held or
previously published records and open report references. Preserve a restorable
snapshot and immutable provenance. If old public/per-version reactions exist,
stop for a reviewed deduplicated transition; do not reinterpret them as one
post-level vote. A scoped migration must preserve author/type/creation/history,
leave old drafts hidden, preserve holds, bind expected versions and produce
auditable source/target records. The patch implements no new operator role or
trusted migration capability. F39 and deployment authorization stay separate.

Existing profile drafts without `profileModeration` cannot self-initialize or
clear a legacy hold during an ordinary update. Publication remains blocked
until authorized inventory/initialization preserves the original decisions.
New own drafts initialize only clear/version1/null defaults atomically.

Rollback is source/environment scoped: keep release and community rollout
disabled, retain the current rules/data snapshot, and revert routing/source as
an isolated reviewed change. Do not deploy historical r1 rules over populated
schema2 records; they do not understand new receipts/interactions. Keep schema2
data read-only until a compatible rollback or forward fix has been reviewed.
Do not delete reports or immutable histories as cleanup.
