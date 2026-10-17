package com.gmail.manhle240406.project.community;

import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.*;
import android.provider.Settings;
import android.security.keystore.*;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Explicit-intent encrypted SQL queue. All disk/crypto/network waits run off the UI thread. */
public final class CommunityOutbox {
 public static final int JOB=3104;
 public interface Scheduler {void schedule(long delay);void cancel();}
 public static final class Entry {
  public final SocialCommand command;public final String state;final long accepted,boot,next;final int attempts;
  Entry(SocialCommand c,String s,long a,long b,long n,int tries){command=c;state=s;accepted=a;boot=b;next=n;attempts=tries;}
  public boolean review(){return "needs_review".equals(state);}
  public boolean replaceable(){return attempts==0&&java.util.Arrays.asList("queued","policy_paused","retry_wait").contains(state);}
 }
 private final Context app;private final SessionGateway sessions;private final Helper helper;private final Scheduler scheduler;private final FirebaseFirestore database;private String activeOwner;
 private final ExecutorService disk=Executors.newSingleThreadExecutor(),network=Executors.newSingleThreadExecutor();
 private final MutableLiveData<List<Entry>> entries=new MutableLiveData<>(Collections.emptyList());
 private final Handler main=new Handler(Looper.getMainLooper());private volatile boolean running,cleanupFailed,stopRequested;
 private volatile long epoch;private final Runnable invalidation=this::invalidate;
 public CommunityOutbox(Context c,SessionGateway s){this(c,s,null);}
 public CommunityOutbox(Context c,SessionGateway s,Scheduler scheduler){this(c,s,scheduler,s==null?null:FirebaseFirestore.getInstance());}
 public CommunityOutbox(Context c,SessionGateway s,Scheduler scheduler,FirebaseFirestore database){this.database=s==null?null:Objects.requireNonNull(database);this.scheduler=scheduler;app=c.getApplicationContext();sessions=s;helper=new Helper(app,new File(app.getNoBackupFilesDir(),"community-intents.db").getAbsolutePath());if(s!=null)s.onInvalidated(invalidation);if(s==null||s.capture().uid==null)invalidate();else {activeOwner=owner(s.capture().uid);refresh();}}
 public LiveData<List<Entry>> entries(){return entries;}
 public Entry pendingLike(String post){List<Entry> current=entries.getValue();Entry found=null;if(current!=null)for(Entry e:current)if(post.equals(e.command.postId)&&"set_like".equals(e.command.command))found=e;return found;}
 private void check(SessionEpoch.Ticket t){if(cleanupFailed||sessions==null||t==null||t.uid==null||!sessions.current(t))throw new IllegalStateException("session_changed");}
 private static String owner(String uid){try{byte[] h=MessageDigest.getInstance("SHA-256").digest(uid.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:h)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 private long boot(){return Settings.Global.getInt(app.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}
 private SecretKey key(String owner,boolean create)throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);String alias="anilove_community_intent_"+owner;if(ks.containsAlias(alias))return (SecretKey)ks.getKey(alias,null);if(!create)throw new GeneralSecurityException("key_missing");KeyGenerator g=KeyGenerator.getInstance("AES","AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 private byte[][] encrypt(String owner,SocialCommand c,long accepted,long boot)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(owner,true));cipher.updateAAD((owner+":"+c.operationId+":"+accepted+":"+boot).getBytes(StandardCharsets.UTF_8));return new byte[][]{cipher.getIV(),cipher.doFinal(CommunityIntent.encode(c).getBytes(StandardCharsets.UTF_8))};}
 private SocialCommand decrypt(String owner,String id,long accepted,long boot,byte[] iv,byte[] payload)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(owner,false),new GCMParameterSpec(128,iv));cipher.updateAAD((owner+":"+id+":"+accepted+":"+boot).getBytes(StandardCharsets.UTF_8));SocialCommand c=CommunityIntent.decode(new String(cipher.doFinal(payload),StandardCharsets.UTF_8));if(!id.equals(c.operationId))throw new GeneralSecurityException("intent_id");return c;}
 private SocialCommand decryptLegacy(String owner,String id,byte[] iv,byte[] payload)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(owner,false),new GCMParameterSpec(128,iv));cipher.updateAAD((owner+":"+id).getBytes(StandardCharsets.UTF_8));SocialCommand c=CommunityIntent.decode(new String(cipher.doFinal(payload),StandardCharsets.UTF_8));if(!id.equals(c.operationId))throw new GeneralSecurityException("intent_id");return c;}
 private List<Entry> read(SessionEpoch.Ticket t)throws Exception{check(t);String o=owner(t.uid);List<Entry> out=new ArrayList<>();try(Cursor c=helper.getReadableDatabase().rawQuery("SELECT id,state,accepted,boot,next,attempts,iv,payload FROM intents WHERE owner=? ORDER BY accepted,id LIMIT 50",new String[]{o})){while(c.moveToNext()){SocialCommand cmd;boolean legacy=false;try{cmd=decrypt(o,c.getString(0),c.getLong(2),c.getLong(3),c.getBlob(6),c.getBlob(7));}catch(AEADBadTagException oldEnvelope){cmd=decryptLegacy(o,c.getString(0),c.getBlob(6),c.getBlob(7));legacy=true;}String state=legacy?"needs_review":c.getString(1);if(!java.util.Arrays.asList("sending","outcome_unknown").contains(state)&&!CommunityIntent.maySend(c.getLong(2),c.getLong(3),SystemClock.elapsedRealtime(),boot(),c.getInt(5)))state="needs_review";out.add(new Entry(cmd,state,c.getLong(2),c.getLong(3),c.getLong(4),c.getInt(5)));}}check(t);return out;}
 public void refresh(){if(sessions==null)return;SessionEpoch.Ticket t=sessions.capture();long generation=epoch;Tasks.call(disk,()->read(t)).addOnSuccessListener(value->{if(generation==epoch&&sessions.current(t)){entries.setValue(Collections.unmodifiableList(value));long next=Long.MAX_VALUE;for(Entry e:value)if(!e.review())next=Math.min(next,e.next);if(next!=Long.MAX_VALUE)schedule(Math.max(0,next-SystemClock.elapsedRealtime()));}});}
 public Task<Void> accept(SessionEpoch.Ticket t,SocialCommand command){CommunityIntent.validate(command);return Tasks.call(disk,()->{check(t);String o=owner(t.uid);activeOwner=o;byte[] plain=CommunityIntent.encode(command).getBytes(StandardCharsets.UTF_8);long accepted=SystemClock.elapsedRealtime(),acceptedBoot=boot();byte[][] encrypted=encrypt(o,command,accepted,acceptedBoot);SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();try{
   try(Cursor prior=db.rawQuery("SELECT owner,accepted,boot,iv,payload FROM intents WHERE id=?",new String[]{command.operationId})){if(prior.moveToFirst()){if(!o.equals(prior.getString(0))||!CommunityIntent.encode(decrypt(o,command.operationId,prior.getLong(1),prior.getLong(2),prior.getBlob(3),prior.getBlob(4))).equals(CommunityIntent.encode(command)))throw new IllegalStateException("operation_conflict");db.setTransactionSuccessful();return null;}}
   // Coalesce only an unsent same-post like. Never mutate a possibly committed in-flight operation.
   if(command.command.equals("set_like"))db.delete("intents","owner=? AND kind='set_like' AND post=? AND state IN ('queued','policy_paused','retry_wait') AND attempts=0",new String[]{o,command.postId});
   try(Cursor count=db.rawQuery("SELECT COUNT(*),COALESCE(SUM(bytes),0) FROM intents WHERE owner=?",new String[]{o})){count.moveToFirst();if(count.getInt(0)>=50||count.getLong(1)+plain.length>128*1024)throw new IllegalStateException("queue_full");}
   ContentValues row=new ContentValues();row.put("id",command.operationId);row.put("owner",o);row.put("kind",command.command);row.put("post",command.postId);row.put("state","queued");row.put("accepted",accepted);row.put("boot",acceptedBoot);row.put("next",0);row.put("attempts",0);row.put("bytes",plain.length);row.put("iv",encrypted[0]);row.put("payload",encrypted[1]);check(t);if(db.insertOrThrow("intents",null,row)<0)throw new SQLiteException("local_save_failed");db.setTransactionSuccessful();
  }finally{db.endTransaction();}check(t);return null;}).continueWithTask(done->{if(!done.isSuccessful())return Tasks.forException(Objects.requireNonNull(done.getException()));refresh();schedule(0);return Tasks.forResult(null);});}
 public Task<Void> cancel(String id){SessionEpoch.Ticket t=sessions.capture();return Tasks.<Void>call(disk,()->{check(t);helper.getWritableDatabase().delete("intents","owner=? AND id=? AND state NOT IN ('sending','outcome_unknown')",new String[]{owner(t.uid),id});return null;}).addOnSuccessListener(v->refresh());}
 private void update(SessionEpoch.Ticket t,Entry e,String state,int attempts,long delay)throws Exception{Tasks.await(Tasks.call(disk,()->{check(t);ContentValues v=new ContentValues();v.put("state",state);v.put("attempts",attempts);v.put("next",SystemClock.elapsedRealtime()+delay);if(helper.getWritableDatabase().update("intents",v,"owner=? AND id=?",new String[]{owner(t.uid),e.command.operationId})!=1)throw new IllegalStateException("intent_removed");return null;}));}
 private void remove(SessionEpoch.Ticket t,Entry e)throws Exception{Tasks.await(Tasks.call(disk,()->{check(t);helper.getWritableDatabase().delete("intents","owner=? AND id=?",new String[]{owner(t.uid),e.command.operationId});return null;}));}
 public void schedule(long delay){if(sessions==null||sessions.capture().uid==null)return;if(scheduler!=null){scheduler.schedule(delay);return;}app.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(JOB,new ComponentName(app,CommunitySyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
 public void stop(){stopRequested=true;}
 public void drain(Runnable finished){if(running){finished.run();return;}running=true;stopRequested=false;long generation=epoch;network.execute(()->{boolean retry=false;try{
   SessionEpoch.Ticket t=sessions.capture();check(t);List<Entry> candidates=Tasks.await(Tasks.call(disk,()->read(t)));SocialRepository social=new SocialRepository(database,sessions);
   for(Entry e:candidates){if(generation!=epoch||stopRequested)break;check(t);if(e.review())continue;if(e.next>SystemClock.elapsedRealtime()){retry=true;continue;}
    boolean ambiguous="sending".equals(e.state)||"outcome_unknown".equals(e.state),sendingThisRun=false;
    try{
     // Always reconcile first. Missing online receipt is the only permission to try the exact frozen input.
     DocumentSnapshot receipt=Tasks.await(social.read("users/"+t.uid+"/communityOperations/"+e.command.operationId),20,TimeUnit.SECONDS);
     if(receipt.exists()){if(!e.command.matches(receipt.getData())){update(t,e,"needs_review",e.attempts,0);continue;}remove(t,e);continue;}
     if(!CommunityIntent.maySend(e.accepted,e.boot,SystemClock.elapsedRealtime(),boot(),e.attempts)){update(t,e,"needs_review",e.attempts,0);continue;}
     DocumentSnapshot policy=Tasks.await(social.read("system/communityPolicy"),20,TimeUnit.SECONDS);
     if(!Boolean.TRUE.equals(policy.get("enabled"))){update(t,e,"policy_paused",e.attempts,30*60*1000);retry=true;continue;}
     if(stopRequested)break;update(t,e,"sending",e.attempts+1,0);ambiguous=true;sendingThisRun=true;
     Tasks.await(social.execute(t,e.command),45,TimeUnit.SECONDS);check(t);remove(t,e);
    }catch(Exception failure){check(t);Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();boolean transientFailure=cause instanceof java.util.concurrent.TimeoutException||cause instanceof FirebaseFirestoreException&&Arrays.asList(FirebaseFirestoreException.Code.UNAVAILABLE,FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,FirebaseFirestoreException.Code.ABORTED,FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED).contains(((FirebaseFirestoreException)cause).getCode());
     if(transientFailure){int attempts=e.attempts+1;update(t,e,ambiguous?"outcome_unknown":"retry_wait",attempts,Math.min(30*60*1000,30000L*(1L<<Math.min(6,attempts))));retry=true;}else{boolean deniedWrite=sendingThisRun&&(cause instanceof IllegalStateException||cause instanceof FirebaseFirestoreException&&java.util.Arrays.asList(FirebaseFirestoreException.Code.PERMISSION_DENIED,FirebaseFirestoreException.Code.UNAUTHENTICATED,FirebaseFirestoreException.Code.FAILED_PRECONDITION,FirebaseFirestoreException.Code.INVALID_ARGUMENT).contains(((FirebaseFirestoreException)cause).getCode()));update(t,e,ambiguous&&!deniedWrite?"outcome_unknown":"needs_review",e.attempts,30*60*1000);}
    }
   }
  }catch(Exception ignored){/* No payload/error logging; next authenticated foreground can reconcile. */}finally{running=false;refresh();if(retry&&generation==epoch)schedule(30*60*1000);main.post(finished);}});}
 private void invalidate(){epoch++;if(scheduler!=null)scheduler.cancel();else app.getSystemService(JobScheduler.class).cancel(JOB);entries.postValue(Collections.emptyList());disk.execute(()->{try{SQLiteDatabase db=helper.getWritableDatabase();Set<String> owners=new HashSet<>();if(activeOwner!=null)owners.add(activeOwner);try(Cursor cursor=db.rawQuery("SELECT DISTINCT owner FROM intents",null)){while(cursor.moveToNext())owners.add(cursor.getString(0));}db.delete("intents",null,null);KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);for(String owned:owners)ks.deleteEntry("anilove_community_intent_"+owned);activeOwner=null;cleanupFailed=false;}catch(Exception e){cleanupFailed=true;}});}
 private static final class Helper extends SQLiteOpenHelper {Helper(Context c,String path){super(c,path,null,1);}public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE intents(id TEXT PRIMARY KEY,owner TEXT NOT NULL,kind TEXT NOT NULL,post TEXT NOT NULL,state TEXT NOT NULL,accepted INTEGER NOT NULL,boot INTEGER NOT NULL,next INTEGER NOT NULL,attempts INTEGER NOT NULL,bytes INTEGER NOT NULL,iv BLOB NOT NULL,payload BLOB NOT NULL)");}public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){throw new SQLiteException("outbox_upgrade_requires_migration");}}
}
