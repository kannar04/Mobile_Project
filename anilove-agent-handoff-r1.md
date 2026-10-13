# ANILOVE roadmap — agent handoff

Roadmap revision: 1. Source context revision: 2.
Scope: course prototype; simulated adoptions and welfare cases.
**This is planning state, not approval evidence or verified implementation. Reconcile with the workspace before changing authoritative files.**

Current focus: F42 — Foundation analysis.
Requests are registered. Detailed analysis and approval are the next steps; implementation progress has not been verified.

## Agent instructions
1. Read roadmap/UPDATE_RULE.md and the parent ANILOVE-feature-analysis-rule.md once. Read current parent context.md and relevant index.csv rows.
2. Register any unregistered proposal in request_feature/<id>-<slug>/README.md before analysis. Detect ID collisions; do not overwrite another request.
3. Read only the target request and necessary approved dependencies. Analyze before implementation when no approved design exists.
4. Save a revisioned draft in analysted. Only exact user approval allows done in the index and promotion to context.
5. Preserve approval versus implementation states separately. Verify completion evidence; browser claims are not proof.
6. Reconcile the exported JSON with roadmap/data/roadmap.json, validate dependencies and changes, then regenerate the local data wrapper.

## Selected feature: F42 — Firebase services, trusted operations, cost, and security
Firestore as required by course, Auth, media storage choice, protected roles, Firestore/Storage rules, trusted execution for scheduled/privileged work, bounded queries/uploads, and separate demo configuration.
Prerequisites: none.
Analysis: needs_analysis. Implementation: not_started. Registration: registered.
Request: ../request_feature/F42-firebase-services-trusted-operations-cost-and-security/README.md.
Notes: none.
Implementation evidence: none recorded.

## Feature state
| ID | Feature | Analysis | Build | Prerequisites |
|---|---|---|---|---|
| F01 | Login & Google sign-in | needs_analysis | not_started | F42 |
| F02 | Account recovery | needs_analysis | not_started | F01 |
| F03 | Profiles & privacy | needs_analysis | not_started | F01 |
| F04 | Factual verification badges | needs_analysis | not_started | F01 |
| F05 | Admin roles & restrictions | needs_analysis | not_started | F01, F03 |
| F06 | Pet identity | needs_analysis | not_started | F01, F03 |
| F07 | Listing lifecycle | needs_analysis | not_started | F06 |
| F08 | Health & care disclosure | needs_analysis | not_started | F06 |
| F09 | Pet photos | needs_analysis | not_started | F07, F42 |
| F10 | Approval & availability | needs_analysis | not_started | F05, F07, F09 |
| F11 | Species, breeds & regions | needs_analysis | not_started | F42 |
| F12 | Public browse & detail | needs_analysis | not_started | F10, F08 |
| F13 | Search & filters | needs_analysis | not_started | F12, F11 |
| F14 | Favorites | needs_analysis | not_started | F01, F12 |
| F15 | Share listings | needs_analysis | not_started | F12 |
| F16 | Suitability questions | needs_analysis | not_started | F01, F08, F12 |
| F17 | Adoption applications | needs_analysis | not_started | F16, F12 |
| F18 | Select & reserve | needs_analysis | not_started | F17, F07 |
| F19 | Private contact / chat | needs_analysis | not_started | F18 |
| F20 | Agreement & meeting | needs_analysis | not_started | F18, F08 |
| F21 | Two-party handover | needs_analysis | not_started | F20 |
| F22 | Placement history | needs_analysis | not_started | F21 |
| F23 | Private follow-up | needs_analysis | not_started | F21 |
| F24 | Due tasks & extensions | needs_analysis | not_started | F23 |
| F25 | Welfare case review | needs_analysis | not_started | F23, F29 |
| F26 | Supported returns | needs_analysis | not_started | F25, F21 |
| F27 | Appeals & restrictions | needs_analysis | not_started | F25, F05 |
| F28 | Participant reviews | needs_analysis | not_started | F21, F29 |
| F29 | Report content & accounts | needs_analysis | not_started | F01, F12, F39 |
| F30 | Notifications & inbox | needs_analysis | not_started | F18, F10 |
| F31 | Community feed & news | needs_analysis | not_started | F01, F39, F29 |
| F32 | Lost & found | needs_analysis | not_started | F06, F29 |
| F33 | Temporary care | needs_analysis | not_started | F20, F25 |
| F34 | Pet exchange | needs_analysis | not_started | F18, F21, F25 |
| F35 | Nearby pets & maps | needs_analysis | not_started | F12, F11 |
| F36 | Care-date reminders | needs_analysis | not_started | F21, F08 |
| F37 | Pet recommendations | needs_analysis | not_started | F12, F13 |
| F38 | Rotation & accessible UI | needs_analysis | not_started | F42 |
| F39 | Mobile Admin tools | needs_analysis | not_started | F05, F10 |
| F40 | Dashboard & statistics | needs_analysis | not_started | F10, F21, F29 |
| F41 | SQLite & offline pages | needs_analysis | not_started | F12, F07, F01 |
| F42 | Firebase & access rules | needs_analysis | not_started | none |
| F43 | Deletion & retention | needs_analysis | not_started | F01, F21, F29 |
| F44 | Help & care guidance | needs_analysis | not_started | F01, F25 |

## Latest planning changes
- 2026-10-11 / baseline:  Registered 44 source requests and proposed a dependency roadmap. No feature approval or implementation completion inferred.

## Approval boundary
No plan order, branch insertion, browser status edit, file export, or unregistered feature is approval of an analysis. Preserve the existing approval-gated workflow.

Pair this brief with the full JSON export for exact dependency, milestone and state data.
