import {participationPolicy} from './domain/participation.mjs';
/** Read through the caller's Firestore transaction; never a process-wide policy cache. */
export async function courseParticipation(tx,db){return !!await participationPolicy({get:async path=>(await tx.get(db.doc(path))).data()??null});}
