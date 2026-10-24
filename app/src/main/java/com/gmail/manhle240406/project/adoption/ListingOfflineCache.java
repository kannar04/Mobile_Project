package com.gmail.manhle240406.project.adoption;

import android.content.Context;
import android.content.SharedPreferences;
import com.gmail.manhle240406.project.extensions.EncryptedOfflineStore;
import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import java.util.*;

/** Account-scoped, bounded cache of already validated managed listing projections. */
final class ListingOfflineCache {
 private static EncryptedOfflineStore store;private static SessionEpoch.Ticket storeTicket;private static SessionGateway gateway;private static Runnable rotate;
 private static final long LEASE=24L*60*60*1000;
 private ListingOfflineCache(){}
 private static synchronized EncryptedOfflineStore store(SessionGateway sessions,SessionEpoch.Ticket ticket){
  if(store==null||storeTicket==null||!sessions.current(storeTicket)){
   Context app=FirebaseApp.getInstance().getApplicationContext();SharedPreferences prefs=app.getSharedPreferences("listing_offline_namespace",Context.MODE_PRIVATE);String key="nonce_"+AdoptionValues.digest(ticket.uid).substring(0,16);
   if(gateway!=null&&rotate!=null)gateway.removeInvalidation(rotate);gateway=sessions;rotate=()->prefs.edit().remove(key).apply();sessions.onInvalidated(rotate);
   String nonce=prefs.getString(key,null);if(nonce==null){nonce=UUID.randomUUID().toString();prefs.edit().putString(key,nonce).apply();}
   EncryptedOfflineStore.Consumer cards=new EncryptedOfflineStore.Consumer(){public int schemaVersion(){return 1;}public long leaseMillis(){return LEASE;}public Map<String,Object> validate(Map<String,Object> payload,boolean draft){if(draft)throw new IllegalArgumentException("listing_cache_read_only");ListingRow row=new ListingRow(payload);return serializable(row.getData());}};
   store=new EncryptedOfflineStore(app,sessions,ticket,nonce,Map.of("listing_card",cards,"listing_detail",cards));storeTicket=ticket;
 }return store;
 }
 @SuppressWarnings("unchecked") private static Map<String,Object> serializable(Map<String,Object> source){Map<String,Object> out=new HashMap<>();for(Map.Entry<String,Object> entry:source.entrySet()){Object value=entry.getValue();if(value instanceof com.google.firebase.Timestamp){com.google.firebase.Timestamp stamp=(com.google.firebase.Timestamp)value;value=AdoptionFlowModel.map("_seconds",stamp.getSeconds(),"_nanoseconds",(long)stamp.getNanoseconds());}else if(value instanceof Map)value=serializable((Map<String,Object>)value);else if(value instanceof List){List<Object> items=new ArrayList<>();for(Object item:(List<?>)value)items.add(item instanceof Map?serializable((Map<String,Object>)item):item);value=items;}out.put(entry.getKey(),value);}return out;}
 static String queryKey(String operation,Map<String,Object> input){return AdoptionValues.digest("managed-listing-v1|"+operation+"|"+AdoptionValues.canonical(input));}
 static Task<Void> savePage(SessionGateway sessions,SessionEpoch.Ticket ticket,String queryKey,Map<String,Object> value){
  EncryptedOfflineStore local=store(sessions,ticket);List<Map<String,Object>> rows=AdoptionFlowModel.rows(value.get("rows"));List<String> ids=new ArrayList<>();List<Task<Void>> writes=new ArrayList<>();long now=System.currentTimeMillis();
  for(Map<String,Object> raw:rows){ListingRow row=new ListingRow(raw);ids.add(row.getId());writes.add(local.putValidated("listing_card",row.getId(),row.getLong("contentVersion"),row.getLong("publicationVersion"),now,row.getData()));}
  return Tasks.whenAll(writes).continueWithTask(done->{if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return local.saveQuery(queryKey,Math.max(1,now),"listing_card",ids,null,0);});
 }
 static Task<Map<String,Object>> page(SessionGateway sessions,SessionEpoch.Ticket ticket,String queryKey){return store(sessions,ticket).savedQuery(queryKey).continueWith(done->{if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());List<Map<String,Object>> rows=new ArrayList<>();for(EncryptedOfflineStore.Row row:done.getResult())rows.add(row.payload);return AdoptionFlowModel.map("rows",rows,"cursor",null,"cached",true);});}
 static Task<Void> saveDetail(SessionGateway sessions,SessionEpoch.Ticket ticket,Map<String,Object> raw){ListingRow row=new ListingRow(raw);return store(sessions,ticket).putValidated("listing_detail",row.getId(),row.getLong("contentVersion"),row.getLong("publicationVersion"),System.currentTimeMillis(),row.getData());}
 static Task<Map<String,Object>> detail(SessionGateway sessions,SessionEpoch.Ticket ticket,String id){return store(sessions,ticket).read("listing_detail",id).continueWith(done->{if(!done.isSuccessful())throw Objects.requireNonNull(done.getException());return done.getResult()==null?Collections.emptyMap():done.getResult().payload;});}
}
