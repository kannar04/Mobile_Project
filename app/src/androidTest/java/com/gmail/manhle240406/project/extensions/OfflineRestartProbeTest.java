package com.gmail.manhle240406.project.extensions;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gmail.manhle240406.project.AniloveApplication;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gmail.manhle240406.project.auth.AuthState;
import androidx.lifecycle.Observer;
import java.util.concurrent.CountDownLatch;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Run phase=write, force-stop the app, then phase=read. Fixture consumer ONLY. */
@RunWith(AndroidJUnit4.class)
public class OfflineRestartProbeTest {
 @Test public void sameBootProcessRestartReadsEncryptedFixtureWithFirestoreNetworkDisabled()throws Exception{
  AniloveApplication app=ApplicationProvider.getApplicationContext();assertEquals("local_emulator",app.foundation().alias);
  String phase=InstrumentationRegistry.getArguments().getString("phase");assertTrue("write".equals(phase)||"read".equals(phase));
  if("write".equals(phase)){
   CountDownLatch ready=new CountDownLatch(1);Observer<AuthState> observer=s->{if(s.uid!=null&&s.setupReady&&!s.pending)ready.countDown();};
   InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{app.auth().state().observeForever(observer);app.auth().email("offline-probe-"+UUID.randomUUID()+"@example.test","synthetic-password-123",true);});
   try{assertTrue("approved F01 account bootstrap",ready.await(30,TimeUnit.SECONDS));}
   finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().state().removeObserver(observer));}
  }
  assertNotNull(FirebaseAuth.getInstance().getCurrentUser());
  EncryptedOfflineStore.Consumer fixture=new EncryptedOfflineStore.Consumer(){public int schemaVersion(){return 1;}public long leaseMillis(){return 3600000;}public Map<String,Object> validate(Map<String,Object> p,boolean draft){if(!p.keySet().equals(Set.of("title"))||!(p.get("title") instanceof String))throw new IllegalArgumentException("fixture_only");return new HashMap<>(p);}};
  EncryptedOfflineStore store=new EncryptedOfflineStore(app,app.foundation().sessions,app.foundation().sessions.capture(),"restart_probe_fixture_r1",Map.of("fixture_page",fixture));
  if("write".equals(phase)){Tasks.await(store.putValidated("fixture_page","page",1,1,System.currentTimeMillis(),Map.of("title","Previously viewed fictional page")),20,TimeUnit.SECONDS);store.close();}
  else{Tasks.await(FirebaseFirestore.getInstance().disableNetwork(),10,TimeUnit.SECONDS);try{EncryptedOfflineStore.Row row=Tasks.await(store.read("fixture_page","page"),20,TimeUnit.SECONDS);assertNotNull(row);assertEquals("Previously viewed fictional page",row.payload.get("title"));assertNull(Tasks.await(store.read("fixture_page","unseen"),20,TimeUnit.SECONDS));}finally{Tasks.await(store.closeNamespace(),20,TimeUnit.SECONDS);Tasks.await(FirebaseFirestore.getInstance().enableNetwork(),10,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}}
 }
}
