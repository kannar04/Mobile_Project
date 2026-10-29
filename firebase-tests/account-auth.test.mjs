import {test} from 'node:test';
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
const host='http://127.0.0.1:9299';
const api=async(method,body)=>{
 const r=await fetch(`${host}/identitytoolkit.googleapis.com/v1/accounts:${method}?key=local-emulator-only`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
 const data=await r.json();return {ok:r.ok,data};
};
test('Auth emulator: request alone leaves credential unchanged; applied reset requires explicit same-UID sign-in',async()=>{
 const email=`f02-${randomUUID()}@example.test`,old='Fixture-Old-Password-123',replacement='Fixture-New-Password-456';
 const created=await api('signUp',{email,password:old,returnSecureToken:true});assert.equal(created.ok,true);
 assert.equal((await api('sendOobCode',{requestType:'PASSWORD_RESET',email})).ok,true);
 const before=await api('signInWithPassword',{email,password:old,returnSecureToken:true});assert.equal(before.ok,true);
 const mail=await fetch(`${host}/emulator/v1/projects/demo-anilove-account-tests/oobCodes`);
 const messages=await mail.json();const action=messages.oobCodes.find(x=>x.email===email && x.requestType==='PASSWORD_RESET');assert.ok(action);
 assert.equal((await api('resetPassword',{oobCode:action.oobCode,newPassword:replacement})).ok,true);
 assert.equal((await api('signInWithPassword',{email,password:old,returnSecureToken:true})).ok,false);
 const signed=await api('signInWithPassword',{email,password:replacement,returnSecureToken:true});assert.equal(signed.ok,true);
 assert.equal(signed.data.localId,created.data.localId);
 assert.equal((await api('resetPassword',{oobCode:action.oobCode,newPassword:old})).ok,false);
 // Remove only this synthetic emulator test identity; no live project/account operation.
 assert.equal((await api('delete',{idToken:signed.data.idToken})).ok,true);
});
test('Auth emulator rejects invalid action code without app-owned token storage',async()=>{
 assert.equal((await api('resetPassword',{oobCode:'invalid-fixture-code',newPassword:'Fixture-New-Password-456'})).ok,false);
});
