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
 private void idle(ActivityScenario<? extends AdoptionFlowActivity> scenario){settled(scenario);scenario.onActivity(a->assertEquals("",a.m.error));}
 private void settled(ActivityScenario<? extends AdoptionFlowActivity> scenario){long until=SystemClock.uptimeMillis()+30000;AtomicBoolean ready=new AtomicBoolean();do{scenario.onActivity(a->ready.set(!a.m.busy));if(ready.get())return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);fail("Managed demo review timed out");}
 private void clickTag(String tag){if(tag.startsWith("adoption_pet_")||tag.startsWith("adoption_offer_"))onView(withTagValue(equalTo(tag))).perform(scrollTo(),click());else onView(withTagValue(equalTo(tag))).perform(click());}
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
  }
   Map<String,Object> care=call("care",map("petId",pet,"expectedCareVersion",0,"expectedIdentityVersion",1,"expectedCustodyVersion",0,"payload",map("healthState","unknown","color","","behaviorNotes","","careNeeds","Synthetic care discussion","sterilization","unknown","vaccinations",new ArrayList<>()),"medicationNotes","Synthetic private note"));
   boolean preview="true".equals(InstrumentationRegistry.getArguments().getString("testAdoptionPreviewShow"));
   if(preview){try(ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> owner=ActivityScenario.launch(com.gmail.manhle240406.project.listing.ListingActivity.class)){
    idle(owner);clickTag("adoption_new");owner.onActivity(a->a.m.draftId=listing);clickTag("adoption_pet_"+pet);idle(owner);
    clickTag("adoption_save_care");idle(owner);
    onView(withTagValue(equalTo("ad_offer_title"))).perform(scrollTo(),replaceText("Synthetic self-review QA offer"),closeSoftKeyboard());
    onView(withTagValue(equalTo("ad_description"))).perform(scrollTo(),replaceText("Disposable course-test record. No real animal."),closeSoftKeyboard());
    clickTag("adoption_preview");onView(withText("Synthetic self-review QA cat")).check(matches(isDisplayed()));onView(withText("Synthetic private note")).check(doesNotExist());
    owner.onActivity(a->{assertEquals(3,a.m.step);assertNull(a.m.listingId);});
    clickTag("adoption_submit");idle(owner);owner.onActivity(a->{assertEquals("offerStatus",a.m.page);assertEquals(listing,a.m.listingId);assertEquals("listing_submitted",a.m.notice);});
    onView(withTagValue(equalTo("adoption_feedback"))).check(matches(withText(R.string.ad_listing_sent_feedback)));
   }}else{
    call("saveOffer",map("listingId",listing,"petId",pet,"expectedContentVersion",0,"expectedControlVersion",0,"careVersion",1,"careDigest",care.get("careDigest"),"fields",map("title","Synthetic self-review QA offer","description","Disposable course-test record. No real animal.","regionCode","north","offerType","adoption","mediaRefs",new ArrayList<>())));
    call("submitReview",map("listingId",listing,"contentVersion",1,"requestVersion",0));
   }
  try(ActivityScenario<ListingReviewActivity> scenario=ActivityScenario.launch(ListingReviewActivity.class)){
   idle(scenario);decide(scenario,"adoption_review_offer_"+listing,true);
   Map<String,Object> offer=read("offer",map("listingId",listing));assertEquals("hidden",object(offer.get("listing")).get("visibility"));assertEquals(1,number(object(offer.get("publication")).get("approvedContentVersion")));String decision=String.valueOf(object(offer.get("publication")).get("decisionId"));var audit=Tasks.await(FirebaseFirestore.getInstance().document("adoptionReviewAudit/"+decision).get(),20,TimeUnit.SECONDS);assertEquals("demo_self_review",audit.getString("reviewMode"));assertEquals(uid,audit.getString("ownerUid"));assertEquals(uid,audit.getString("reviewerUid"));
  }
  if("true".equals(InstrumentationRegistry.getArguments().getString("testAdoptionPreviewShow"))){try(ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> owner=ActivityScenario.launch(com.gmail.manhle240406.project.listing.ListingActivity.class)){
   idle(owner);clickTag("adoption_offer_"+listing);idle(owner);clickTag("adoption_show");settled(owner);
   boolean released="true".equals(InstrumentationRegistry.getArguments().getString("testAdoptionRelease"));
   owner.onActivity(a->{if(released)assertEquals("",a.m.error);else assertTrue("Closed rollout must deny Show",a.m.error.contains("publication_not_ready"));assertFalse(a.m.uncertain());});
   if(!released)onView(withTagValue(equalTo("adoption_feedback"))).check(matches(isDisplayed()));
   assertEquals(released?"shown":"hidden",object(read("offer",map("listingId",listing)).get("listing")).get("visibility"));
  }}
  if("true".equals(InstrumentationRegistry.getArguments().getString("testAdoptionRelease")))verifyRelease(listing,pet,file,expected);
  assertEquals(uid,FirebaseAuth.getInstance().getUid());
 }

 private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.graphics.Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);java.io.File dir=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"adoption-release-audit");assertTrue(dir.mkdirs()||dir.isDirectory());try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}}
 private void verifyRelease(String listing,String pet,java.io.File fixture,String hash)throws Exception{
  assertEquals(false,read("publicDetail",map("listingId",listing)).get("applicationsEnabled"));
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  String readerJson=new String(java.nio.file.Files.readAllBytes(new java.io.File(context.getExternalFilesDir(null),"adoption-release-reader.json").toPath()),java.nio.charset.StandardCharsets.UTF_8);org.json.JSONObject credentials=new org.json.JSONObject(readerJson);
  com.google.firebase.FirebaseApp app=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().getOptions(),"synthetic-adoption-reader");
  try{com.google.firebase.auth.FirebaseAuth auth=com.google.firebase.auth.FirebaseAuth.getInstance(app);Tasks.await(auth.signInWithEmailAndPassword(credentials.getString("email"),credentials.getString("password")),30,TimeUnit.SECONDS);assertFalse(auth.getCurrentUser().isEmailVerified());
   Map<?,?> response=(Map<?,?>)Tasks.await(com.google.firebase.functions.FirebaseFunctions.getInstance(app,"asia-east2").getHttpsCallable("adoptionOperations").call(map("operation","publicDetail","input",map("listingId",listing))),30,TimeUnit.SECONDS).getData();assertEquals(false,response.get("applicationsEnabled"));assertEquals("Synthetic self-review QA offer",((Map<?,?>)response.get("detail")).get("title"));
   try{Tasks.await(FirebaseFirestore.getInstance(app).document("listingDiscovery/"+listing).get(com.google.firebase.firestore.Source.SERVER),20,TimeUnit.SECONDS);fail("Direct projection must remain denied");}catch(java.util.concurrent.ExecutionException denied){assertTrue(denied.getCause() instanceof com.google.firebase.firestore.FirebaseFirestoreException);assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,((com.google.firebase.firestore.FirebaseFirestoreException)denied.getCause()).getCode());}
  }finally{app.delete();}
  try(ActivityScenario<com.gmail.manhle240406.project.MainActivity> main=ActivityScenario.launch(com.gmail.manhle240406.project.MainActivity.class)){
   main.onActivity(a->a.navigate("discovery"));androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Synthetic self-review QA cat")),30000));capture("discovery");
  }
  try(ActivityScenario<AdoptionFlowActivity> detail=ActivityScenario.launch(new android.content.Intent(context,AdoptionFlowActivity.class).putExtra("mode","detail").putExtra("listing",listing))){idle(detail);onView(withText(R.string.ad_browse_only)).check(matches(isDisplayed()));onView(withTagValue(equalTo("adoption_apply"))).check(doesNotExist());capture("detail");}
  final String[] post={null};try(ActivityScenario<com.gmail.manhle240406.project.community.SocialComposerActivity> composer=ActivityScenario.launch(com.gmail.manhle240406.project.community.SocialComposerActivity.class)){
   composer.onActivity(a->post[0]=new androidx.lifecycle.ViewModelProvider(a).get(com.gmail.manhle240406.project.community.DailyViewModel.class).postId);
   try(java.io.FileOutputStream out=new java.io.FileOutputStream(fixture)){out.write(new org.json.JSONObject(map("petId",pet,"listingId",listing,"postId",post[0],"ownerHash",hash)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   onView(withId(R.id.post_type_picker)).perform(scrollTo(),click());androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.is(context.getString(R.string.feed_adoption))).perform(click());onView(withId(R.id.adoption_listing_action)).perform(click());
   androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Synthetic self-review QA cat")),30000));capture("picker");onView(withText("Synthetic self-review QA cat")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
   AtomicBoolean selected=new AtomicBoolean();long selectionDeadline=SystemClock.uptimeMillis()+30000;do{composer.onActivity(a->selected.set(new androidx.lifecycle.ViewModelProvider(a).get(com.gmail.manhle240406.project.community.DailyViewModel.class).selectedListing!=null));if(!selected.get())SystemClock.sleep(100);}while(!selected.get()&&SystemClock.uptimeMillis()<selectionDeadline);assertTrue("Picker must finish its current-source read",selected.get());
   onView(withId(R.id.social_body_input)).perform(scrollTo(),replaceText("Synthetic course adoption-share test. No real animal."),closeSoftKeyboard());onView(withId(R.id.social_publish_action)).perform(scrollTo(),click());onView(withText(R.string.social_publish)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
   long until=SystemClock.uptimeMillis()+30000;while(composer.getState()!=androidx.lifecycle.Lifecycle.State.DESTROYED&&SystemClock.uptimeMillis()<until)SystemClock.sleep(100);assertEquals("Acknowledged adoption post must close composer",androidx.lifecycle.Lifecycle.State.DESTROYED,composer.getState());
  }
  var feed=Tasks.await(FirebaseFirestore.getInstance().document("communityFeed/"+post[0]).get(com.google.firebase.firestore.Source.SERVER),20,TimeUnit.SECONDS);assertEquals("adoption",feed.getString("type"));assertEquals(listing,feed.getString("listingId"));
  try(ActivityScenario<com.gmail.manhle240406.project.community.SocialDetailActivity> social=ActivityScenario.launch(new android.content.Intent(context,com.gmail.manhle240406.project.community.SocialDetailActivity.class).putExtra("post",post[0]))){
   androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());String selected=context.getString(R.string.feed_listing_selected,"Synthetic self-review QA cat");assertTrue("Published social card must resolve through managed reads",device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.textContains(selected)),30000));
   Map<String,Object> l=object(read("offer",map("listingId",listing)).get("listing"));call("hide",map("listingId",listing,"expectedControlVersion",l.get("controlVersion")));assertTrue("Visible linked card must remove hidden source within foreground refresh",device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text(context.getString(R.string.feed_listing_unavailable))),35000));
  }
  try{read("publicDetail",map("listingId",listing));fail("Hidden source must not remain readable");}catch(java.util.concurrent.ExecutionException expected){}
  Map<String,Object> window=read("discoveryWindow",map("ids",Arrays.asList(listing)));assertTrue(rows(window.get("rows")).isEmpty());
 }

 @Test public void namedDemoPolicyRecognizedReadOnly()throws Exception{assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("runLiveDemoSelfReview")));assertNull(AdoptionApi.testTransport);String uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(uid.getBytes(java.nio.charset.StandardCharsets.UTF_8)))hash.append(String.format("%02x",b));assertEquals(InstrumentationRegistry.getArguments().getString("reviewerHash"),hash.toString());assertEquals(true,read("workspace",map()).get("selfReviewAllowed"));Map<String,Object> queue=read("reviewQueue",map());assertEquals(true,queue.get("selfReviewAllowed"));boolean own=false;for(Map<String,Object> claim:rows(queue.get("claims")))if(uid.equals(claim.get("claimantUid"))){own=true;assertEquals("demo_self_review",read("claimCandidate",map("petId",claim.get("petId"),"ownerUid",uid)).get("reviewMode"));}assertTrue("Current user's pending declaration must be visible in their demo queue",own);assertEquals(uid,FirebaseAuth.getInstance().getUid());}
}
