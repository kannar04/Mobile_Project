package com.gmail.manhle240406.project.listing;

import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthState;
import com.gmail.manhle240406.project.foundation.SessionEpoch;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static com.gmail.manhle240406.project.pet.PetSchema.*;

/** Compile-only here until an Android target exists. All accounts/data are synthetic emulator fixtures. */
@RunWith(AndroidJUnit4.class)
public class ListingUiIntegrationTest {
    private AniloveApplication account()throws Exception{AniloveApplication app=ApplicationProvider.getApplicationContext();assertEquals("local_emulator",app.foundation().alias);CountDownLatch ready=new CountDownLatch(1);Observer<AuthState> observer=s->{if(s.uid!=null&&s.setupReady&&!s.pending)ready.countDown();};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{app.auth().logout();app.auth().state().observeForever(observer);app.auth().email("listing-"+UUID.randomUUID()+"@example.test","synthetic-password-123",true);});try{assertTrue(ready.await(25,TimeUnit.SECONDS));}finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().state().removeObserver(observer));}return app;}
    @Test public void sqliteReopenKeepsStableIdsWhitelistedInputAndPendingCommand()throws Exception{AniloveApplication app=account();SessionEpoch.Ticket ticket=app.foundation().sessions.capture();LocalListingDraftStore store=app.listingDrafts();try{store.executor().submit(()->{LocalListingDraftStore.Draft d=new LocalListingDraftStore.Draft(ticket.uid);d.petId=UUID.randomUUID().toString();d.fields=ListingSchema.empty();d.fields.put("title","Synthetic draft");ListingCommand c=new ListingCommand("create",d.listingId,d.petId,UUID.randomUUID().toString(),map("contentVersion",0L,"controlVersion",0L,"slotVersion",0L,"identityVersion",1L,"authorityVersion",1L,"custodyVersion",0L),d.fields);d.pendingJson=new org.json.JSONObject(map("kind","canonical","command",c.persisted())).toString();store.save(ticket,d);LocalListingDraftStore reopened=new LocalListingDraftStore(app,app.foundation().sessions);LocalListingDraftStore.Draft r=reopened.list(ticket,0).get(0);assertEquals(d.id,r.id);assertEquals(d.listingId,r.listingId);assertEquals(d.fields,r.fields);assertEquals(c.operationId,ListingCommand.restored(nested(LocalListingDraftStore.decode(r.pendingJson),"command")).operationId);reopened.close();}).get(10,TimeUnit.SECONDS);}finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}}
    @Test public void sqliteCapacityAndLogoutRemovalDoNotSilentlyEvictDrafts()throws Exception{AniloveApplication app=account();SessionEpoch.Ticket t=app.foundation().sessions.capture();LocalListingDraftStore s=app.listingDrafts();s.executor().submit(()->{for(int n=0;n<100;n++){LocalListingDraftStore.Draft d=new LocalListingDraftStore.Draft(t.uid);s.save(t,d);}try{s.save(t,new LocalListingDraftStore.Draft(t.uid));fail("capacity must be visible");}catch(IllegalStateException expected){assertEquals("capacity",expected.getMessage());}int count=0;for(int offset=0;offset<100;offset+=20)count+=s.list(t,offset).size();assertEquals(100,count);}).get(15,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());s.executor().submit(()->{try{s.list(t,0);fail("old epoch must be rejected");}catch(IllegalStateException expected){assertEquals("session_changed",expected.getMessage());}try(android.database.Cursor c=s.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM listing_drafts WHERE account_uid=?",new String[]{t.uid})){c.moveToFirst();assertEquals(0,c.getLong(0));}}).get(10,TimeUnit.SECONDS);}
    @Test public void actualEntryRotationAndLogoutKeepNoPrivateHierarchyState()throws Exception{AniloveApplication app=account();try(ActivityScenario<ListingActivity> scenario=ActivityScenario.launch(ListingActivity.class)){AtomicReference<ListingViewModel> m=new AtomicReference<>();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){scenario.onActivity(a->m.set(new ViewModelProvider(a).get(ListingViewModel.class)));if(!m.get().busy)break;Thread.sleep(50);}scenario.onActivity(a->{m.get().newDraft();m.get().changed("Synthetic rotation draft","Private candidate",0);});String id=m.get().draft.listingId;scenario.recreate();scenario.onActivity(a->{ListingViewModel kept=new ViewModelProvider(a).get(ListingViewModel.class);assertSame(m.get(),kept);assertEquals(id,kept.draft.listingId);assertEquals("Synthetic rotation draft",kept.title);assertFalse(a.findViewById(R.id.listing_root).isSaveEnabled());});InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertNull(m.get().draft);assertNull(m.get().pending);assertEquals("",m.get().description);}finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}}
}
