package com.gmail.manhle240406.project.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.LruCache;
import com.google.android.gms.tasks.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Bounded account-scoped RAM plus encrypted, app-private thumbnail fallback; never authority. */
public final class SessionImageCache {
 private static final long TTL=60_000;
 private static final long DISK_TTL=24*60*60_000L,DISK_CAP=100L*1024*1024;
 private static final java.util.concurrent.ExecutorService DECODE=java.util.concurrent.Executors.newFixedThreadPool(2);
 private static final LruCache<String,Entry> CACHE=new LruCache<String,Entry>(20*1024*1024){protected int sizeOf(String key,Entry e){return e.bitmap.getAllocationByteCount();}};
 private static final Map<String,Work> PENDING=new HashMap<>();
 private static final ArrayDeque<Work> QUEUE=new ArrayDeque<>();
 private static int active;
 private static long generation;
 private static File disk;
 static java.util.function.BiFunction<String,String,Task<byte[]>> source=ManagedMediaClient::image;
 static java.util.function.LongSupplier clock=SystemClock::elapsedRealtime;
 public static synchronized long epoch(){return generation;}
 static synchronized int bytes(){return CACHE.size();}
 static synchronized int pending(){return PENDING.size();}
 private SessionImageCache(){}
 public static synchronized void initialize(Context context){disk=new File(context.getNoBackupFilesDir(),"offline_images");if(!disk.isDirectory())disk.mkdirs();}
 private static final class Entry {final Bitmap bitmap;final long at=clock.getAsLong();Entry(Bitmap b){bitmap=b;}}
 private static final class Work {final String key,uid,id,variant,accessTarget;final long epoch=generation;final TaskCompletionSource<Bitmap> result=new TaskCompletionSource<>();Work(String k,String u,String i,String v,String a){key=k;uid=u;id=i;variant=v;accessTarget=a;}}
 public static synchronized void clear(){generation++;CACHE.evictAll();while(!QUEUE.isEmpty()){Work w=QUEUE.remove();PENDING.remove(w.key);w.result.trySetException(new IllegalStateException("session_changed"));}}
 public static synchronized void clearAll(){clear();if(disk!=null)DECODE.execute(()->delete(disk));}
 static synchronized void resetForTests(){clear();if(disk!=null)delete(disk);}
 public static synchronized Task<Bitmap> get(String uid,String id,String variant){
  return get(uid,id,variant,null);
 }
 public static synchronized Task<Bitmap> get(String uid,String id,String variant,String accessTarget){
  String key=generation+":"+String.valueOf(uid)+":"+id+":"+variant+":"+String.valueOf(accessTarget);Entry e=CACHE.get(key);
  if(e!=null&&clock.getAsLong()-e.at<TTL)return Tasks.forResult(e.bitmap);
  CACHE.remove(key);Work previous=PENDING.get(key);if(previous!=null)return previous.result.getTask();
  if(PENDING.size()>=64)return Tasks.forException(new IllegalStateException("image_queue_full"));
  Work work=new Work(key,String.valueOf(uid),id,variant,accessTarget);PENDING.put(key,work);QUEUE.add(work);pump();return work.result.getTask();
 }
 private static void pump(){while(active<4&&!QUEUE.isEmpty()){Work w=QUEUE.remove();active++;
  Tasks.call(DECODE,()->readDisk(w)).continueWithTask(local->{if(local.isSuccessful()&&local.getResult()!=null)return Tasks.forResult(local.getResult());Task<byte[]> remote=w.accessTarget==null?source.apply(w.id,w.variant):ManagedMediaClient.image(w.id,w.variant,w.accessTarget);return remote.continueWith(DECODE,t->{byte[] bytes=t.getResult();Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IllegalStateException("image_decode");synchronized(SessionImageCache.class){if(w.epoch!=generation)throw new IllegalStateException("session_changed");}writeDisk(w,bytes);return b;});}).addOnCompleteListener(t->{synchronized(SessionImageCache.class){active--;PENDING.remove(w.key);if(w.epoch!=generation)w.result.trySetException(new IllegalStateException("session_changed"));else if(t.isSuccessful()){CACHE.put(w.key,new Entry(t.getResult()));w.result.trySetResult(t.getResult());}else w.result.trySetException(t.getException());pump();}});
 }}
 private static Bitmap readDisk(Work w){try{File f=file(w);if(!f.isFile()||System.currentTimeMillis()-f.lastModified()>DISK_TTL){if(f.exists())f.delete();return null;}byte[] all=read(f);if(all.length<13)return null;byte[] iv=Arrays.copyOfRange(all,0,12),cipher=Arrays.copyOfRange(all,12,all.length);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(w),new GCMParameterSpec(128,iv));c.updateAAD(aad(w));byte[] bytes=c.doFinal(cipher);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception bad){File f=file(w);if(f.exists())f.delete();return null;}}
 private static void writeDisk(Work w,byte[] bytes){try{if(disk==null||bytes.length==0)return;Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(w));c.updateAAD(aad(w));byte[] encrypted=c.doFinal(bytes),out=new byte[12+encrypted.length];System.arraycopy(c.getIV(),0,out,0,12);System.arraycopy(encrypted,0,out,12,encrypted.length);File f=file(w),tmp=new File(f.getPath()+".tmp");try(FileOutputStream stream=new FileOutputStream(tmp)){stream.write(out);stream.getFD().sync();}if(!tmp.renameTo(f)){tmp.delete();return;}trim();}catch(Exception ignored){}}
 private static SecretKey key(Work w)throws Exception{String alias="anilove_image_"+hex(w.uid).substring(0,24);KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(alias))return (SecretKey)store.getKey(alias,null);KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 private static byte[] aad(Work w){return (w.id+":"+w.variant).getBytes(StandardCharsets.UTF_8);}
 private static File file(Work w){return new File(disk,hex(w.uid+":"+w.id+":"+w.variant)+".bin");}
 private static String hex(String value){try{StringBuilder b=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}catch(Exception e){throw new IllegalStateException(e);}}
 private static byte[] read(File f)throws IOException{try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);return out.toByteArray();}}
 private static void trim(){File[] files=disk==null?null:disk.listFiles((d,n)->n.endsWith(".bin"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();for(File f:files)if(total>DISK_CAP){total-=f.length();f.delete();}}
 private static void delete(File root){File[] files=root.listFiles();if(files!=null)for(File f:files)if(f.isDirectory())delete(f);else f.delete();}
}
