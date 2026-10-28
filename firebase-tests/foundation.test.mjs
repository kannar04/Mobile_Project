import { before, after, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, signOut, signInWithEmailAndPassword,
  sendEmailVerification, reload, reauthenticateWithCredential, EmailAuthProvider,
  GoogleAuthProvider, linkWithCredential, signInWithCredential } from 'firebase/auth';
import { getFirestore, connectFirestoreEmulator, doc, setDoc, getDoc, getDocs, deleteDoc,
  updateDoc, collection, collectionGroup, query, limit, orderBy, startAfter, serverTimestamp,
  Timestamp, runTransaction, documentId } from 'firebase/firestore';

const projectId = 'demo-anilove';
let env;
const subject = status => ({ status, roles: ['member'], updatedAt: Timestamp.now() });
const config = () => ({ schemaVersion: 1, environmentLabel: 'local_emulator', featureMode: 'course_demo', updatedAt: Timestamp.now() });
const data = (uid, id) => ({ ownerUid: uid, operationId: id, schemaVersion: 1, createdAt: serverTimestamp() });
const operations = (db, uid) => collection(db, `foundationChecks/${uid}/operations`);
const operation = (db, uid, id) => doc(operations(db, uid), id);
const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const visitor = () => env.unauthenticatedContext().firestore();

before(async () => {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST ?? '', /^(127\.0\.0\.1|localhost):8180$/);
  assert.ok(process.env.FIREBASE_AUTH_EMULATOR_HOST, 'Auth tests must never contact cloud');
  env = await initializeTestEnvironment({ projectId, firestore: {
    host: '127.0.0.1', port: 8180, rules: await readFile('../firestore.rules', 'utf8')
  }});
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const db = ctx.firestore();
    await Promise.all([
      setDoc(doc(db, 'system/config'), config()),
      setDoc(doc(db, 'securitySubjects/alice'), subject('active')),
      setDoc(doc(db, 'securitySubjects/bob'), subject('active')),
      setDoc(doc(db, 'securitySubjects/restricted'), subject('restricted'))
    ]);
  });
});

test('visitor can get only safe config; cannot enumerate or write it', async () => {
  const db = visitor();
  await assertSucceeds(getDoc(doc(db, 'system/config')));
  await assertFails(getDocs(collection(db, 'system')));
  await assertFails(setDoc(doc(db, 'system/config'), config()));
  await assertFails(getDoc(doc(db, 'securitySubjects/alice')));
});
test('operator accidentally adds private field: public get is denied', async () => {
  await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), 'system/config'), { ...config(), secret: 'synthetic-only' }));
  await assertFails(getDoc(doc(visitor(), 'system/config')));
});
test('only owner may get authority; roles/status cannot be self-granted', async () => {
  const db = alice();
  await assertSucceeds(getDoc(doc(db, 'securitySubjects/alice')));
  await assertFails(getDoc(doc(db, 'securitySubjects/bob')));
  await assertFails(getDocs(collection(db, 'securitySubjects')));
  await assertFails(updateDoc(doc(db, 'securitySubjects/alice'), { roles: ['admin'] }));
  await assertFails(setDoc(doc(db, 'securitySubjects/alice'), subject('active')));
  await assertFails(deleteDoc(doc(db, 'securitySubjects/alice')));
});
test('active owner creates, reads and deletes exact immutable diagnostic', async () => {
  const db = alice(), id = randomUUID(), ref = operation(db, 'alice', id);
  await assertSucceeds(setDoc(ref, data('alice', id)));
  const snapshot = await assertSucceeds(getDoc(ref));
  assert.equal(snapshot.data().operationId, id);
  assert.ok(snapshot.data().createdAt instanceof Timestamp);
  await assertFails(updateDoc(ref, { schemaVersion: 2 }));
  await assertFails(setDoc(ref, data('alice', id)));
  await assertSucceeds(deleteDoc(ref));
});
test('cross-user access fails even with forged owner and valid timestamp', async () => {
  const id = randomUUID();
  await setDoc(operation(alice(), 'alice', id), data('alice', id));
  await assertFails(getDoc(operation(bob(), 'alice', id)));
  await assertFails(getDocs(query(operations(bob(), 'alice'), limit(20))));
  const forgedId = randomUUID();
  await assertFails(setDoc(operation(bob(), 'alice', forgedId), data('alice', forgedId)));
  await assertFails(deleteDoc(operation(bob(), 'alice', id)));
});
test('unauthenticated private create/get/list/delete denied', async () => {
  const db = visitor(), id = randomUUID();
  await assertFails(setDoc(operation(db, 'alice', id), data('alice', id)));
  await assertFails(getDoc(operation(db, 'alice', id)));
  await assertFails(getDocs(query(operations(db, 'alice'), limit(20))));
  await assertFails(deleteDoc(operation(db, 'alice', id)));
});
test('restricted or missing subject cannot create or delete diagnostics', async () => {
  for (const uid of ['restricted', 'missing']) {
    const db = env.authenticatedContext(uid).firestore(), id = randomUUID();
    await assertFails(setDoc(operation(db, uid, id), data(uid, id)));
    await assertFails(deleteDoc(operation(db, uid, id)));
  }
});
test('strict create schema rejects malicious and incomplete fields', async () => {
  const db = alice();
  const cases = [
    d => ({ ...d, ownerUid: 'bob' }), d => ({ ...d, operationId: randomUUID() }),
    d => ({ ...d, schemaVersion: 2 }), d => ({ ...d, schemaVersion: '1' }),
    d => ({ ...d, role: 'admin' }), d => ({ ...d, payload: 'arbitrary data' }),
    d => ({ ...d, createdAt: Timestamp.fromMillis(1) }),
    d => { const { createdAt, ...rest } = d; return rest; }
  ];
  for (const transform of cases) {
    const id = randomUUID();
    await assertFails(setDoc(operation(db, 'alice', id), transform(data('alice', id))));
  }
  await assertFails(setDoc(operation(db, 'alice', 'not-a-uuid'), data('alice', 'not-a-uuid')));
});
test('unknown domain and parent paths remain denied', async () => {
  const db = alice();
  for (const path of ['profiles/alice', 'listings/pet', 'welfareCases/case', 'foundationChecks/alice', 'system/other']) {
    await assertFails(getDoc(doc(db, path)));
    await assertFails(setDoc(doc(db, path), { ownerUid: 'alice' }));
  }
});
test('no collection-group enumeration', async () => {
  await assertFails(getDocs(query(collectionGroup(alice(), 'operations'), limit(20))));
});
test('query without limit or over 20 denied; bounded descending cursor succeeds', async () => {
  const db = alice();
  await env.withSecurityRulesDisabled(async ctx => {
    for (let i = 0; i < 23; i++) {
      const id = randomUUID();
      await setDoc(operation(ctx.firestore(), 'alice', id), { ownerUid: 'alice', operationId: id, schemaVersion: 1, createdAt: Timestamp.fromMillis(1000 + Math.floor(i / 2)) });
    }
  });
  await assertFails(getDocs(operations(db, 'alice')));
  await assertFails(getDocs(query(operations(db, 'alice'), limit(21))));
  const first = await assertSucceeds(getDocs(query(operations(db, 'alice'), orderBy('createdAt', 'desc'), orderBy(documentId(), 'desc'), limit(20))));
  assert.equal(first.size, 20);
  const last = first.docs.at(-1);
  const second = await assertSucceeds(getDocs(query(operations(db, 'alice'), orderBy('createdAt', 'desc'), orderBy(documentId(), 'desc'), startAfter(last.data().createdAt, last.id), limit(20))));
  assert.equal(second.size, 3);
  assert.equal(new Set([...first.docs, ...second.docs].map(d => d.id)).size, 23);
});
async function diagnostic(db, uid, id) {
  return runTransaction(db, async tx => {
    const authority = await tx.get(doc(db, `securitySubjects/${uid}`));
    assert.equal(authority.data()?.status, 'active');
    const ref = operation(db, uid, id), previous = await tx.get(ref);
    if (previous.exists()) {
      assert.equal(previous.data().ownerUid, uid);
      assert.equal(previous.data().operationId, id);
      assert.equal(previous.data().schemaVersion, 1);
    } else tx.set(ref, data(uid, id));
  }, { maxAttempts: 3 });
}
test('two clients race same ID and safe retry leaves exactly one unchanged record', async () => {
  const a = alice(), b = alice(), id = randomUUID();
  await Promise.all([diagnostic(a, 'alice', id), diagnostic(b, 'alice', id)]);
  const before = (await getDoc(operation(a, 'alice', id))).data().createdAt;
  await diagnostic(a, 'alice', id);
  const after = (await getDoc(operation(a, 'alice', id))).data().createdAt;
  assert.ok(before.isEqual(after));
  assert.equal((await getDocs(query(operations(a, 'alice'), limit(20)))).size, 1);
});
test('operator restriction blocks future writes without pretending cache was erased', async () => {
  const db = alice(), id = randomUUID();
  await diagnostic(db, 'alice', id);
  await env.withSecurityRulesDisabled(ctx => updateDoc(doc(ctx.firestore(), 'securitySubjects/alice'), { status: 'restricted' }));
  const newId = randomUUID();
  await assertFails(setDoc(operation(db, 'alice', newId), data('alice', newId)));
  await assertFails(deleteDoc(operation(db, 'alice', id)));
});
test('real Auth emulator session signs out and cannot read another account', async () => {
  const app = initializeApp({ projectId, apiKey: 'emulator-only', appId: 'emulator-only' }, randomUUID());
  const auth = getAuth(app);
  connectAuthEmulator(auth, 'http://127.0.0.1:9199', { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, '127.0.0.1', 8180);
  try {
    const a = await createUserWithEmailAndPassword(auth, `a-${randomUUID()}@example.test`, 'synthetic-pass-123');
    const uidA = a.user.uid;
    await env.withSecurityRulesDisabled(ctx => setDoc(doc(ctx.firestore(), `securitySubjects/${uidA}`), subject('active')));
    const id = randomUUID();
    await diagnostic(db, uidA, id);
    await signOut(auth);
    await assertFails(getDoc(operation(db, uidA, id)));
    await createUserWithEmailAndPassword(auth, `b-${randomUUID()}@example.test`, 'synthetic-pass-123');
    await assertFails(getDoc(operation(db, uidA, id)));
  } finally { await deleteApp(app); }
});

test('F01 own exact shell create/get before activation; no subject self-enrollment', async () => {
  const db = env.authenticatedContext('unactivated').firestore();
  const ref = doc(db, 'users/unactivated');
  await assertSucceeds(setDoc(ref, { uid: 'unactivated', schemaVersion: 1, createdAt: serverTimestamp() }));
  await assertSucceeds(getDoc(ref));
  await assertFails(setDoc(doc(db, 'securitySubjects/unactivated'), subject('active')));
  const id = randomUUID();
  await assertFails(setDoc(operation(db, 'unactivated', id), data('unactivated', id)));
});
test('F01 shell roles, verification, email, credentials and extra payload are denied', async () => {
  const db = alice();
  for (const [key, value] of [['role','admin'],['emailVerified',true],['email','a@example.test'],['password','synthetic'],['token','synthetic']]) {
    await assertFails(setDoc(doc(db, 'users/alice'), { uid:'alice', schemaVersion:1, createdAt:serverTimestamp(), [key]:value }));
  }
  await assertFails(setDoc(doc(db,'users/alice'), { uid:'bob', schemaVersion:1, createdAt:serverTimestamp() }));
  await assertFails(setDoc(doc(db,'users/alice'), { uid:'alice', schemaVersion:2, createdAt:serverTimestamp() }));
  await assertFails(setDoc(doc(db,'users/alice'), { uid:'alice', schemaVersion:1, createdAt:Timestamp.fromMillis(1) }));
});
test('F01 shell cannot be enumerated, changed or deleted; cross-UID and visitor denied', async () => {
  const ref = doc(alice(),'users/alice');
  await setDoc(ref, { uid:'alice', schemaVersion:1, createdAt:serverTimestamp() });
  await assertFails(getDoc(doc(bob(),'users/alice')));
  await assertFails(getDoc(doc(visitor(),'users/alice')));
  await assertFails(setDoc(doc(bob(),'users/alice'), { uid:'alice', schemaVersion:1, createdAt:serverTimestamp() }));
  await assertFails(getDocs(collection(alice(),'users')));
  await assertFails(updateDoc(ref,{ schemaVersion:2 }));
  await assertFails(deleteDoc(ref));
});
async function ensureShell(db, uid) {
  return runTransaction(db, async tx => {
    const ref = doc(db,`users/${uid}`), previous = await tx.get(ref);
    if (!previous.exists()) tx.set(ref,{ uid,schemaVersion:1,createdAt:serverTimestamp() });
    else { assert.equal(previous.data().uid,uid); assert.equal(previous.data().schemaVersion,1); }
  }, {maxAttempts:3});
}
test('F01 simultaneous bootstrap and retry keep one immutable UID shell', async () => {
  await Promise.all([ensureShell(alice(),'alice'),ensureShell(alice(),'alice')]);
  const created = (await getDoc(doc(alice(),'users/alice'))).data().createdAt;
  await ensureShell(alice(),'alice');
  assert.ok(created.isEqual((await getDoc(doc(alice(),'users/alice'))).data().createdAt));
});
test('F01 real Auth registration, partial setup retry, verification send and same-UID sign-in', async () => {
  const app = initializeApp({projectId,apiKey:'emulator-only',appId:'emulator-only'},randomUUID());
  const auth = getAuth(app); connectAuthEmulator(auth,'http://127.0.0.1:9199',{disableWarnings:true});
  const db = getFirestore(app); connectFirestoreEmulator(db,'127.0.0.1',8180);
  const email=`f01-${randomUUID()}@example.test`, password='synthetic-password-123';
  try {
    const account=await createUserWithEmailAndPassword(auth,email,password), uid=account.user.uid;
    assert.equal(account.user.emailVerified,false);
    assert.equal((await getDoc(doc(db,`users/${uid}`))).exists(),false); // Auth alone is not setup
    await ensureShell(db,uid); await ensureShell(db,uid);
    await sendEmailVerification(account.user);
    await reload(account.user);
    assert.equal(account.user.emailVerified,false); // requesting email is not verification
    const id=randomUUID(); await assertFails(setDoc(operation(db,uid,id),data(uid,id)));
    await signOut(auth);
    const signed=await signInWithEmailAndPassword(auth,email,password);
    assert.equal(signed.user.uid,uid);
    await ensureShell(db,uid);
  } finally { await deleteApp(app); }
});
test('F01 explicit synthetic Google linking retains UID after password reauthentication', async () => {
  const app=initializeApp({projectId,apiKey:'emulator-only',appId:'emulator-only'},randomUUID());
  const auth=getAuth(app); connectAuthEmulator(auth,'http://127.0.0.1:9199',{disableWarnings:true});
  const email=`link-${randomUUID()}@example.test`, password='synthetic-password-123';
  try {
    const account=await createUserWithEmailAndPassword(auth,email,password), uid=account.user.uid;
    await reauthenticateWithCredential(account.user,EmailAuthProvider.credential(email,password));
    // Auth emulator accepts synthetic JSON ID-token fixtures, not genuine Google OAuth proof.
    const credential=GoogleAuthProvider.credential(JSON.stringify({sub:randomUUID(),email,email_verified:true}));
    const linked=await linkWithCredential(account.user,credential);
    assert.equal(linked.user.uid,uid);
    assert.ok(linked.user.providerData.some(p=>p.providerId==='google.com'));
  } finally { await deleteApp(app); }
});

test('F01 Google credential owned by another UID refuses linking and leaves current UID intact', async () => {
  const app=initializeApp({projectId,apiKey:'emulator-only',appId:'emulator-only'},randomUUID());
  const auth=getAuth(app); connectAuthEmulator(auth,'http://127.0.0.1:9199',{disableWarnings:true});
  const googleEmail=`owned-${randomUUID()}@example.test`;
  const credential=GoogleAuthProvider.credential(JSON.stringify({sub:randomUUID(),email:googleEmail,email_verified:true}));
  try {
    const first=await signInWithCredential(auth,credential), uidA=first.user.uid;
    await signOut(auth);
    const email=`current-${randomUUID()}@example.test`, password='synthetic-password-123';
    const second=await createUserWithEmailAndPassword(auth,email,password), uidB=second.user.uid;
    assert.notEqual(uidA,uidB);
    await reauthenticateWithCredential(second.user,EmailAuthProvider.credential(email,password));
    await assert.rejects(linkWithCredential(second.user,credential), error=> /credential-already-in-use|email-already-in-use|account-exists-with-different-credential/.test(error.code));
    assert.equal(auth.currentUser.uid,uidB);
    assert.equal(auth.currentUser.providerData.some(p=>p.providerId==='google.com'),false);
  } finally { await deleteApp(app); }
});
