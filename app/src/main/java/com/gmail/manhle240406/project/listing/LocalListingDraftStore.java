package com.gmail.manhle240406.project.listing;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import com.gmail.manhle240406.project.foundation.*;
import java.util.*;
import org.json.*;
import static com.gmail.manhle240406.project.pet.PetSchema.*;

/** App-private SQLite is ordinary storage, not encryption. Every access is epoch/UID guarded. */
public final class LocalListingDraftStore extends SQLiteOpenHelper {
    public static final String DATABASE="listing_drafts.db";
    private final SessionGateway sessions;
    private String sessionUid;
    private final Runnable invalidation=this::invalidate;
    private volatile boolean cleanupFailed;
    private final java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
    public java.util.concurrent.ExecutorService executor(){return executor;}
    public static final class Draft {
        public String uid,id,listingId,petId,pendingJson,remoteOperation;
        public long contentVersion,controlVersion,localVersion,serverVersion,modifiedAt,identityVersion;
        public Map<String,Object> fields;
        public Draft(String uid){this.uid=uid;id=UUID.randomUUID().toString();listingId=UUID.randomUUID().toString();fields=ListingSchema.empty();}
    }
    public LocalListingDraftStore(Context c,SessionGateway s){super(c,DATABASE,null,1);sessions=s;sessionUid=s==null?null:s.capture().uid;if(s!=null)s.onInvalidated(invalidation);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE listing_drafts(account_uid TEXT NOT NULL,local_draft_id TEXT NOT NULL,intended_listing_id TEXT NOT NULL,pet_id TEXT,schema_version INTEGER NOT NULL,base_listing_version INTEGER NOT NULL,base_control_version INTEGER NOT NULL,base_identity_version INTEGER NOT NULL,draft_version INTEGER NOT NULL,payload_json TEXT NOT NULL,modified_at INTEGER NOT NULL,server_synced_version INTEGER NOT NULL,last_operation_id TEXT,pending_command TEXT,PRIMARY KEY(account_uid,local_draft_id))");}
    @Override public void onUpgrade(SQLiteDatabase db,int old,int next){throw new IllegalStateException("unknown_schema");}
    private void invalidate(){String previous=sessionUid;sessionUid=sessions.capture().uid;if(previous!=null)executor.execute(()->{synchronized(this){try{getWritableDatabase().delete("listing_drafts",null,null);cleanupFailed=false;}catch(SQLiteException e){cleanupFailed=true;}}});}
    public void clearOnSignedOutStartup(){if(sessionUid==null)executor.execute(()->{synchronized(this){try{getWritableDatabase().delete("listing_drafts",null,null);cleanupFailed=false;}catch(SQLiteException e){cleanupFailed=true;}}});}
    public java.util.concurrent.CompletionStage<Void> clearAccountAfterSignOut(String uid) {
        return java.util.concurrent.CompletableFuture.runAsync(()->{synchronized(this){
            if(uid==null || uid.isEmpty())throw new IllegalArgumentException("cleanup_uid");
            getWritableDatabase().delete("listing_drafts","account_uid=?",new String[]{uid});
            if(cleanupFailed)throw new IllegalStateException("local_cleanup_required");
        }},executor);
    }
    private void valid(SessionEpoch.Ticket t){if(t==null||t.uid==null||sessions==null||!sessions.current(t))throw new IllegalStateException("session_changed");if(cleanupFailed)throw new IllegalStateException("cleanup_failed");}
    @Override public synchronized void close(){if(sessions!=null)sessions.removeInvalidation(invalidation);executor.shutdown();super.close();}
    public synchronized void save(SessionEpoch.Ticket t,Draft d){valid(t);if(!t.uid.equals(d.uid)||!id(d.id)||!id(d.listingId)||(d.petId!=null&&!id(d.petId))||!ListingSchema.fields(d.fields,false))throw new IllegalArgumentException("invalid_input");
        String json=new JSONObject(d.fields).toString();if(ListingSchema.bytes(json)>16384||(d.pendingJson!=null&&ListingSchema.bytes(d.pendingJson)>32768))throw new IllegalArgumentException("invalid_input");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{boolean exists;long previousVersion=0;try(Cursor row=db.rawQuery("SELECT draft_version FROM listing_drafts WHERE account_uid=? AND local_draft_id=?",new String[]{t.uid,d.id})){exists=row.moveToFirst();if(exists)previousVersion=row.getLong(0);}if(!exists){try(Cursor count=db.rawQuery("SELECT COUNT(*) FROM listing_drafts WHERE account_uid=?",new String[]{t.uid})){count.moveToFirst();if(count.getLong(0)>=100)throw new IllegalStateException("capacity");}}
            ContentValues v=new ContentValues();v.put("account_uid",d.uid);v.put("local_draft_id",d.id);v.put("intended_listing_id",d.listingId);v.put("pet_id",d.petId);v.put("schema_version",1);v.put("base_listing_version",d.contentVersion);v.put("base_control_version",d.controlVersion);v.put("base_identity_version",d.identityVersion);long nextVersion=Math.max(previousVersion,d.localVersion)+1;v.put("draft_version",nextVersion);v.put("payload_json",json);v.put("modified_at",System.currentTimeMillis());v.put("server_synced_version",d.serverVersion);v.put("last_operation_id",d.remoteOperation);v.put("pending_command",d.pendingJson);valid(t);if(db.insertWithOnConflict("listing_drafts",null,v,SQLiteDatabase.CONFLICT_REPLACE)<0)throw new SQLiteException();db.setTransactionSuccessful();d.localVersion=nextVersion;}finally{db.endTransaction();}}
    public synchronized List<Draft> list(SessionEpoch.Ticket t,int offset){valid(t);List<Draft> rows=new ArrayList<>();try(Cursor c=getReadableDatabase().query("listing_drafts",null,"account_uid=?",new String[]{t.uid},null,null,"modified_at DESC,local_draft_id DESC","20 OFFSET "+Math.max(0,offset))){while(c.moveToNext()){Draft d=new Draft(t.uid);d.id=c.getString(c.getColumnIndexOrThrow("local_draft_id"));d.listingId=c.getString(c.getColumnIndexOrThrow("intended_listing_id"));d.petId=c.getString(c.getColumnIndexOrThrow("pet_id"));d.contentVersion=c.getLong(c.getColumnIndexOrThrow("base_listing_version"));d.controlVersion=c.getLong(c.getColumnIndexOrThrow("base_control_version"));d.identityVersion=c.getLong(c.getColumnIndexOrThrow("base_identity_version"));d.localVersion=c.getLong(c.getColumnIndexOrThrow("draft_version"));d.serverVersion=c.getLong(c.getColumnIndexOrThrow("server_synced_version"));d.modifiedAt=c.getLong(c.getColumnIndexOrThrow("modified_at"));d.remoteOperation=c.getString(c.getColumnIndexOrThrow("last_operation_id"));d.pendingJson=c.getString(c.getColumnIndexOrThrow("pending_command"));d.fields=decode(c.getString(c.getColumnIndexOrThrow("payload_json")));if(c.getInt(c.getColumnIndexOrThrow("schema_version"))!=1||!ListingSchema.fields(d.fields,false)||!id(d.id)||!id(d.listingId))throw new IllegalStateException("unknown_schema");rows.add(d);}}valid(t);return rows;}
    public synchronized void delete(SessionEpoch.Ticket t,String id){valid(t);getWritableDatabase().delete("listing_drafts","account_uid=? AND local_draft_id=?",new String[]{t.uid,id});}
    public static Map<String,Object> decode(String json){try{return object(new JSONObject(json));}catch(JSONException e){throw new IllegalArgumentException("unknown_schema",e);}}
    private static Map<String,Object> object(JSONObject j)throws JSONException{Map<String,Object> m=new HashMap<>();Iterator<String> keys=j.keys();while(keys.hasNext()){String k=keys.next();m.put(k,value(j.get(k)));}return m;}
    private static Object value(Object v)throws JSONException{if(v==JSONObject.NULL)return null;if(v instanceof JSONObject)return object((JSONObject)v);if(v instanceof JSONArray){List<Object> a=new ArrayList<>();for(int i=0;i<((JSONArray)v).length();i++)a.add(value(((JSONArray)v).get(i)));return a;}return v instanceof Integer?((Integer)v).longValue():v;}
}
