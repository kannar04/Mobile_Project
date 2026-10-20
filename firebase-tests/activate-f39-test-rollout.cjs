// Explicit course-test rollout. This does not weaken ownership, participant,
// assignment, conflict, version, restriction, or protected-write checks.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { createRequire } = require('node:module');
const { getGlobalDefaultAccount, getAccessToken } = require('firebase-tools/lib/auth');
const firebaseApi = require('firebase-tools/lib/api');

const managed = createRequire(path.resolve(__dirname, '../functions/package.json'));
const projectId = 'aniloveproject';

async function connect() {
  const account = getGlobalDefaultAccount();
  assert(account?.tokens?.refresh_token, 'firebase_cli_login_required');
  const credentials = {
    type: 'authorized_user',
    client_id: firebaseApi.clientId(),
    client_secret: firebaseApi.clientSecret(),
    refresh_token: account.tokens.refresh_token,
  };
  // Refresh once before opening Firestore so an expired CLI session fails closed.
  await getAccessToken(account.tokens.refresh_token, ['https://www.googleapis.com/auth/cloud-platform']);
  const { Firestore } = managed('@google-cloud/firestore');
  return new Firestore({ projectId, credentials });
}

function requireEvidence() {
  const audit = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../build/two-device/integrity-audit.json'), 'utf8'));
  assert.equal(audit.passed, true, 'two_device_audit_required');
  assert.equal(audit.independentResolution, true, 'independent_resolution_required');
  assert.equal(audit.uncertainCommitRecovered, true, 'retry_recovery_required');
  assert.equal(audit.twoDistinctImageReaders, true, 'two_device_media_required');
  assert.deepEqual(audit.cases.map(x => x.status), ['FINALIZED', 'CANCELLED_BEFORE_HANDOVER', 'CANCELLED_BEFORE_HANDOVER']);
}

async function main() {
  requireEvidence();
  const apply = process.argv.includes('--apply');
  const receiptPath = path.join(process.env.USERPROFILE, '.anilove', 'demo-adoption-reviewer.json');
  assert(fs.existsSync(receiptPath), 'independent_reviewer_receipt_required');
  const reviewerUid = JSON.parse(fs.readFileSync(receiptPath, 'utf8')).uid;
  assert.equal(typeof reviewerUid, 'string');
  const db = await connect();
  const auditId = `f39_test_rollout_${randomUUID()}`;
  let result;
  try {
    result = await db.runTransaction(async tx => {
      const refs = {
        participation: db.doc('participationPolicy/current'),
        lifecycle: db.doc('adoptionLifecyclePolicy/current'),
        workflow: db.doc('adoptionWorkflowPolicy/current'),
        browse: db.doc('adoptionBrowseRelease/current'),
        notifications: db.doc('notificationPolicy/current'),
        moderation: db.doc('system/moderationCapacity'),
        reviewer: db.doc(`operatorCapabilities/${reviewerUid}`),
        audit: db.doc(`f39RolloutAudit/${auditId}`),
      };
      const snapshots = await Promise.all(Object.values(refs).slice(0, 7).map(ref => tx.get(ref)));
      const [participation, lifecycle, workflow, browse, notifications, moderation, reviewer] = snapshots.map(s => s.data());
      assert.equal(lifecycle?.schemaVersion, 2, 'lifecycle_policy_missing');
      assert.equal(lifecycle?.policyVersion, 2, 'lifecycle_policy_version');
      assert.equal(lifecycle?.termsVersion, 1, 'lifecycle_terms_version');
      assert.equal(reviewer?.schemaVersion, 1, 'reviewer_missing');
      assert.equal(reviewer?.active, true, 'reviewer_inactive');
      assert(Array.isArray(reviewer.capabilities) && reviewer.capabilities.includes('adoption_lifecycle'), 'independent_reviewer_missing');
      const now = new Date();
      const capabilities = [...new Set([...reviewer.capabilities, 'decision_review'])];
      const writes = {
        participation: { schemaVersion: 1, policyVersion: Math.max(1, participation?.policyVersion ?? 0), mode: 'course_simplified', enabled: true, requireVerifiedEmailForAuthoring: true, phoneRequired: false, publicProfileRequired: false, termsVersion: 1, sourceSchema: 3, updatedAt: now, auditRef: auditId },
        lifecycle: { ...lifecycle, enabled: true, jobsEnabled: true, pushEnabled: true, courseDemoOnly: true, updatedAt: now, auditRef: auditId },
        workflow: { ...workflow, schemaVersion: 2, commandsEnabled: true, publicationEnabled: true, writerSetVersion: 2, reviewCapacityVerified: true, updatedAt: now, reviewNote: 'Explicit user-authorized full course testing rollout', auditRef: auditId },
        browse: { ...browse, schemaVersion: 1, version: Math.max(1, browse?.version ?? 0), enabled: true, applicationsEnabled: true, updatedAt: now, auditRef: auditId },
        notifications: { ...notifications, schemaVersion: 1, enabled: true, updatedAt: now, auditRef: auditId },
        moderation: { ...moderation, schemaVersion: 1, mode: 'course_manual_owner', staffingVerified: true, supportedPostSchemas: [2, 4, 5, 6], updatedAt: now, auditRef: auditId },
        reviewer: { ...reviewer, capabilities, version: reviewer.version + (capabilities.length === reviewer.capabilities.length ? 0 : 1), updatedAt: now, auditRef: auditId },
      };
      if (apply) {
        for (const key of ['participation', 'lifecycle', 'workflow', 'browse', 'notifications', 'moderation', 'reviewer']) tx.set(refs[key], writes[key]);
        tx.create(refs.audit, { schemaVersion: 1, auditId, mode: 'course_test_full', explicitUserOverride: true, approvalSource: '2026-10-20 user instruction to enable all F39-gated features for testing', preservedServerGuards: true, independentReviewerUid: reviewerUid, policiesEnabled: ['participation', 'adoption_lifecycle', 'adoption_jobs', 'adoption_push', 'adoption_publication', 'adoption_applications', 'notifications'], createdAt: now });
      }
      return { apply, auditId, policiesEnabled: 7, independentReviewerReady: capabilities.includes('decision_review') };
    });
  } finally {
    await db.terminate();
  }
  console.log(JSON.stringify(result));
}

main().catch(error => {
  console.error(/^[a-z0-9_]+$/.test(error.message) ? error.message : 'f39_test_rollout_failed');
  process.exitCode = 1;
});
