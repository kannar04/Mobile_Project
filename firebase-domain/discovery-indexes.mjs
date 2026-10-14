export const filterFields=['speciesId','breedId','regionId','ageBand','sex','offerType'];
export function discoveryIndexes(){
 const indexes=[];
 for(let mask=0;mask<64;mask++){
  if(mask&2&&!(mask&1))continue;
  for(const prefix of [false,true]){
   const fields=['audience','state','availability',...filterFields.filter((_,i)=>mask&(1<<i))].map(fieldPath=>({fieldPath,order:'ASCENDING'}));
   fields.push({fieldPath:prefix?'nameKey':'firstPublishedAt',order:prefix?'ASCENDING':'DESCENDING'},{fieldPath:'__name__',order:prefix?'ASCENDING':'DESCENDING'});
   indexes.push({collectionGroup:'listingDiscovery',queryScope:'COLLECTION',fields});
  }
 }
 return indexes;
}
