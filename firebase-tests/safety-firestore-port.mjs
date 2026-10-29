import {doc,runTransaction,getDocs,collection,query,where,orderBy,limit,startAfter,documentId} from 'firebase/firestore';
// Test-only adapter in rules-disabled context; not a deployed authenticated endpoint.
export const port=db=>({transaction:work=>runTransaction(db,tx=>work({get:async path=>{const d=await tx.get(doc(db,path));return d.exists()?d.data():undefined;},set:(path,data)=>tx.set(doc(db,path),data),delete:path=>tx.delete(doc(db,path))}),{maxAttempts:3})});
