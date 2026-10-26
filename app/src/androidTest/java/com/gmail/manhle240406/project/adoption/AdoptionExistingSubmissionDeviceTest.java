package com.gmail.manhle240406.project.adoption;

import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static org.hamcrest.Matchers.equalTo;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;
import com.gmail.manhle240406.project.R;

/** Reads the user's existing pending declaration. No writes, identity changes or screenshots. */
@RunWith(AndroidJUnit4.class)
public final class AdoptionExistingSubmissionDeviceTest {
 private void idle(ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario){long until=SystemClock.uptimeMillis()+25000;AtomicBoolean ready=new AtomicBoolean();do{scenario.onActivity(a->ready.set(!a.m.busy));if(ready.get())return;SystemClock.sleep(100);}while(SystemClock.uptimeMillis()<until);fail("Read-only adoption response timed out");}
 @Test public void existingPendingDeclarationUsesCurrentServerReviewMode()throws Exception{
  assertNull(AdoptionApi.testTransport);String uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);
  Map<String,Object> workspace=Tasks.await(AdoptionApi.call("workspace",map()),25,TimeUnit.SECONDS);assumeTrue(workspace.get("reviewer") instanceof java.util.List&&!((java.util.List<?>)workspace.get("reviewer")).isEmpty());String selected=null;
  for(Map<String,Object> pet:rows(workspace.get("pets"))){String id=String.valueOf(pet.get("petId"));Map<String,Object> detail=Tasks.await(AdoptionApi.call("pet",map("petId",id)),25,TimeUnit.SECONDS);if("submitted".equals(object(detail.get("claim")).get("reviewState"))){selected=id;break;}}
  assumeTrue("An existing pending declaration is required for this read-only check",selected!=null);String id=selected;
  Map<String,Object> queue=Tasks.await(AdoptionApi.call("reviewQueue",map()),25,TimeUnit.SECONDS);boolean ownAllowed=Boolean.TRUE.equals(workspace.get("selfReviewAllowed"));boolean ownFound=false;for(Map<String,Object> claim:rows(queue.get("claims"))){if(uid.equals(claim.get("claimantUid")))ownFound=true;if(!ownAllowed)assertNotEquals(uid,claim.get("claimantUid"));}if(ownAllowed)assertTrue(ownFound);
  try(ActivityScenario<com.gmail.manhle240406.project.listing.ListingActivity> scenario=ActivityScenario.launch(com.gmail.manhle240406.project.listing.ListingActivity.class)){
   idle(scenario);onView(withTagValue(equalTo("adoption_new"))).perform(click());onView(withTagValue(equalTo("adoption_pet_"+id))).perform(scrollTo(),click());idle(scenario);
   onView(withText(R.string.ad_claim_waiting)).perform(scrollTo()).check(matches(isDisplayed()));onView(withText(ownAllowed?R.string.ad_demo_self_available:R.string.ad_claim_self_review)).perform(scrollTo()).check(matches(isDisplayed()));onView(withTagValue(equalTo("adoption_submit_claim"))).check(doesNotExist());
   scenario.onActivity(a->assertEquals("",a.m.error));
  }
  assertEquals(uid,FirebaseAuth.getInstance().getUid());
 }
}
