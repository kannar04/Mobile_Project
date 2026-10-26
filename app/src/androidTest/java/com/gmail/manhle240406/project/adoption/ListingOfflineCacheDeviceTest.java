package com.gmail.manhle240406.project.adoption;

import static org.junit.Assert.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ListingOfflineCacheDeviceTest {
 private SessionGateway sessions;private SessionEpoch.Ticket ticket;private java.util.function.BiFunction<String,Map<String,Object>,com.google.android.gms.tasks.Task<Map<String,Object>>> original;
 @Before public void setUp(){AniloveApplication app=(AniloveApplication)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();sessions=app.foundation().sessions;ticket=sessions.capture();assertNotNull(ticket.uid);original=AdoptionApi.testTransport;}
 @After public void tearDown(){AdoptionApi.testTransport=original;}
 private static Map<String,Object> row(String id){return AdoptionFlowModel.map("schemaVersion",1L,"listingId",id,"petId",id,"ownerUid","fixture-owner","contentVersion",1L,"publicationVersion",1L,"audience","signed_in","state","visible","firstPublishedAt",new Timestamp(10,0),"updatedAt",new Timestamp(11,0),"title","Previously viewed pet","petName","Milo","nameKey","milo","speciesId","cat","breedId",null,"regionId",null,"regionLabel","Ha Noi","sex","unknown","ageBand","young","offerType","adoption","availability","available","careCues",AdoptionFlowModel.map("source","owner_report","healthState","unknown"),"bundledIllustrationKey","cat","mediaDescriptors",Collections.emptyList());}
 @Test public void managedFailureReturnsEncryptedPreviouslyValidatedPage()throws Exception{
  String id=UUID.randomUUID().toString(),prefix="cache"+id.substring(0,6);DiscoveryQuery query=new DiscoveryQuery(prefix,"available",Collections.emptyMap());
  AdoptionApi.testTransport=(op,input)->Tasks.forResult(AdoptionFlowModel.map("rows",Collections.singletonList(row(id)),"cursor",null));ListingDiscoveryRepository first=new ListingDiscoveryRepository(FirebaseFirestore.getInstance(),sessions);assertEquals(id,Tasks.await(first.page(ticket,query,null),20,TimeUnit.SECONDS).getDocuments().get(0).getId());first.close();
  AdoptionApi.testTransport=(op,input)->Tasks.forException(new IllegalStateException("offline"));ListingDiscoveryRepository offline=new ListingDiscoveryRepository(FirebaseFirestore.getInstance(),sessions);ListingDiscoveryRepository.Page cached=Tasks.await(offline.page(ticket,query,null),20,TimeUnit.SECONDS);assertEquals(1,cached.size());assertEquals("Previously viewed pet",cached.getDocuments().get(0).getString("title"));offline.close();
 }
}
