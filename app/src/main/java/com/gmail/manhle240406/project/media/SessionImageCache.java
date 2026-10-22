package com.gmail.manhle240406.project.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.LruCache;
import com.google.android.gms.tasks.*;
import java.util.*;

/** Downloaded copies only: bounded, short-lived, account-scoped RAM; never disk or authority. */
public final class SessionImageCache {
 private static final long TTL=60_000;
 private static final java.util.concurrent.ExecutorService DECODE=java.util.concurrent.Executors.newFixedThreadPool(2);
 private static final LruCache<String,Entry> CACHE=new LruCache<String,Entry>(12*1024*1024){protected int sizeOf(String key,Entry e){return e.bitmap.getAllocationByteCount();}};
 private static final Map<String,Work> PENDING=new HashMap<>();
 private static final ArrayDeque<Work> QUEUE=new ArrayDeque<>();
 private static int active;
 private static long generation;
 static java.util.function.BiFunction<String,String,Task<byte[]>> source=ManagedMediaClient::image;
 static java.util.function.LongSupplier clock=SystemClock::elapsedRealtime;
 public static synchronized long epoch(){return generation;}
 static synchronized int bytes(){return CACHE.size();}
 static synchronized int pending(){return PENDING.size();}
 private SessionImageCache(){}
 private static final class Entry {final Bitmap bitmap;final long at=clock.getAsLong();Entry(Bitmap b){bitmap=b;}}
 private static final class Work {final String key,id,variant;final long epoch=generation;final TaskCompletionSource<Bitmap> result=new TaskCompletionSource<>();Work(String k,String i,String v){key=k;id=i;variant=v;}}
 public static synchronized void clear(){generation++;CACHE.evictAll();while(!QUEUE.isEmpty()){Work w=QUEUE.remove();PENDING.remove(w.key);w.result.trySetException(new IllegalStateException("session_changed"));}}
 public static synchronized Task<Bitmap> get(String uid,String id,String variant){
  String key=generation+":"+String.valueOf(uid)+":"+id+":"+variant;Entry e=CACHE.get(key);
  if(e!=null&&clock.getAsLong()-e.at<TTL)return Tasks.forResult(e.bitmap);
  CACHE.remove(key);Work previous=PENDING.get(key);if(previous!=null)return previous.result.getTask();
  if(PENDING.size()>=64)return Tasks.forException(new IllegalStateException("image_queue_full"));
  Work work=new Work(key,id,variant);PENDING.put(key,work);QUEUE.add(work);pump();return work.result.getTask();
 }
 private static void pump(){while(active<4&&!QUEUE.isEmpty()){Work w=QUEUE.remove();active++;
  source.apply(w.id,w.variant).continueWith(DECODE,t->{byte[] bytes=t.getResult();Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(b==null)throw new IllegalStateException("image_decode");return b;}).addOnCompleteListener(t->{synchronized(SessionImageCache.class){active--;PENDING.remove(w.key);if(w.epoch!=generation)w.result.trySetException(new IllegalStateException("session_changed"));else if(t.isSuccessful()){CACHE.put(w.key,new Entry(t.getResult()));w.result.trySetResult(t.getResult());}else w.result.trySetException(t.getException());pump();}});
 }}
}
