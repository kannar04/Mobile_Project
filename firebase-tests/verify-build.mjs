import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

// Inspect generated artifacts without dumping any course-project configuration.
const root = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');
const pkg = 'com/gmail/manhle240406/project';
const debugFlags = await read(`app/build/generated/source/buildConfig/debug/${pkg}/BuildConfig.java`);
const releaseFlags = await read(`app/build/generated/source/buildConfig/release/${pkg}/BuildConfig.java`);
assert.match(debugFlags, /FIREBASE_EMULATORS = true/);
assert.match(releaseFlags, /FIREBASE_EMULATORS = false/);
assert.match(releaseFlags, /COURSE_PROJECT_VERIFIED = false/);
const debugConfig = await read('app/build/generated/res/processDebugGoogleServices/values/values.xml');
const releaseConfig = await read('app/build/generated/res/processReleaseGoogleServices/values/values.xml');
assert.match(debugConfig, /demo-anilove/);
assert.doesNotMatch(releaseConfig, /demo-anilove|AIzaSyEmulatorOnly/);
for (const variant of ['debug', 'release']) {
  const stage = variant === 'debug' ? 'processDebugManifest' : 'processReleaseManifest';
  const manifest = await read(`app/build/intermediates/merged_manifests/${variant}/${stage}/AndroidManifest.xml`);
  assert.match(manifest, /AniloveApplication/);
  assert.match(manifest, /android.permission.INTERNET/);
  assert.doesNotMatch(manifest, /FirebaseInitProvider/);
  if (variant === 'release') assert.doesNotMatch(manifest, /usesCleartextTraffic="true"/);
}
assert.match(await read('app/src/main/res/xml/backup_rules.xml'), /exclude domain="sharedpref" path="\."/);
const extraction = await read('app/src/main/res/xml/data_extraction_rules.xml');
assert.equal((extraction.match(/exclude domain="sharedpref" path="\."/g) ?? []).length, 2);
assert.match(await read('app/src/main/res/xml/backup_rules.xml'), /exclude domain="database" path="listing_drafts.db"/);
assert.equal((extraction.match(/exclude domain="database" path="listing_drafts.db"/g) ?? []).length, 2);
console.log('PASS: debug/release environment flags and generated configs are separated; release is fail-closed.');
console.log('PASS: app initialization and Internet permission merged; automatic Firebase provider removed.');
console.log('PASS: release has no debug cleartext opt-in; auth preferences excluded from backup/transfer.');
console.log('PASS: private listing SQLite drafts excluded from cloud backup and device transfer.');
