// Trusted synthetic-demo tooling only, never imported into Android or used against cloud.
import { doc, runTransaction, serverTimestamp } from 'firebase/firestore';
import assert from 'node:assert/strict';

export async function publishReviewed(db, uid, reviewedVersion, reviewerRef) {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST ?? '', /^(127\.0\.0\.1|localhost):8180$/);
  assert.equal(db.app.options.projectId, 'demo-anilove');
  assert.ok(reviewerRef && reviewerRef.length <= 100);
  return runTransaction(db, async tx => {
    const draftRef=doc(db,`users/${uid}/profileDraft/current`), publicRef=doc(db,`publicProfiles/${uid}`);
    const draft=(await tx.get(draftRef)).data();
    const controls=await tx.get(doc(db,`publicProfileControls/${uid}`));
    const subject=(await tx.get(doc(db,`securitySubjects/${uid}`))).data();
    const prior=(await tx.get(publicRef)).data();
    const reviewRef=doc(db,`users/${uid}/profileReviews/published-${reviewedVersion}`);
    const previousReview=await tx.get(reviewRef);
    assert.equal(subject?.status,'active');
    assert.equal(draft?.ownerUid,uid);assert.equal(draft?.version,reviewedVersion);assert.equal(draft?.submission,'submitted');
    assert.deepEqual(Object.keys(draft).sort(),['ownerUid','schemaVersion','version','displayName','avatarKey','regionCode','bio','submission','lastOperationId','updatedAt'].sort());
    assert.equal(draft.schemaVersion,1);
    assert.ok(Array.from(draft.displayName).length>=2 && Array.from(draft.displayName).length<=40);
    assert.ok(Array.from(draft.bio).length<=500);
    assert.ok(['default','cat','dog'].includes(draft.avatarKey));assert.ok(draft.regionCode===null || ['north','central','south'].includes(draft.regionCode));
    assert.ok(!/(?:\+?\d[\d .()\-]{5,}\d|[\w.+-]+@[\w.-]+\.[a-z]{2,}|địa chỉ|address|đường|street|phường|số nhà)/iu.test(draft.displayName+'\n'+draft.bio),'manual contact correction required');
    if(controls.exists()) {assert.equal(controls.data().ownerUid,uid);assert.ok(['hidden','public'].includes(controls.data().visibility));}
    // Read control revision to serialize with owner hide; never write controls or infer public intent.
    if(prior) {
      assert.deepEqual(Object.keys(prior).sort(),['ownerUid','schemaVersion','publicationVersion','sourceDraftVersion','displayName','avatarKey','regionCode','bio','publishedAt'].sort());
      assert.equal(prior.ownerUid,uid);assert.equal(prior.schemaVersion,1);
      assert.ok(Number.isSafeInteger(prior.publicationVersion) && prior.publicationVersion>0);
      assert.ok(Number.isSafeInteger(prior.sourceDraftVersion) && prior.sourceDraftVersion>0);
    }
    if(prior?.sourceDraftVersion===reviewedVersion) {
      for(const field of ['displayName','avatarKey','regionCode','bio'])assert.equal(prior[field],draft[field]);
      assert.equal(previousReview.data()?.draftVersion,reviewedVersion);
      assert.equal(previousReview.data()?.decision,'published');
      assert.equal(previousReview.data()?.publicationVersion,prior.publicationVersion);
      return prior;
    }
    assert.equal(previousReview.exists(),false,'immutable review history cannot be overwritten');
    const publicationVersion=(prior?.publicationVersion??0)+1;
    const projection={ownerUid:uid,schemaVersion:1,publicationVersion,sourceDraftVersion:reviewedVersion,
      displayName:draft.displayName,avatarKey:draft.avatarKey,regionCode:draft.regionCode,bio:draft.bio,publishedAt:serverTimestamp()};
    tx.set(publicRef,projection);
    tx.set(reviewRef,{draftVersion:reviewedVersion,decision:'published',feedback:'Synthetic demo revision reviewed.',reviewerRef,publicationVersion,reviewedAt:serverTimestamp()});
    return projection;
  },{maxAttempts:3});
}
