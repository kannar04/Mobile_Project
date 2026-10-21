# ANILOVE Codex handoff — 2026-10-21

## Purpose

Continue the existing ANILOVE Android/Firebase implementation without losing the current uncommitted work, approval history, deployment state, or focused-verification boundary.

## Workspace and mandatory instructions

- App repository: `D:\dan\Mobile_Project`
- Planning and approval records: `C:\Users\Minht\Desktop\New folder`
- Read before making feature changes:
  - `D:\dan\Mobile_Project\AGENTS.md`
  - `C:\Users\Minht\Desktop\New folder\AGENTS.md`
  - `C:\Users\Minht\Desktop\New folder\implementation\AGENTS.md`
  - `C:\Users\Minht\Desktop\New folder\implementation\WORKFLOW.md`
  - `C:\Users\Minht\Desktop\New folder\implementation\VERIFICATION.md`
- Project-local skills:
  - `C:\Users\Minht\Desktop\New folder\.agents\skills\anilove-ui\SKILL.md`
  - `C:\Users\Minht\Desktop\New folder\.agents\skills\anilove-firebase-code\SKILL.md`
- Native Android UI changes require the Impeccable native audit described by the workflow.
- Do not treat roadmap exports or this handoff as approval evidence. Exact analyses and receipts in the planning root are authoritative.
- Do not print secrets from `local.properties`, Firebase configuration, credentials, or environment variables.

## Repository snapshot

- Branch: `new_build2`
- HEAD at handoff: `f7b879f` — `fix after feedback 7 still need permission testing`
- Android stack: Java, XML layouts, Material Components, Gradle Kotlin DSL.
- Firebase stack: Authentication, Firestore, Functions, Messaging, and Storage.
- Connected build variant: `spark`; build with `\.\gradlew.bat :app:assembleSpark`.
- Firebase project expected by the Spark variant: `aniloveproject`.
- Two devices were visible over wireless ADB at handoff:
  - `2606FRN72Y`
  - `M2010J19SG`
- Do not assume they will remain connected or unlocked in a later session.

## Critical working-tree warning

The working tree is intentionally dirty. Do not reset, clean, checkout over, reformat wholesale, or discard it. At handoff there were 53 tracked modified files plus these untracked source/test resources:

- `app/src/androidTest/java/com/gmail/manhle240406/project/community/OwnPostsFooterDeviceTest.java`
- `app/src/main/res/values/adoption_unified.xml`
- `app/src/main/res/values/ids.xml`
- `app/src/main/res/values/ocj8_errors.xml`
- `app/src/test/java/com/gmail/manhle240406/project/adoption/LifecycleErrorCopyTest.java`

`firebase-tests/__pycache__/` is generated noise and should not be committed. Re-run `git status --short` before editing. Inspect and preserve every existing diff. The current diff spans adoption, community, notification, pet/profile navigation, feature-grid wiring, many XML layouts, welfare reporting, and `functions/adoption-flow.mjs`.

## Latest exact approved analyses

The current coordinated adoption revisions are recorded as approved in `C:\Users\Minht\Desktop\New folder\approval_review`:

- F03 r8 — profiles/contact/privacy
- F10 r8 — listing approval, availability, and unified adoption access
- F20 r4 — agreement/meeting and unified exchange stage
- F23 r4 — post-adoption follow-up
- F28 r2 — participant reviews

Their analyses are in `analysted/`, and their approval receipts are:

- `approval_review/F03.r8.approved.json`
- `approval_review/F10.r8.approved.json`
- `approval_review/F20.r4.approved.json`
- `approval_review/F23.r4.approved.json`
- `approval_review/F28.r2.approved.json`

The receipts say the user approved these five exact coordinated revisions after presentation on 2026-10-21. Do not run `approval_gate.py check` on already promoted revisions and interpret rejection as revoked approval: the command is a pre-approval check and rejects analyses no longer in `awaiting_approval`. Validate receipt hashes and analysis contents directly when continuing approved work. Any materially new scope or revision still requires the workflow's fresh prepare/presentation/approval process.

## Latest implementation state

Primary evidence: `C:\Users\Minht\Desktop\New folder\implementation\reports\UNIFIED_ADOPTION.r1.md`.

Implemented in the current partial slice:

- Trusted adoption lifecycle maintains `adoptionReceivingCapacity/<uid>` and permits at most two active receiving requests.
- Rejection, withdrawal, pre-handover cancellation, completed return, and finalized 90-day monitoring release capacity.
- Offering pets remains unlimited.
- Native adoption summary uses three stages: request submitted, personal-information review, and exchange/handover.
- My-pets surface includes `Thú cưng của tôi` and `Đang nhận nuôi`, using the existing adoption index.
- Active adoption entries open the unified lifecycle.
- Dog/cat account-avatar presets were removed; Google, uploaded, and default account avatars remain.
- Lost/found and Maps remain hidden.
- The static fake F28 review shortcut is hidden.
- Firebase OTP remains the authoritative phone-verification mechanism.
- `adoptionOperations` was deployed to `aniloveproject` in `asia-east2`.

Earlier relevant completed work and reports:

- Dedicated Firebase phone verification and Vietnamese `0...`/`+84...` normalization: `implementation/reports/PHONE_VERIFICATION.r1.md`.
- Ordinary adoption access no longer depends on a manually provisioned protected-member role: `implementation/reports/PERMISSIONS_ACCESS_FIX.r1.md`.
- F39 course-test rollout enabled implemented lifecycle features while retaining ownership/capability controls: `implementation/reports/F39_TEST_ROLLOUT.r1.md`.
- Dark-mode sub-feature text fixes: `implementation/reports/DARK_MODE_TEXT.r1.md`.
- Dedicated care/support icon family: `implementation/reports/CARE_ICONS.r1.md`.

The prior F39 report says live course participation, adoption lifecycle commands/jobs/push, source-3 adoption publication, applications, and notifications were enabled. Treat external real-world services as unavailable unless independently configured and verified.

## Explicit done / not-done matrix

This table is the clearest status snapshot. "Implemented" means code exists in the current repository or deployed Functions according to the cited report. It does not automatically mean every path was tested on both devices.

| Area | Status | Current evidence or remaining work |
|---|---|---|
| Spark Firebase build configuration | Implemented | `spark` targets `aniloveproject`; historical Spark compile and assemble passed. |
| Google/Firebase authentication | Implemented | Existing app flow; preserve current Firebase sessions during device testing. |
| Vietnamese phone entry and normalization | Implemented | Accepts local `0...` and `+84...`; see `PHONE_VERIFICATION.r1.md`. |
| Real phone verification | Implemented, runtime coverage incomplete | Uses Firebase Phone Auth and writes the private contact only after Firebase confirms the credential. Real SMS delivery and edge cases remain device/provider dependent. |
| Profile phone UX | Implemented | Locked display row opens the dedicated verification screen; it is not a normal editable profile field. |
| Ordinary-member adoption permission | Implemented | Legacy protected-member role is no longer required for ordinary adoption access. Server ownership/restriction checks remain. |
| F39 course-test feature flags | Implemented and activated | Live configuration was enabled according to `F39_TEST_ROLLOUT.r1.md`. |
| Maximum two active receiving adoptions | Implemented and deployed | Server transaction maintains `adoptionReceivingCapacity/<uid>`; focused concurrency/release test passed. |
| Unlimited pet offering | Implemented policy | Receiving capacity does not limit the number of pets a user may offer. |
| Capacity release | Implemented | Rejection, withdrawal, pre-handover cancellation, completed return, and final 90-day completion release a slot. |
| Unified three-stage adoption timeline | Implemented, device journey incomplete | Summary stages exist; full two-account progression on the current APK is still unverified. |
| Owner request review | Existing/partially wired | Must be exercised end to end for accept, reject with reason, and notification behavior. |
| Adoption chat reuse | Implemented previously | Adoption messages use the existing chat area; verify discoverability and both accounts on the current build. |
| Two-party handover confirmation | Existing/partially verified | Must prove both participants can confirm and that one participant alone cannot complete the adoption. |
| My Pets / Adopting tabs | Implemented | Current-build checks passed on one device; cross-account data and completed-pet movement remain to verify. |
| Adopted pet moved into managed pets | Intended/existing backend path, not currently proven end to end | Verify after a real completed handover and monitoring transition. |
| Dog/cat account-avatar presets | Removed | Google photo, uploaded photo, and default avatar remain. |
| Profile and author cache behavior | Previously implemented, regression verification needed | Ensure refresh updates author name/avatar without per-bind Firebase reads or restart-only refresh. |
| F28 participant reviews | Partial | Domain code exists. Deployed callable/read adapter, real composer, profile aggregate, and two-account verification are not implemented. Static fake sample entry is hidden. |
| 90-day follow-up | Partial | Existing foundation/status paths exist, but the complete cross-account journey and final managed-pet transition are not proven on the current build. |
| Vaccination reminders | Not implemented | Due-date schema, scheduler, notification behavior, UI integration, and tests remain. |
| Admin contact review | Not implemented | Command, scoped UI, audit behavior, and tests remain. It must not impersonate Firebase OTP verification. |
| Lost/found | Intentionally not implemented/hidden | Latest approved scope says to leave it out. |
| Veterinary map/nearby services | Not operational/hidden | Requires Maps/Places configuration, API key, billing/quota, and a real data provider before exposure. |
| Fake/static review UI | Removed from navigation | Do not restore until backed by real F28 operations. |
| Dark-mode sub-feature text | Implemented for the audited screens | Build/lint and one-device visual evidence exist; not every legacy destination was manually traversed. |
| Dedicated care/support icons | Implemented draft | Source mapping and one-device dark-mode check passed; large-text/landscape visual coverage remains. |
| Current two-device adoption test | Not completed | One Xiaomi device rejected installation with `INSTALL_FAILED_USER_RESTRICTED` during the latest unified-adoption pass. |
| Current notification test on both devices | Not completed | Must be included in the real two-account journey using the same current APK. |
| Final implementation/audit report | Not complete | Update planning evidence after the remaining runtime work and approved gaps are finished. |

## Work performed during the handoff turn

This handoff turn did not modify application code, Firebase data, Firebase configuration, deployments, user accounts, or permissions. It inspected the repository and planning evidence and created only:

- `CODEX_HANDOFF_2026-10-21.md`
- `CODEX_CONTINUATION_PROMPT.txt`

Both files are currently untracked. All application changes described above predate this documentation turn and remain in the existing dirty working tree or cited deployment reports.

## Verified results already recorded

From `UNIFIED_ADOPTION.r1.md`:

- `node --test functions/test/adoption-flow.test.mjs`: 9/9 passed.
- `gradlew :app:compileSparkJavaWithJavac`: passed.
- `gradlew :app:assembleSpark`: passed.
- `git diff --check`: passed after newline correction.
- Native Impeccable source audit completed for that slice.
- On `2606FRN72Y`, the then-current Spark APK passed signed-in pet tabs, empty adopting state, system Back, dark mode, and 1.3 font scale.
- `M2010J19SG` rejected installation with `INSTALL_FAILED_USER_RESTRICTED`, so the latest two-device journey was not completed.

These are historical results for the recorded diff. Re-run only checks affected by subsequent changes, following `VERIFICATION.md`; do not claim current runtime coverage from compilation alone.

## Known incomplete or blocked work

The unified adoption report is explicitly **partial**, not complete:

1. Run the current Spark build through a real two-account journey on both devices after installing the same current APK:
   - browse/open an adoption listing;
   - submit as adopter;
   - owner reviews and accepts/rejects;
   - verify notification/chat entry;
   - confirm exchange/handover by both participants;
   - verify completion/history and managed-pet ownership;
   - verify the two-active-adoption capacity rule and capacity release.
2. F28 has domain code but lacks deployed callable/read adapter and real composer/profile aggregate wiring. Keep the fake static screen hidden until real operations and two-account tests exist.
3. Vaccination due-date schema/scheduler and Admin contact-review command/UI are unfinished.
4. Admin contact review must remain separate from Firebase OTP; do not fabricate phone verification.
5. Check the large uncommitted UI/navigation diff against the latest reviewer feedback and current routes before adding more code.
6. Confirm that all ordinary authenticated users follow the current approved access policy while ownership, private records, participant boundaries, restrictions, and scoped Admin capabilities remain enforced server-side.
7. The latest installation failure on `M2010J19SG` was a Xiaomi user-restriction prompt, not an app compile failure.

## Product decisions that must remain consistent

- Use `người cho nhận nuôi` / `chủ thú cưng` and `người nhận nuôi`; this app does not sell pets and must not use seller/buyer/payment language.
- Basic adoption is for authenticated ordinary members; no legacy protected-member role is required.
- A verified Firebase phone number is required when entering adoption actions that require it. The profile phone row opens verification and is not a freely editable text field.
- Phone format validity alone is not verification.
- A user may receive at most two pets in active adoption workflows; offering pets is unlimited.
- Do not auto-complete adoption from a single request action. Owner review and both-party handover confirmation remain required.
- Admin/moderation power does not erase ownership or participant authorization and must remain audited/scoped in Firebase.
- Preserve lazy caching and bounded reads; do not reintroduce per-scroll/per-bind Firebase reads or author-name/avatar flicker.
- Use the shared main shell and correct system Back behavior. Avoid opening duplicate Activities for primary bottom-navigation destinations.
- Build and test the Firebase-connected app with `spark`, not `debug`.

## Recommended continuation order

1. Read the mandatory instructions and the five exact analyses/receipts.
2. Inspect `git status`, the full diff, and `UNIFIED_ADOPTION.r1.md` before changing files.
3. Build `:app:assembleSpark` once if the diff has changed since the recorded evidence.
4. Install the same APK on both devices without clearing app data or changing test identities.
5. Perform focused two-account adoption verification and capture logs/screenshots only where needed.
6. Fix failures found by that journey, then rerun only the affected checks.
7. Complete the approved F28 adapter/UI and vaccination/Admin-contact gaps only within the exact approved analyses.
8. Update planning implementation reports with concrete evidence and unresolved blockers.

## Handoff completion criteria

Before declaring this work complete, require current evidence for build/resource compilation, affected unit/Functions tests, server-side ownership and capacity controls, two-account adoption progression, notification/chat reachability, Back navigation, dark/light readability on affected screens, and truthful error/success feedback. Report anything that could not be exercised on a device or live Firebase project.
