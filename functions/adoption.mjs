import {ensure,uuid} from './contract.mjs';

/** Same current-source fence as F12 discovery; never writes protected adoption data. */
export async function adoptionSource(read,uid,input){
 ensure(uuid(input.listingId)&&Number.isSafeInteger(input.listingContentVersion)&&input.listingContentVersion>0&&Number.isSafeInteger(input.listingPublicationVersion)&&input.listingPublicationVersion>0,'invalid_listing');
 const id=input.listingId,d=await read(`listingDiscovery/${id}`),policy=await read('listingDiscoveryPolicy/current');
 ensure(policy?.schemaVersion===1&&policy.enabled===true&&policy.writerSetVersion===1&&d?.schemaVersion===1&&d.listingId===id&&d.ownerUid===uid&&d.offerType==='adoption'&&d.audience==='signed_in'&&d.state==='visible'&&['available','reserved'].includes(d.availability)&&uuid(d.petId),'listing_unavailable');
 ensure(d.contentVersion===input.listingContentVersion&&d.publicationVersion===input.listingPublicationVersion,'listing_changed');
 const [l,p,pet,a,g,s]=await Promise.all([`listings/${id}`,`listingPublication/${id}`,`pets/${d.petId}`,`petOfferAuthorities/${d.petId}`,`petActivityGates/${d.petId}`,`petListingSlots/${d.petId}`].map(read));
 ensure(l?.ownerUid===uid&&l.petId===d.petId&&l.contentVersion===d.contentVersion&&l.lifecycle==='open'&&l.visibility==='visible'&&p?.schemaVersion===1&&p.state==='eligible'&&p.approvedContentVersion===d.contentVersion&&p.publicationVersion===d.publicationVersion,'listing_unavailable');
 ensure(pet?.schemaVersion===1&&pet.petId===d.petId&&pet.lifecycle==='active'&&pet.editorUid===uid&&a?.schemaVersion===1&&a.petId===d.petId&&a.status==='enabled'&&a.hold==='clear'&&a.offerUid===uid&&a.identityVersion===pet.identityVersion&&a.custodyVersion===pet.custodyVersion&&a.decisionId!=null,'listing_unavailable');
 ensure(g?.schemaVersion===1&&g.petId===d.petId&&g.custodyVersion===pet.custodyVersion&&g.activePlacementId===null&&(d.availability==='available'?g.state==='none'&&g.activeReservationId===null:g.state==='reserved'&&g.activeReservationId!=null)&&s?.schemaVersion===1&&s.petId===d.petId&&s.activeListingId===id,'listing_unavailable');
 return {listingId:id,listingContentVersion:d.contentVersion,listingPublicationVersion:d.publicationVersion};
}
