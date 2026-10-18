import {createHash} from 'node:crypto';
export const LIMIT=1_000_000_000, RESERVATION=8*1024*1024;
export const CANDIDATE=5*1024*1024, DISPLAY=2.5*1024*1024, THUMBNAIL=.5*1024*1024;
export const PURPOSES=['post_image','pet_photo','profile_avatar','google_avatar'];
export const uuid=x=>typeof x==='string'&&/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(x);
export const fail=code=>{throw new Error(code);};
export const ensure=(test,code='invalid')=>{if(!test)fail(code);};
export function canonical(x){if(x===null||typeof x!=='object')return JSON.stringify(x);if(Array.isArray(x))return '['+x.map(canonical).join(',')+']';return '{'+Object.keys(x).sort().map(k=>JSON.stringify(k)+':'+canonical(x[k])).join(',')+'}';}
export const digest=x=>createHash('sha256').update(Buffer.isBuffer(x)?x:canonical(x)).digest('hex');
export function exact(x,keys){ensure(x&&typeof x==='object'&&!Array.isArray(x)&&Object.keys(x).sort().join('|')===[...keys].sort().join('|'));return x;}
export function selection(ids){ensure(Array.isArray(ids)&&ids.length<=100&&ids.every(uuid)&&new Set(ids).size===ids.length);return ids;}
export function reserveCount(current){ensure(Number.isSafeInteger(current)&&current>=0&&current%RESERVATION===0,'budget_corrupt');ensure(current+RESERVATION<=LIMIT,'capacity_full');return current+RESERVATION;}
export function googleImageUrl(value){let u;try{u=new URL(value);}catch{fail('provider_photo_unavailable');}ensure(u.protocol==='https:'&&!u.username&&!u.password&&!u.port&&/^lh[0-9]+\.googleusercontent\.com$/.test(u.hostname),'provider_photo_unavailable');return u;}
export function bodyText(value){ensure(typeof value==='string'&&value.trim()===value&&value.length>0&&[...value].length<=2000&&Buffer.byteLength(value)<=8000,'invalid_text');return value;}
export function choice(value){ensure(Number.isSafeInteger(value)&&value>=0);return value;}
