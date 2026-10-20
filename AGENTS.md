# ANILOVE mobile app agent entry point

Mobile app root: `D:\dan\Mobile_Project`.
Planning/workflow root: `C:\Users\Minht\Desktop\New folder`.

Before ANILOVE feature work, read the planning root's `AGENTS.md`, `implementation/AGENTS.md`, and `implementation/WORKFLOW.md`. Use its context/index and exact accepted analyses as the approval source. All planning-relative paths in those instructions resolve against the planning root, not this mobile folder.

Hard approval gate: no complete, presented detailed analysis means no feature approval, regardless of who attempts it. Follow the planning rule's approval_gate.py prepare/check process and require fresh exact-revision approval; do not reuse approval messages that predate the draft.

Applicable project-local skills are in the planning root's `.agents/skills/anilove-ui/SKILL.md` and `.agents/skills/anilove-firebase-code/SKILL.md`. UI discovery uses Figma Bridge; new/materially changed implemented UI requires Impeccable's native audit. No separate custom external backend.

This project is currently a native Android starter using Java and XML layouts. Preserve the existing stack unless the user requests an approved change. Put app code/config/tests here; keep requests, analyses, approval, and implementation tracking in the planning root. Exported roadmap/handoff files here are planning snapshots, not approval evidence. Never print secrets from local configuration.

## Requested-scope boundary — 14 October 2026

Follow planning implementation/WORKFLOW.md v2.1. Bare implement-feature means non-UI domain/data code and applicable tests/audit only. Do not modify presentation/Figma/layouts/navigation or wire the main app unless explicitly requested. UI design, UI-code implementation and wiring are separate flows; UI requirement flags grant no execution permission.

## Focused verification — 20 October 2026

Read planning `implementation/VERIFICATION.md` with the workflow. Default to checks for changed behavior; reuse applicable prior evidence, rerun only affected failures and stop when relevant checks pass. No full suite/device matrix/clean-build loop without concrete cause. Required authorization/data-integrity/native checks remain; report blocked required coverage honestly.
