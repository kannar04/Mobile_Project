// Read-only local inventory. This is fixture/admin inspection, never a user's permission proof.
import assert from 'node:assert/strict';
import {initializeTestEnvironment} from '@firebase/rules-unit-testing';
import {collection,collectionGroup,getDocs,query,limit} from 'firebase/firestore';
assert.match(process.env.FIRESTORE_EMULATOR_HOST??'',/^(127\.0\.0\.1|localhost):8180$/,'Only the isolated demo emulator is authorized');
const env=await initializeTestEnvironment({projectId:'demo-anilove',firestore:{host:'127.0.0.1',port:8180}});
try {
 const result=await env.withSecurityRulesDisabled(async context=>{
  const db=context.firestore(),posts=await getDocs(query(collection(db,'communityPosts'),limit(501))),feed=await getDocs(query(collection(db,'communityFeed'),limit(501))),likes=await getDocs(query(collectionGroup(db,'likes'),limit(501)));
  return {environment:'demo-anilove local emulator only',mode:'read_only',scanLimit:501,incomplete:posts.size===501||feed.size===501||likes.size===501,
   privateSchema1:posts.docs.filter(d=>d.get('schemaVersion')===1&&d.get('visibility')==='hidden').length,
   schema1PreviouslyPublished:posts.docs.filter(d=>d.get('schemaVersion')===1&&d.get('firstPublishedAt')!=null).length,
   schema2:posts.docs.filter(d=>d.get('schemaVersion')===2).length,
   unsupportedRoots:posts.docs.filter(d=>![1,2].includes(d.get('schemaVersion'))).length,
   oldFeedProjections:feed.docs.filter(d=>d.get('schemaVersion')!==2).length,
   oldVersionThreadLikes:likes.docs.filter(d=>d.ref.path.includes('/threads/')).length,
   migrationExecuted:false,cloudDataInspected:false};
 });
 process.stdout.write(JSON.stringify(result,null,2)+'\n');
} finally {await env.cleanup();}
