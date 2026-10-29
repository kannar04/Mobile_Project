import test from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {Store} from './adoption-flow.test.mjs';
import {WelfareReports} from '../welfare-reports.mjs';

function fixture(){
 const store=new Store(),uid='member',assetId=randomUUID();
 store.docs.set('users/'+uid,{uid,schemaVersion:1,createdAt:new Date()});
 store.docs.set('mediaAssets/'+assetId,{ownerUid:uid,assetId,purpose:'adoption_private',state:'ready',targetId:null,version:1});
 return {store,uid,assetId,service:new WelfareReports(store)};
}

test('demo welfare report requires real private evidence and enters the admin report queue',async()=>{
 const f=fixture(),reportId=randomUUID(),operationId=randomUUID();
 const result=await f.service.execute({uid:f.uid},{command:'submit',operationId,expectedVersion:0,reportId,category:'abandonment',statement:'Con vật cần được kiểm tra và hỗ trợ.',assetIds:[f.assetId]});
 assert.deepEqual(result,{reportId,status:'received',demoOnly:true});
 assert.equal(f.store.docs.get('reports/'+reportId).status,'received');
 assert.equal(f.store.docs.get('mediaAssets/'+f.assetId).targetId,'report:'+reportId);
 assert.deepEqual(f.store.docs.get(`reports/${reportId}/restrictedEvidence/current`).assetIds,[f.assetId]);
});

test('demo welfare report rejects missing or foreign evidence',async()=>{
 const f=fixture();
 await assert.rejects(f.service.execute({uid:f.uid},{command:'submit',operationId:randomUUID(),expectedVersion:0,reportId:randomUUID(),category:'abuse',statement:'Mô tả đủ dài cho báo cáo minh họa.',assetIds:[]}),/invalid_asset/);
 f.store.docs.get('mediaAssets/'+f.assetId).ownerUid='another';
 await assert.rejects(f.service.execute({uid:f.uid},{command:'submit',operationId:randomUUID(),expectedVersion:0,reportId:randomUUID(),category:'abuse',statement:'Mô tả đủ dài cho báo cáo minh họa.',assetIds:[f.assetId]}),/invalid_asset/);
});
