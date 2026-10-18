package com.gmail.manhle240406.project.extensions;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.security.keystore.*;
import com.google.android.gms.tasks.*;
import com.gmail.manhle240406.project.foundation.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** F41 infrastructure only. No consumer is enabled by default and F07 is not migrated.
 * All SQL/crypto/files execute on one worker. No cloud mutation/outbox replay exists.
 * Databases and encrypted variant files live below noBackupFilesDir. */
public final class EncryptedOfflineStore implements AutoCloseable {
    public interface Clock { long elapsed(); long wall(); long boot(); }
    public interface Consumer {
        int schemaVersion();
        long leaseMillis();
        /** Return a strictly whitelisted normalized representation; reject extra fields. */
        Map<String,Object> validate(Map<String,Object> input, boolean draft);
        default boolean permitsDraft() { return false; }
        default boolean permitsThumbnail() { return false; }
        default void validateThumbnail(String entity,String asset,String variant,long generation,byte[] bytes,String sha) {
            throw new IllegalStateException("media_consumer_witness_required");
        }
    }
    public static final Map<String,Consumer> NO_CONSUMERS = Collections.emptyMap();
    public static final class Row {
        public final Map<String,Object> payload;
        public final long sourceVersion, controlVersion, generation;
        public final String state;
        Row(Map<String,Object> p,long s,long c,long g,String state) {
            payload=Collections.unmodifiableMap(p);sourceVersion=s;controlVersion=c;generation=g;this.state=state;
        }
    }
    private final SessionGateway sessions;
    private final SessionEpoch.Ticket ticket;
    private final Map<String,Consumer> consumers;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean active=new AtomicBoolean(true), viewingEnabled=new AtomicBoolean(true);
    private final AtomicLong cacheGeneration=new AtomicLong();
    private final AtomicBoolean disposed=new AtomicBoolean();
    private Task<Void> disposal;
    private final Clock clock;
    private final File directory, databaseFile;
    private final String namespace, keyAlias;
    private final Helper helper;
    private final Runnable invalidate=this::closeNamespace;
    private SecretKey key;
    private boolean initialized;
    private static final int CACHE_CAP=500, DRAFT_CAP=20, THUMB_CAP=200;
    private static final long CACHE_BYTES=10L*1024*1024, THUMB_BYTES=50L*1024*1024;
    private static final Set<String> DEFAULT_MATRIX_KINDS=new HashSet<>(Arrays.asList(
            "listing_card","listing_detail","community_feed","community_post","catalog","f06_draft","f07_draft","f31_draft"));

    public EncryptedOfflineStore(Context c, SessionGateway s, SessionEpoch.Ticket t,
                                 String sessionNonce, Map<String,Consumer> adapters) {
        this(c,s,t,sessionNonce,adapters,new Clock(){
            public long elapsed(){return SystemClock.elapsedRealtime();}
            public long wall(){return System.currentTimeMillis();}
            public long boot(){return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
        });
    }
    public EncryptedOfflineStore(Context c,SessionGateway s,SessionEpoch.Ticket t,String nonce,
                                 Map<String,Consumer> adapters,Clock clock) {
        if(s==null||t==null||t.uid==null||!s.current(t)||nonce==null||!nonce.matches("[a-zA-Z0-9_-]{16,128}"))
            throw new IllegalArgumentException("online_namespace_required");
        sessions=s;ticket=t;consumers=Collections.unmodifiableMap(new HashMap<>(adapters));this.clock=clock;
        namespace=hash(t.uid+":"+nonce);keyAlias="anilove_offline_"+namespace;
        directory=new File(c.getNoBackupFilesDir(),"offline/"+namespace);
        databaseFile=new File(directory,"cache.db");helper=new Helper(c,databaseFile.getAbsolutePath());
        sessions.onInvalidated(invalidate);
    }
    private static String hash(String s) {
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    private void check(){if(!active.get()||!sessions.current(ticket))throw new IllegalStateException("namespace_invalidated");}
    private Consumer consumer(String kind){boolean fixture=com.gmail.manhle240406.project.BuildConfig.DEBUG&&kind!=null&&kind.startsWith("fixture_");Consumer c=consumers.get(kind);if(!DEFAULT_MATRIX_KINDS.contains(kind)&&!fixture||c==null||c.schemaVersion()<1)throw new IllegalArgumentException("consumer_amendment_required");return c;}
    private static void id(String id){if(id==null||!id.matches("[a-zA-Z0-9_-]{1,128}"))throw new IllegalArgumentException("opaque_id");}
    private void initialize()throws Exception{
        check();if(initialized)return;
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("off_main_required");
        boolean existing=databaseFile.exists();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("local_storage_unavailable");
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(keyAlias))key=(SecretKey)ks.getKey(keyAlias,null);
        else {if(existing)throw new GeneralSecurityException("namespace_key_missing");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(keyAlias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());key=generator.generateKey();}
        helper.getWritableDatabase();initialized=true;check();
    }
    private <T> Task<T> task(Callable<T> fn){return Tasks.call(worker,()->{check();initialize();T result=fn.call();check();return result;});}
    private byte[][] encrypt(byte[] bytes,String aad)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key);c.updateAAD((namespace+":"+aad).getBytes(StandardCharsets.UTF_8));return new byte[][]{c.getIV(),c.doFinal(bytes)};}
    private byte[] decrypt(byte[] nonce,byte[] bytes,String aad)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));c.updateAAD((namespace+":"+aad).getBytes(StandardCharsets.UTF_8));byte[] result=c.doFinal(bytes);check();return result;}
    private String aad(String kind,String id,long source,long control,int schema){return kind+":"+id+":"+source+":"+control+":"+schema;}
    private static byte[] encode(Map<String,Object> payload){return com.gmail.manhle240406.project.adoption.AdoptionValues.canonical(payload).getBytes(StandardCharsets.UTF_8);}
    private static Map<String,Object> decode(byte[] payload)throws Exception{return jsonObject(new JSONObject(new String(payload,StandardCharsets.UTF_8)));}
    private static Map<String,Object> jsonObject(JSONObject j)throws Exception{Map<String,Object> out=new HashMap<>();Iterator<String> keys=j.keys();while(keys.hasNext()){String k=keys.next();out.put(k,jsonValue(j.get(k)));}return out;}
    private static Object jsonValue(Object value)throws Exception{if(value==JSONObject.NULL)return null;if(value instanceof JSONObject)return jsonObject((JSONObject)value);if(value instanceof org.json.JSONArray){List<Object> list=new ArrayList<>();org.json.JSONArray array=(org.json.JSONArray)value;for(int i=0;i<array.length();i++)list.add(jsonValue(array.get(i)));return list;}return value;}
    private static void inserted(long result){if(result<0)throw new SQLiteException("local_save_failed");}
    private long scalar(SQLiteDatabase db,String sql,String... args){try(Cursor c=db.rawQuery(sql,args)){c.moveToFirst();return c.getLong(0);}}
    private void rejectTombstone(SQLiteDatabase db,String kind,String entity,long source,long control){try(Cursor c=db.rawQuery("SELECT source,control FROM invalidations WHERE kind=? AND entity=?",new String[]{kind,entity})){if(c.moveToFirst()&&(source<c.getLong(0)||source==c.getLong(0)&&control<=c.getLong(1)))throw new IllegalStateException("known_target_revoked");}}

    /** Call only after accepted mapper has observed an authoritative SERVER response.
     * There is deliberately no raw DocumentSnapshot or SDK fromCache overload. */
    public Task<Void> putValidated(String kind,String entity,long source,long control,long validatedServerMillis,Map<String,Object> payload){
        final long generation=cacheGeneration.get();final Map<String,Object> frozen=com.gmail.manhle240406.project.listing.ListingSchema.freeze(payload);
        return task(()->{id(entity);Consumer adapter=consumer(kind);if(!viewingEnabled.get()||generation!=cacheGeneration.get())throw new IllegalStateException("view_cache_disabled");
            if(source<1||control<0||validatedServerMillis<=0)throw new IllegalArgumentException("server_versions");
            byte[] bytes=encode(adapter.validate(frozen,false));if(bytes.length>CACHE_BYTES)throw new IllegalArgumentException("cache_capacity");
            long lease=adapter.leaseMillis();if(lease<=0||lease>OfflineLease.MAX_MILLIS||clock.boot()<0)throw new IllegalStateException("offline_lease_unavailable");
            SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
                rejectTombstone(db,kind,entity,source,control);
                try(Cursor c=db.rawQuery("SELECT source,control,schema,nonce,payload FROM cache_entries WHERE kind=? AND entity=?",new String[]{kind,entity})){
                    if(c.moveToFirst()){if(source<c.getLong(0)||control<c.getLong(1))throw new IllegalStateException("stale_cache_response");
                        if(source==c.getLong(0)&&control==c.getLong(1)&&!Arrays.equals(bytes,decrypt(c.getBlob(3),c.getBlob(4),aad(kind,entity,source,control,c.getInt(2)))))throw new IllegalStateException("same_version_payload_conflict");}}
                byte[][] encrypted=encrypt(bytes,aad(kind,entity,source,control,adapter.schemaVersion()));ContentValues row=new ContentValues();row.put("kind",kind);row.put("entity",entity);row.put("source",source);row.put("control",control);row.put("schema",adapter.schemaVersion());row.put("nonce",encrypted[0]);row.put("payload",encrypted[1]);row.put("bytes",encrypted[1].length+encrypted[0].length);row.put("validated",validatedServerMillis);row.put("elapsed",clock.elapsed());row.put("boot",clock.boot());row.put("lease",lease);row.put("viewed",clock.wall());inserted(db.insertWithOnConflict("cache_entries",null,row,SQLiteDatabase.CONFLICT_REPLACE));
                while(scalar(db,"SELECT COUNT(*) FROM cache_entries")>CACHE_CAP||scalar(db,"SELECT COALESCE(SUM(bytes),0) FROM cache_entries")>CACHE_BYTES){db.execSQL("DELETE FROM cache_entries WHERE rowid=(SELECT rowid FROM cache_entries ORDER BY viewed,kind,entity LIMIT 1)");}
                check();if(generation!=cacheGeneration.get())throw new IllegalStateException("stale_cache_generation");db.setTransactionSuccessful();return null;
            }finally{db.endTransaction();}});
    }
    public Task<Row> read(String kind,String entity){final long cg=cacheGeneration.get();return task(()->{Row row=readNow(kind,entity);if(cg!=cacheGeneration.get())throw new IllegalStateException("stale_cache_generation");return row;});}
    private Row readNow(String kind,String entity)throws Exception{
        id(entity);Consumer adapter=consumer(kind);if(!viewingEnabled.get())return null;SQLiteDatabase db=helper.getReadableDatabase();
        try(Cursor c=db.rawQuery("SELECT source,control,schema,nonce,payload,elapsed,boot,lease FROM cache_entries WHERE kind=? AND entity=?",new String[]{kind,entity})){
            if(!c.moveToFirst()||c.getInt(2)!=adapter.schemaVersion()||!OfflineLease.valid(c.getLong(5),clock.elapsed(),c.getLong(6),clock.boot(),c.getLong(7)))return null;
            rejectTombstone(db,kind,entity,c.getLong(0),c.getLong(1));Map<String,Object> value=adapter.validate(decode(decrypt(c.getBlob(3),c.getBlob(4),aad(kind,entity,c.getLong(0),c.getLong(1),c.getInt(2)))),false);
            return new Row(value,c.getLong(0),c.getLong(1),0,"previously_validated_snapshot");}
    }
    public Task<Void> tombstone(String kind,String entity,long source,long control){cacheGeneration.incrementAndGet();return task(()->{id(entity);consumer(kind);if(source<1||control<0)throw new IllegalArgumentException("tombstone_versions");SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
        long s=source,c=control;try(Cursor old=db.rawQuery("SELECT source,control FROM invalidations WHERE kind=? AND entity=?",new String[]{kind,entity})){if(old.moveToFirst()){s=Math.max(s,old.getLong(0));c=Math.max(c,old.getLong(1));}}
        ContentValues row=new ContentValues();row.put("kind",kind);row.put("entity",entity);row.put("source",s);row.put("control",c);inserted(db.insertWithOnConflict("invalidations",null,row,SQLiteDatabase.CONFLICT_REPLACE));db.delete("cache_entries","kind=? AND entity=?",new String[]{kind,entity});db.delete("query_items","kind=? AND entity=?",new String[]{kind,entity});db.delete("media_entries","kind=? AND entity=?",new String[]{kind,entity});db.setTransactionSuccessful();return null;}finally{db.endTransaction();}});}

    public Task<Row> saveDraft(String feature,String draftId,long expectedGeneration,long baseVersion,Map<String,Object> payload){final Map<String,Object> frozen=com.gmail.manhle240406.project.listing.ListingSchema.freeze(payload);return task(()->{id(draftId);Consumer adapter=consumer(feature);if(!adapter.permitsDraft())throw new IllegalArgumentException("draft_amendment_required");byte[] plain=encode(adapter.validate(frozen,true));if(plain.length>65536||expectedGeneration<0||baseVersion<0)throw new IllegalArgumentException("draft_bounds");SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{long old=scalar(db,"SELECT COALESCE(MAX(generation),0) FROM draft_entries WHERE feature=? AND entity=?",feature,draftId);if(old!=expectedGeneration)throw new IllegalStateException("draft_generation_conflict");if(old==0&&scalar(db,"SELECT COUNT(*) FROM draft_entries")>=DRAFT_CAP)throw new IllegalStateException("draft_capacity");long next=old+1;byte[][] encrypted=encrypt(plain,aad(feature,draftId,next,baseVersion,adapter.schemaVersion()));ContentValues row=new ContentValues();row.put("feature",feature);row.put("entity",draftId);row.put("generation",next);row.put("base",baseVersion);row.put("schema",adapter.schemaVersion());row.put("nonce",encrypted[0]);row.put("payload",encrypted[1]);row.put("updated",clock.wall());row.put("state","local_only");inserted(db.insertWithOnConflict("draft_entries",null,row,SQLiteDatabase.CONFLICT_REPLACE));check();db.setTransactionSuccessful();return new Row(adapter.validate(frozen,true),baseVersion,0,next,"local_only");}finally{db.endTransaction();}});}
    public Task<Row> readDraft(String feature,String draftId){return task(()->{id(draftId);Consumer adapter=consumer(feature);if(!adapter.permitsDraft())throw new IllegalArgumentException("draft_amendment_required");try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT generation,base,schema,nonce,payload,updated FROM draft_entries WHERE feature=? AND entity=?",new String[]{feature,draftId})){if(!c.moveToFirst())return null;if(c.getInt(2)!=adapter.schemaVersion())throw new IllegalStateException("draft_schema_migration_required");Map<String,Object> value=adapter.validate(decode(decrypt(c.getBlob(3),c.getBlob(4),aad(feature,draftId,c.getLong(0),c.getLong(1),c.getInt(2)))),true);return new Row(value,c.getLong(1),0,c.getLong(0),clock.wall()-c.getLong(5)>=30L*86400000?"frozen_needs_keep_or_discard":"local_only");}});}
    /** Receipt ack clears exactly the sent generation, never a later local edit. */
    public Task<Boolean> acknowledgeDraft(String feature,String draftId,long sentGeneration){return task(()->{id(draftId);consumer(feature);return helper.getWritableDatabase().delete("draft_entries","feature=? AND entity=? AND generation=?",new String[]{feature,draftId,Long.toString(sentGeneration)})==1;});}
    public Task<Void> deleteDrafts(){return task(()->{helper.getWritableDatabase().delete("draft_entries",null,null);return null;});}
    /** Minimal outcome metadata only. It has no payload and cannot dispatch/replay a command. */
    public Task<Void> recordUnknownOutcome(String operationId,String command,String sourceKey,
                                           long expectedVersion,String inputDigest,String feature,
                                           String draftId,long sentGeneration){
        return task(()->{id(operationId);id(sourceKey);id(draftId);Consumer adapter=consumer(feature);
            if(!adapter.permitsDraft()||command==null||!command.matches("[a-z_]{1,40}")||!inputDigest.matches("[a-f0-9]{64}")||expectedVersion<0||sentGeneration<1)throw new IllegalArgumentException("intent_metadata");
            SQLiteDatabase db=helper.getWritableDatabase();try(Cursor old=db.rawQuery("SELECT command,source_key,expected,digest,feature,entity,generation FROM reconciliation_intents WHERE operation=?",new String[]{operationId})){
                if(old.moveToFirst()){if(!command.equals(old.getString(0))||!sourceKey.equals(old.getString(1))||expectedVersion!=old.getLong(2)||!inputDigest.equals(old.getString(3))||!feature.equals(old.getString(4))||!draftId.equals(old.getString(5))||sentGeneration!=old.getLong(6))throw new IllegalStateException("operation_identity_conflict");return null;}}
            if(scalar(db,"SELECT COUNT(*) FROM reconciliation_intents")>=20)throw new IllegalStateException("intent_capacity");
            ContentValues row=new ContentValues();row.put("operation",operationId);row.put("command",command);row.put("source_key",sourceKey);row.put("expected",expectedVersion);row.put("digest",inputDigest);row.put("feature",feature);row.put("entity",draftId);row.put("generation",sentGeneration);row.put("state","outcome_unknown");db.insertOrThrow("reconciliation_intents",null,row);return null;});
    }
    /** Invoke after a separately authorized managed receipt read, never on connectivity alone. */
    public Task<Boolean> applyConfirmedReceipt(String operationId,String matchingDigest,boolean committed){
        return task(()->{id(operationId);SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
            String feature,entity;long generation;try(Cursor c=db.rawQuery("SELECT digest,feature,entity,generation FROM reconciliation_intents WHERE operation=?",new String[]{operationId})){
                if(!c.moveToFirst()||!c.getString(0).equals(matchingDigest))throw new IllegalStateException("receipt_identity_mismatch");feature=c.getString(1);entity=c.getString(2);generation=c.getLong(3);}
            boolean cleared=committed&&db.delete("draft_entries","feature=? AND entity=? AND generation=?",new String[]{feature,entity,Long.toString(generation)})==1;
            ContentValues values=new ContentValues();values.put("state",committed?"committed":"not_committed");db.update("reconciliation_intents",values,"operation=?",new String[]{operationId});db.setTransactionSuccessful();return cleared;
        }finally{db.endTransaction();}});
    }
    public Task<Void> clearViewed(){cacheGeneration.incrementAndGet();return task(()->{SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{db.delete("query_items",null,null);db.delete("query_metadata",null,null);db.delete("cache_entries",null,null);db.delete("media_entries",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}deleteVariantFiles();return null;});}
    public Task<Void> setViewingEnabled(boolean enabled){viewingEnabled.set(enabled);return enabled?Tasks.forResult(null):clearViewed();}

    public Task<Void> saveQuery(String queryHash,long generation,String kind,List<String> ids,String anchor,int offset){List<String> frozen=new ArrayList<>(ids);return task(()->{consumer(kind);if(anchor!=null)id(anchor);if(!viewingEnabled.get()||!queryHash.matches("[a-f0-9]{64}")||generation<1||frozen.size()>100||new HashSet<>(frozen).size()!=frozen.size())throw new IllegalArgumentException("query_bounds");for(String entity:frozen){id(entity);if(readNow(kind,entity)==null)throw new IllegalStateException("query_contains_unavailable_page");}SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{long old=scalar(db,"SELECT COALESCE(MAX(generation),0) FROM query_metadata WHERE query_hash=?",queryHash);if(generation<old)throw new IllegalStateException("stale_query_generation");db.delete("query_items","query_hash=?",new String[]{queryHash});for(int i=0;i<frozen.size();i++){ContentValues item=new ContentValues();item.put("query_hash",queryHash);item.put("kind",kind);item.put("entity",frozen.get(i));item.put("ordinal",i);db.insertOrThrow("query_items",null,item);}ContentValues meta=new ContentValues();meta.put("query_hash",queryHash);meta.put("generation",generation);meta.put("anchor",anchor);meta.put("offset",offset);inserted(db.insertWithOnConflict("query_metadata",null,meta,SQLiteDatabase.CONFLICT_REPLACE));db.setTransactionSuccessful();return null;}finally{db.endTransaction();}});}
    public Task<List<Row>> savedQuery(String queryHash){return task(()->{List<Row> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT kind,entity FROM query_items WHERE query_hash=? ORDER BY ordinal LIMIT 100",new String[]{queryHash})){while(c.moveToNext()){Row row=readNow(c.getString(0),c.getString(1));if(row!=null)out.add(row);}}return out;});}
    public Task<List<Row>> searchSaved(String kind,String term){return task(()->{consumer(kind);if(term==null||term.length()>100)throw new IllegalArgumentException("search_term");List<Row> rows=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT entity FROM cache_entries WHERE kind=? ORDER BY viewed DESC LIMIT 500",new String[]{kind})){while(c.moveToNext()){Row row=readNow(kind,c.getString(0));if(row!=null&&new JSONObject(row.payload).toString().toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT)))rows.add(row);}}return rows;});}

    public Task<Void> putThumbnail(String kind,String entity,String asset,String variant,long generation,byte[] image,String expectedSha){final byte[] frozen=image.clone();final long cg=cacheGeneration.get();return task(()->{id(entity);id(asset);id(variant);Consumer adapter=consumer(kind);if(!adapter.permitsThumbnail())throw new IllegalArgumentException("media_cache_amendment_required");adapter.validateThumbnail(entity,asset,variant,generation,frozen,expectedSha);Row owner=readNow(kind,entity);if(owner==null||!viewingEnabled.get()||cg!=cacheGeneration.get()||generation<1||frozen.length>512*1024||!hashBytes(frozen).equals(expectedSha))throw new IllegalArgumentException("thumbnail_validation");SQLiteDatabase db=helper.getWritableDatabase();String mediaKey=hash(kind+":"+entity+":"+asset+":"+variant+":"+generation);if(scalar(db,"SELECT COUNT(*) FROM media_entries")>=THUMB_CAP||scalar(db,"SELECT COALESCE(SUM(bytes),0) FROM media_entries")+frozen.length>THUMB_BYTES)throw new IllegalStateException("thumbnail_capacity");sweepOrphans(db,20);long physical=0;int physicalCount=0;File[] physicalFiles=directory.listFiles();if(physicalFiles!=null)for(File file:physicalFiles)if(file.getName().matches("[a-f0-9]{64}\\.(variant|tmp)")){physical+=file.length();physicalCount++;}if(physicalCount>=400||physical+frozen.length+28>THUMB_BYTES)throw new IllegalStateException("physical_thumbnail_capacity");byte[][] encrypted=encrypt(frozen,"media:"+mediaKey);File temp=new File(directory,mediaKey+".tmp"),target=new File(directory,mediaKey+".variant");try(FileOutputStream stream=new FileOutputStream(temp)){stream.write(encrypted[0]);stream.write(encrypted[1]);stream.getFD().sync();}check();if(cg!=cacheGeneration.get()||!temp.renameTo(target)){temp.delete();throw new IOException("stale_thumbnail_commit");}ContentValues row=new ContentValues();row.put("file_key",mediaKey);row.put("kind",kind);row.put("entity",entity);row.put("asset",asset);row.put("variant",variant);row.put("generation",generation);row.put("bytes",frozen.length);row.put("sha",expectedSha);row.put("source",owner.sourceVersion);row.put("control",owner.controlVersion);inserted(db.insertWithOnConflict("media_entries",null,row,SQLiteDatabase.CONFLICT_REPLACE));return null;});}
    public Task<byte[]> thumbnail(String kind,String entity,String asset,String variant,long generation){return task(()->{Consumer adapter=consumer(kind);if(!adapter.permitsThumbnail())throw new IllegalArgumentException("media_cache_amendment_required");Row owner=readNow(kind,entity);if(owner==null)return null;try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT file_key,sha,source,control FROM media_entries WHERE kind=? AND entity=? AND asset=? AND variant=? AND generation=?",new String[]{kind,entity,asset,variant,Long.toString(generation)})){if(!c.moveToFirst()||c.getLong(2)!=owner.sourceVersion||c.getLong(3)!=owner.controlVersion)return null;File file=new File(directory,c.getString(0)+".variant");if(!file.isFile())return null;byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());if(bytes.length<28||bytes.length>512*1024+28)return null;byte[] plain=decrypt(Arrays.copyOfRange(bytes,0,12),Arrays.copyOfRange(bytes,12,bytes.length),"media:"+c.getString(0));if(!hashBytes(plain).equals(c.getString(1)))throw new GeneralSecurityException("thumbnail_hash");return plain;}});}
    private static String hashBytes(byte[] b)throws Exception{byte[] h=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:h)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    private void sweepOrphans(SQLiteDatabase db,int max){int seen=0;File[] files=directory.listFiles();if(files==null)return;for(File file:files){if(seen>=max)break;if(!file.getName().matches("[a-f0-9]{64}\\.(variant|tmp)"))continue;String key=file.getName().substring(0,64);if(scalar(db,"SELECT COUNT(*) FROM media_entries WHERE file_key=?",key)==0){file.delete();seen++;}}}
    private void deleteVariantFiles(){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[a-f0-9]{64}\\.(variant|tmp)"))f.delete();}
    /** Process close preserves same-boot cache/drafts. Future logout wiring MUST retain namespace ownership and call closeNamespace before releasing this instance; closing a handle is not account erasure. */
    @Override public synchronized void close(){if(!disposed.compareAndSet(false,true))return;sessions.removeInvalidation(invalidate);active.set(false);worker.execute(()->helper.close());worker.shutdown();}
    public synchronized Task<Void> closeNamespace(){if(disposal!=null)return disposal;if(!disposed.compareAndSet(false,true))return Tasks.forException(new IllegalStateException("store_already_closed"));active.set(false);cacheGeneration.incrementAndGet();sessions.removeInvalidation(invalidate);disposal=Tasks.call(worker,()->{helper.close();KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(keyAlias))ks.deleteEntry(keyAlias);key=null;deleteVariantFiles();SQLiteDatabase.deleteDatabase(databaseFile);return null;});disposal.addOnCompleteListener(Runnable::run,done->worker.shutdown());return disposal;}
    private static final class Helper extends SQLiteOpenHelper {
        Helper(Context c,String name){super(c,name,null,1);}
        @Override public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE cache_entries(kind TEXT NOT NULL,entity TEXT NOT NULL,source INTEGER NOT NULL,control INTEGER NOT NULL,schema INTEGER NOT NULL,nonce BLOB NOT NULL,payload BLOB NOT NULL,bytes INTEGER NOT NULL,validated INTEGER NOT NULL,elapsed INTEGER NOT NULL,boot INTEGER NOT NULL,lease INTEGER NOT NULL,viewed INTEGER NOT NULL,PRIMARY KEY(kind,entity))");
            d.execSQL("CREATE INDEX cache_viewed ON cache_entries(kind,viewed)");
            d.execSQL("CREATE TABLE invalidations(kind TEXT,entity TEXT,source INTEGER,control INTEGER,PRIMARY KEY(kind,entity))");
            d.execSQL("CREATE TABLE draft_entries(feature TEXT,entity TEXT,generation INTEGER,base INTEGER,schema INTEGER,nonce BLOB,payload BLOB,updated INTEGER,state TEXT,PRIMARY KEY(feature,entity))");
            d.execSQL("CREATE TABLE query_metadata(query_hash TEXT PRIMARY KEY,generation INTEGER,anchor TEXT,offset INTEGER)");
            d.execSQL("CREATE TABLE query_items(query_hash TEXT,kind TEXT,entity TEXT,ordinal INTEGER,PRIMARY KEY(query_hash,entity))");
            d.execSQL("CREATE TABLE media_entries(file_key TEXT PRIMARY KEY,kind TEXT,entity TEXT,asset TEXT,variant TEXT,generation INTEGER,bytes INTEGER,sha TEXT,source INTEGER,control INTEGER)");
            d.execSQL("CREATE TABLE reconciliation_intents(operation TEXT PRIMARY KEY,command TEXT,source_key TEXT,expected INTEGER,digest TEXT,feature TEXT,entity TEXT,generation INTEGER,state TEXT)");
        }
        @Override public void onUpgrade(SQLiteDatabase d,int old,int next){throw new IllegalStateException("lossless_draft_migration_required");}
    }
}
