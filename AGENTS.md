# ANILOVE mobile app agent entry point

Mobile app root: `D:\dan\Mobile_Project`.
Planning/workflow root: `C:\Users\Minht\Desktop\New folder`.

Before ANILOVE feature work, read the planning root's `AGENTS.md`, `implementation/AGENTS.md`, and `implementation/WORKFLOW.md`. Use its context/index and exact accepted analyses as the approval source. All planning-relative paths in those instructions resolve against the planning root, not this mobile folder.

Hard approval gate: no complete, presented detailed analysis means no feature approval, regardless of who attempts it. Follow the planning rule's approval_gate.py prepare/check process and require fresh exact-revision approval; do not reuse approval messages that predate the draft.

Applicable project-local skills are in the planning root's `.agents/skills/anilove-ui/SKILL.md` and `.agents/skills/anilove-firebase-code/SKILL.md`. UI discovery uses Figma Bridge; new/materially changed implemented UI requires Impeccable's native audit. No separate custom external backend.

This project is currently a native Android starter using Java and XML layouts. Preserve the existing stack unless the user requests an approved change. Put app code/config/tests here; keep requests, analyses, approval, and implementation tracking in the planning root. Exported roadmap/handoff files here are planning snapshots, not approval evidence. Never print secrets from local configuration.
