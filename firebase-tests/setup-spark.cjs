// One-time safe configuration bootstrap. No users, catalogs, roles or rollout activation.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const project = 'aniloveproject';
const planning = 'C:/Users/Minht/Desktop/New folder';
const root = `projects/${project}/databases/(default)/documents`;
const specs = {
  'system/config': {schemaVersion: 1, environmentLabel: 'ANILOVE Spark course demo', featureMode: 'course_demo'},
  'system/communityPolicy': {schemaVersion: 1, policyVersion: 1, enabled: false, publicationMode: 'post_moderated'},
  'system/reportPolicy': {schemaVersion: 1, policyVersion: 1, intakeEnabled: false, supportedTargetKinds: ['post', 'comment']}
};
function encode(value) {
  if (typeof value === 'boolean') return {booleanValue: value};
  if (Number.isInteger(value)) return {integerValue: String(value)};
  if (typeof value === 'string') return {stringValue: value};
  if (Array.isArray(value)) return {arrayValue: {values: value.map(encode)}};
  throw Error('unsupported_value');
}
function matches(document, spec) {
  const {updatedAt, ...fields} = document.fields ?? {};
  return typeof updatedAt?.timestampValue === 'string'
    && require('node:util').isDeepStrictEqual(fields, Object.fromEntries(Object.entries(spec).map(([k,v]) => [k,encode(v)])));
}
async function bootstrap(client, apply) {
  const writes = [], result = {created: [], preserved: [], pending: []};
  for (const [id, spec] of Object.entries(specs)) {
    let existing;
    try { existing = (await client.get(`${root}/${id}`, {retries: 0})).body; }
    catch (error) { if ((error.status ?? error.context?.response?.statusCode) !== 404) throw error; }
    if (existing) {
      if (!matches(existing, spec)) throw Error(`existing_config_conflict:${id}`);
      result.preserved.push(id);
    } else {
      result.pending.push(id);
      writes.push({update: {name: `${root}/${id}`, fields: Object.fromEntries(Object.entries(spec).map(([k,v]) => [k,encode(v)]))},
        updateTransforms: [{fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME'}], currentDocument: {exists: false}});
    }
  }
  if (apply && writes.length) {
    await client.post(`${root}:commit`, {writes}, {retries: 0});
    result.created = result.pending; result.pending = [];
  }
  return result;
}
async function selfTest() {
  const docs = new Map(); let commits = 0;
  const fake = {get: async id => {if (!docs.has(id)) throw {status: 404}; return {body: docs.get(id)};},
    post: async (_, {writes}) => {commits++; assert.equal(writes.length, 3); for (const w of writes) {
      assert.equal(w.currentDocument.exists, false);
      assert.equal(w.updateTransforms[0].setToServerValue, 'REQUEST_TIME');
      docs.set(w.update.name, {fields: {...w.update.fields, updatedAt: {timestampValue: '2026-10-17T00:00:00Z'}}});
    }}};
  assert.equal((await bootstrap(fake, false)).pending.length, 3); assert.equal(commits, 0);
  assert.equal((await bootstrap(fake, true)).created.length, 3);
  assert.equal((await bootstrap(fake, true)).preserved.length, 3); assert.equal(commits, 1);
  docs.get(`${root}/system/communityPolicy`).fields.enabled = {booleanValue: true};
  await assert.rejects(bootstrap(fake, true), /existing_config_conflict/); assert.equal(commits, 1);
  await assert.rejects(bootstrap({get: async()=> {throw {status: 403};}}, true));
  console.log('PASS: dry run, atomic create-only writes, server timestamps, idempotency, conflict preservation, permission failures');
}
async function main() {
  if (process.argv.includes('--self-test')) return selfTest();
  const approved = JSON.parse(fs.readFileSync(path.join(planning, 'implementation/evidence/SPARK_WIRING.r1/accepted-inputs.json')));
  for (const id of ['F11', 'F29', 'F31', 'F42']) {
    const input = approved[id], receipt = JSON.parse(fs.readFileSync(path.join(planning, input.receipt)));
    const hash = crypto.createHash('sha256').update(fs.readFileSync(path.join(planning, input.analysis_path))).digest('hex');
    if (hash !== input.sha256 || hash !== receipt.approved_artifact_sha256) throw Error(`approval_mismatch:${id}`);
  }
  const {getGlobalDefaultAccount} = require('firebase-tools/lib/auth');
  const {requireAuth} = require('firebase-tools/lib/requireAuth');
  const {Client} = require('firebase-tools/lib/apiv2');
  const account = getGlobalDefaultAccount(); if (!account) throw Error('login_required');
  await requireAuth({...account, project, nonInteractive: true});
  const billing = new Client({urlPrefix: 'https://cloudbilling.googleapis.com', apiVersion: 'v1'});
  const checkBilling = async () => assert.equal((await billing.get(`projects/${project}/billingInfo`, {retries: 0})).body.billingEnabled, false);
  await checkBilling();
  const client = new Client({urlPrefix: 'https://firestore.googleapis.com', apiVersion: 'v1'});
  const apply = process.argv.includes('--apply');
  const result = await bootstrap(client, apply);
  if (apply) for (const [id, spec] of Object.entries(specs)) assert.ok(matches((await client.get(`${root}/${id}`, {retries: 0})).body, spec));
  await checkBilling();
  const report = {project, apply, ...result, billingEnabled: false, verified: apply, catalogSeed: 'pending_owner_approved_fixtures', rolloutEnabled: false, uploadsEnabled: false};
  fs.mkdirSync(path.join(__dirname, '../build'), {recursive: true});
  fs.writeFileSync(path.join(__dirname, '../build/spark-setup.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
}
if (require.main === module) main().catch(error => {console.error(`Setup stopped: ${error.message ?? 'provider_request_failed'}`); process.exitCode = 1;});
module.exports = {bootstrap, matches, specs};
