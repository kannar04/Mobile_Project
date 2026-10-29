// Read-only check through the actual unprivileged client SDK.
import {readFileSync, writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import {initializeApp, deleteApp} from 'firebase/app';
import {getFirestore, doc, getDocFromServer, collection, getDocs, terminate} from 'firebase/firestore';
const config = JSON.parse(readFileSync(new URL('../app/google-services.json', import.meta.url)));
assert.equal(config.project_info.project_id, 'aniloveproject');
const client = config.client.find(c => c.client_info.android_client_info.package_name === 'com.gmail.manhle240406.project');
const app = initializeApp({projectId: 'aniloveproject', apiKey: client.api_key[0].current_key, appId: client.client_info.mobilesdk_app_id}, 'config-read-check');
const db = getFirestore(app);
const passed = [];
try {
  const result = (await getDocFromServer(doc(db, 'system/config'))).data();
  assert.equal(result.featureMode, 'course_demo');
  assert.equal(result.schemaVersion, 1);
  assert.equal(Object.keys(result).length, 4);
  passed.push('public_safe_config_server_read');
  for (const operation of [() => getDocs(collection(db, 'system')), () => getDocFromServer(doc(db, 'system/communityPolicy')), () => getDocFromServer(doc(db, 'system/reportPolicy'))]) {
    await assert.rejects(operation, e => e.code === 'permission-denied');
  }
  passed.push('anonymous_system_list_denied', 'anonymous_community_policy_denied', 'anonymous_report_policy_denied');
  writeFileSync(new URL('../build/spark-config-client-check.json', import.meta.url), JSON.stringify({project: 'aniloveproject', readOnly: true, passed}, null, 2));
  console.log(JSON.stringify({passed}));
} finally {await terminate(db); await deleteApp(app);}
