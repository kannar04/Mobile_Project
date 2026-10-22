package com.gmail.manhle240406.project.adoption;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;
import com.gmail.manhle240406.project.community.AdoptionPostCard;
import com.google.android.gms.tasks.Tasks;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Live regression proof that a current Firebase offering can render in a community card. */
@RunWith(AndroidJUnit4.class)
public final class AdoptionListingConsistencyDeviceTest {
 @Test public void currentOfferingRendersAndRebindsInPostCard() throws Exception {
  Map<String,Object> workspace=Tasks.await(AdoptionApi.call("petWorkspace",map("limit",20,"after",null)),35,TimeUnit.SECONDS);Map<String,Object> chosen=null;
  for(Map<String,Object> pet:AdoptionFlowModel.rows(workspace.get("pets")))if(!AdoptionFlowModel.object(pet.get("offering")).isEmpty()){chosen=pet;break;}assertNotNull("Signed-in fixture needs one current offering",chosen);
  String listing=String.valueOf(AdoptionFlowModel.object(chosen.get("offering")).get("listingId")),name=String.valueOf(AdoptionFlowModel.object(chosen.get("identity")).get("displayName"));final android.widget.LinearLayout[] host={null};final AdoptionPostCard[] card={null};
  try(ActivityScenario<AdoptionFlowActivity> scenario=ActivityScenario.launch(AdoptionFlowActivity.class)){long deadline=android.os.SystemClock.uptimeMillis()+35_000;final boolean[] ready={false};do{scenario.onActivity(a->ready[0]=!a.m.busy);if(!ready[0])android.os.SystemClock.sleep(100);}while(!ready[0]&&android.os.SystemClock.uptimeMillis()<deadline);assertTrue(ready[0]);scenario.onActivity(a->{host[0]=new android.widget.LinearLayout(a);host[0].setOrientation(android.widget.LinearLayout.VERTICAL);card[0]=new AdoptionPostCard(a);host[0].addView(card[0]);a.setContentView(host[0]);card[0].bind(map("type","adoption","listingId",listing));});java.util.concurrent.atomic.AtomicReference<String> shown=new java.util.concurrent.atomic.AtomicReference<>("");long cardDeadline=android.os.SystemClock.uptimeMillis()+35_000;do{scenario.onActivity(a->shown.set(String.valueOf(((android.widget.TextView)card[0].getChildAt(0)).getText())));if(!shown.get().contains(name))android.os.SystemClock.sleep(100);}while(!shown.get().contains(name)&&android.os.SystemClock.uptimeMillis()<cardDeadline);assertTrue("Card text: "+shown.get(),shown.get().contains(name));scenario.onActivity(a->{host[0].removeView(card[0]);host[0].addView(card[0]);});cardDeadline=android.os.SystemClock.uptimeMillis()+35_000;do{scenario.onActivity(a->shown.set(String.valueOf(((android.widget.TextView)card[0].getChildAt(0)).getText())));if(!shown.get().contains(name))android.os.SystemClock.sleep(100);}while(!shown.get().contains(name)&&android.os.SystemClock.uptimeMillis()<cardDeadline);assertTrue("Rebound card text: "+shown.get(),shown.get().contains(name));}
 }
}
