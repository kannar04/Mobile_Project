package com.gmail.manhle240406.project.adoption;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.gmail.manhle240406.project.R;
import org.junit.Test;import org.junit.runner.RunWith;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;import static org.junit.Assume.assumeTrue;
import static org.hamcrest.Matchers.equalTo;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;

/** Real named actor; decisions target only newly created synthetic records, never the user's pet. */
@RunWith(AndroidJUnit4.class) public final class DemoSelfReviewLiveDeviceTest {
 private Map<String,Object> call(String op,Map<String,Object> values)throws Exception{Map<String,Object> input=new HashMap<>(values);input.put("operationId",UUID.randomUUID().toString());return Tasks.await(AdoptionApi.call(op,input),30,TimeUnit.SECONDS);}
 private Map<String,Object> read(String op,Map<String,Object> values)throws Exception{return Tasks.await(AdoptionApi.call(op,values),30,TimeUnit.SECONDS);}
 private void idle(ActivityScenario<ListingReviewActivity> scenario){long until=SystemClock.uptimeMillis()+30000;AtomicBoolean ready=new AtomicBoolean();do{scenario.onActivity(a->ready.set(!a.m.busy));if(ready.get()){scenario.onActivity(a->assertEquals("",a.m.error));return;}SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);fail("Managed demo review timed out");}
 private void decide(ActivityScenario<ListingReviewActivity> scenario,String tag,boolean listing){onView(withTagValue(equalTo(tag))).perform(scrollTo(),click());idle(scenario);if(listing){onView(withTagValue(equalTo("adoption_assign"))).perform(click());idle(scenario);}onView(withTagValue(equalTo("ad_review_reason"))).perform(scrollTo(),replaceText("Disposable synthetic own review for course-demo verification."),closeSoftKeyboard());onView(withTagValue(equalTo("adoption_approve"))).perform(click());onView(withText(R.string.ad_demo_review_confirmation)).check(matches(isDisplayed()));onView(withText(R.string.ad_confirm_demo_approve)).perform(click());idle(scenario);}
 @Test public void namedDemoAccountReviewsSyntheticOwnClaimAndOfferButNeverPublishes()throws Exception{
  assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runLiveDemoSelfReview")));assertNull(AdoptionApi.testTransport);String uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);
  String expected=InstrumentationRegistry.getArguments().getString("reviewerHash");assertNotNull(expected);StringBuilder actual=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8)))actual.append(String.format("%02x",b));assertEquals(expected,actual.toString());
  assertEquals(true,read("workspace",map()).get("selfReviewAllowed"));String pet=UUID.randomUUID().toString(),listing=UUID.randomUUID().toString();java.io.File file=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"demo-self-fixture.json");assertFalse("Clean the prior exact synthetic fixture before another live test",file.exists());try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(new org.json.JSONObject(map("petId",pet,"listingId",listing,"ownerHash",expected)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  call("register",map("petId",pet,"identity",map("displayName","Synthetic self-review QA cat","species","cat","breed","","sex","unknown","ageMonths",null,"ageObservedAt",null,"marks","","marksUnknown",true,"illustrationKey","cat"),"claim",map("basis","claimed_owner","reportedCare",false,"explanation","Disposable synthetic record. No real animal or ownership assertion.")));
  call("submitClaim",map("petId",pet,"expectedClaimVersion",1,"expectedIdentityVersion",1));
  try(ActivityScenario<ListingReviewActivity> scenario=ActivityScenario.launch(ListingReviewActivity.class)){
   idle(scenario);decide(scenario,"adoption_review_claim_"+pet,false);
   Map<String,Object> detail=read("pet",map("petId",pet));assertEquals("accepted_for_demo",object(detail.get("claim")).get("reviewState"));assertEquals(0,number(object(detail.get("pet")).get("custodyVersion")));String claimId=String.valueOf(object(detail.get("authority")).get("decisionId"));assertEquals("demo_self_review",Tasks.await(FirebaseFirestore.getInstance().document("adoptionReviewAudit/"+claimId).get(),20,TimeUnit.SECONDS).getString("reviewMode"));
   Map<String,Object> care=call("care",map("petId",pet,"expectedCareVersion",0,"expectedIdentityVersion",1,"expectedCustodyVersion",0,"payload",map("healthState","unknown","color","","behaviorNotes","","careNeeds","Synthetic care discussion","sterilization","unknown","vaccinations",new ArrayList<>()),"medicationNotes","Synthetic private note"));
   call("saveOffer",map("listingId",listing,"petId",pet,"expectedContentVersion",0,"expectedControlVersion",0,"careVersion",1,"careDigest",care.get("careDigest"),"fields",map("title","Synthetic self-review QA offer","description","Disposable course-test record. No real animal.","regionCode","north","offerType","adoption","mediaRefs",new ArrayList<>())));
   call("submitReview",map("listingId",listing,"contentVersion",1,"requestVersion",0));scenario.onActivity(a->a.m.load("reviewQueue","reviewQueue",map()));idle(scenario);decide(scenario,"adoption_review_offer_"+listing,true);
   Map<String,Object> offer=read("offer",map("listingId",listing));assertEquals("hidden",object(offer.get("listing")).get("visibility"));assertEquals(1,number(object(offer.get("publication")).get("approvedContentVersion")));String decision=String.valueOf(object(offer.get("publication")).get("decisionId"));var audit=Tasks.await(FirebaseFirestore.getInstance().document("adoptionReviewAudit/"+decision).get(),20,TimeUnit.SECONDS);assertEquals("demo_self_review",audit.getString("reviewMode"));assertEquals(uid,audit.getString("ownerUid"));assertEquals(uid,audit.getString("reviewerUid"));
  }
  assertEquals(uid,FirebaseAuth.getInstance().getUid());
 }

 @Test public void namedDemoPolicyRecognizedReadOnly()throws Exception{assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runLiveDemoSelfReview")));assertNull(AdoptionApi.testTransport);String uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b));assertEquals(InstrumentationRegistry.getArguments().getString("reviewerHash"),hash.toString());assertEquals(true,read("workspace",map()).get("selfReviewAllowed"));Map<String,Object> queue=read("reviewQueue",map());assertEquals(true,queue.get("selfReviewAllowed"));boolean own=false;for(Map<String,Object> claim:rows(queue.get("claims")))if(uid.equals(claim.get("claimantUid"))){own=true;assertEquals("demo_self_review",read("claimCandidate",map("petId",claim.get("petId"),"ownerUid",uid)).get("reviewMode"));}assertTrue("Current user's pending declaration must be visible in their demo queue",own);assertEquals(uid,FirebaseAuth.getInstance().getUid());}
}
