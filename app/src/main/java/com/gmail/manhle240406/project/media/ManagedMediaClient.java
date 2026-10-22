package com.gmail.manhle240406.project.media;

import com.google.android.gms.tasks.*;
import com.google.firebase.auth.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Firebase-managed, token-free byte transport; never a public Storage download URL. */
public final class ManagedMediaClient {
    /** Instrumentation-only seam. Release builds always use the managed Firebase transport. */
    public interface DebugTransport { Task<byte[]> request(String operation,byte[] body,String assetId); }
    public static DebugTransport debugTransport;
    private static final ExecutorService WORK=Executors.newFixedThreadPool(2);
    private static final String BASE="https://asia-east2-aniloveproject.cloudfunctions.net/mediaApi/";
    private static com.gmail.manhle240406.project.foundation.SessionGateway sessions;
    public static void configure(com.gmail.manhle240406.project.foundation.SessionGateway gateway){sessions=gateway;if(gateway!=null)gateway.onInvalidated(SessionImageCache::clear);}
    private ManagedMediaClient(){}
    public static Map<String,Object> map(Object... pairs){Map<String,Object> m=new HashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    public static Task<Map<String,Object>> call(String operation,Map<String,Object> body){
        return request(operation,new JSONObject(body).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),null).continueWith(WORK,t->{byte[] b=t.getResult();Map<String,Object> result=object(new JSONObject(new String(b,java.nio.charset.StandardCharsets.UTF_8)));if(java.util.Arrays.asList("avatar","google","remove","saveProfile","publishProfile","post").contains(operation)){SessionImageCache.clearAll();com.gmail.manhle240406.project.community.SessionAliases.clear();}return result;});
    }
    public static Task<Map<String,Object>> upload(String id,byte[] bytes){return request("receive",bytes,id).continueWith(WORK,t->object(new JSONObject(new String(t.getResult(),java.nio.charset.StandardCharsets.UTF_8))));}
    public static Task<byte[]> image(String id,String variant){return request("read",new JSONObject(map("assetId",id,"variant",variant)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),null);}
    private static Task<byte[]> request(String operation,byte[] body,String id){
        if(com.gmail.manhle240406.project.BuildConfig.DEBUG&&debugTransport!=null)return debugTransport.request(operation,body,id);
        if(com.gmail.manhle240406.project.BuildConfig.FIREBASE_EMULATORS||!com.gmail.manhle240406.project.BuildConfig.MEDIA_UPLOADS_ENABLED)return Tasks.forException(new IllegalStateException("media_disabled"));
        if(!"aniloveproject".equals(com.google.firebase.FirebaseApp.getInstance().getOptions().getProjectId()))return Tasks.forException(new IllegalStateException("media_disabled"));
        com.gmail.manhle240406.project.foundation.SessionEpoch.Ticket epoch=sessions==null?null:sessions.capture();
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();String uid=user==null?null:user.getUid();
        if(uid==null&&!operation.equals("read"))return Tasks.forException(new IllegalStateException("unauthenticated"));
        Task<String> token=user==null?Tasks.forResult(null):user.getIdToken(false).continueWith(t->t.getResult().getToken());
        return token.continueWithTask(WORK,t->{String credential=t.getResult();return Tasks.call(WORK,()->{
            current(uid);if(epoch!=null&&!sessions.current(epoch))throw new IllegalStateException("session_changed");HttpURLConnection c=(HttpURLConnection)new URL(BASE+operation).openConnection();
            try{c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setDoOutput(true);c.setRequestProperty("Content-Type",id==null?"application/json":"image/jpeg");if(credential!=null)c.setRequestProperty("Authorization","Bearer "+credential);if(id!=null)c.setRequestProperty("X-Asset-Id",id);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}int status=c.getResponseCode();byte[] data;
                try(InputStream in=status<300?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in!=null){byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){if(out.size()+n>3*1024*1024)throw new IOException("response_size");out.write(block,0,n);}}data=out.toByteArray();}
                current(uid);if(epoch!=null&&!sessions.current(epoch))throw new IllegalStateException("session_changed");if(status>=300){String code="unavailable";try{code=new JSONObject(new String(data,java.nio.charset.StandardCharsets.UTF_8)).optString("error",code);}catch(JSONException ignored){}throw new IOException(code);}return data;
            }finally{c.disconnect();}
        });});
    }
    private static void current(String uid){FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();if(!Objects.equals(uid,u==null?null:u.getUid()))throw new IllegalStateException("session_changed");}
    private static Object value(Object v)throws JSONException{if(v==JSONObject.NULL)return null;if(v instanceof JSONObject)return object((JSONObject)v);if(v instanceof JSONArray){List<Object> a=new ArrayList<>();for(int i=0;i<((JSONArray)v).length();i++)a.add(value(((JSONArray)v).get(i)));return a;}if(v instanceof Integer)return ((Integer)v).longValue();return v;}
    private static Map<String,Object> object(JSONObject j)throws JSONException{Map<String,Object> out=new HashMap<>();Iterator<String> keys=j.keys();while(keys.hasNext()){String k=keys.next();out.put(k,value(j.get(k)));}return out;}
}
