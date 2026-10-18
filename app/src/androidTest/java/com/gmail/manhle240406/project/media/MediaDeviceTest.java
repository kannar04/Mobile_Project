package com.gmail.manhle240406.project.media;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.*;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Real transport plus native UI, preserving the phone account/profile and never publishing as it. */
@RunWith(AndroidJUnit4.class)
public final class MediaDeviceTest {
 private String uid;private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
 @Before public void preserveIdentity(){assertNotNull(FirebaseAuth.getInstance().getCurrentUser());uid=FirebaseAuth.getInstance().getUid();}
 @After public void identityUnchanged(){assertEquals(uid,FirebaseAuth.getInstance().getUid());}
 @Test public void managedImageFetchReturnsDecodablePixels()throws Exception{
  String post=InstrumentationRegistry.getArguments().getString("mediaPost");Assume.assumeTrue(post!=null);
  com.google.firebase.firestore.DocumentSnapshot row=com.google.android.gms.tasks.Tasks.await(com.google.firebase.firestore.FirebaseFirestore.getInstance().document("communityFeed/"+post).get(com.google.firebase.firestore.Source.SERVER),25,java.util.concurrent.TimeUnit.SECONDS);
  java.util.List<String> ids=MediaGrid.ids(row.get("mediaAssetIds"));assertEquals(6,ids.size());byte[] bytes=com.google.android.gms.tasks.Tasks.await(ManagedMediaClient.image(ids.get(0),"thumbnail"),65,java.util.concurrent.TimeUnit.SECONDS);Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);assertNotNull("Actual Firebase bytes must render, not just a placeholder",bitmap);assertTrue(bitmap.getWidth()>0);bitmap.recycle();
 }
 private static void waitUntil(java.util.function.BooleanSupplier done,long timeout)throws Exception{long deadline=SystemClock.elapsedRealtime()+timeout;while(!done.getAsBoolean()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);assertTrue("Expected media state before deadline",done.getAsBoolean());}
 @Test public void fullViewerOpensFourthTileAndReachesEveryImage()throws Exception{
  String post=InstrumentationRegistry.getArguments().getString("mediaPost");Assume.assumeTrue("Requires the isolated public fixture",post!=null);
  Intent open=new Intent(context(),SocialDetailActivity.class).putExtra("post",post);
  try(ActivityScenario<SocialDetailActivity> scenario=ActivityScenario.launch(open)){
   AtomicBoolean loaded=new AtomicBoolean();waitUntil(()->{scenario.onActivity(a->{ArrayList<View> found=new ArrayList<>();a.getWindow().getDecorView().findViewsWithText(found,"+2",View.FIND_VIEWS_WITH_TEXT);loaded.set(!found.isEmpty());});return loaded.get();},25000);
   onView(withText("+2")).perform(scrollTo(),click());onView(withText(context().getString(R.string.media_photo_position,4,6))).check(matches(isDisplayed()));onView(withText(R.string.media_next)).perform(click());onView(withText(context().getString(R.string.media_photo_position,5,6))).check(matches(isDisplayed()));onView(withText(R.string.media_next)).perform(click());onView(withText(context().getString(R.string.media_photo_position,6,6))).check(matches(isDisplayed()));onView(withText(R.string.media_next)).check(matches(org.hamcrest.Matchers.not(isEnabled())));androidx.test.espresso.Espresso.pressBack();onView(withText("+2")).check(matches(isDisplayed()));
  }
 }
 @Test public void realPrivateUploadRetainsOrderAcrossRotationThenRemovesWithoutPublishing()throws Exception{
  List<File> local=new ArrayList<>();for(int i=0;i<2;i++){File file=new File(context().getCacheDir(),"native-media-fixture-"+i+".jpg");Bitmap b=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888);b.eraseColor(i==0?0xffdb9a42:0xff326b50);try(FileOutputStream out=new FileOutputStream(file)){assertTrue(b.compress(Bitmap.CompressFormat.JPEG,85,out));}b.recycle();local.add(file);}
  try(ActivityScenario<SocialComposerActivity> scenario=ActivityScenario.launch(SocialComposerActivity.class)){
   AtomicReference<MediaDraftViewModel> model=new AtomicReference<>();scenario.onActivity(a->{MediaDraftViewModel m=new ViewModelProvider(a).get(MediaDraftViewModel.class);model.set(m);m.add(Arrays.asList(Uri.fromFile(local.get(0)),Uri.fromFile(local.get(1))));});
   AtomicBoolean ready=new AtomicBoolean();waitUntil(()->{scenario.onActivity(a->ready.set(model.get().entries.size()==2&&model.get().ready()));return ready.get();},90000);
   List<String> ids=new ArrayList<>(model.get().ids());scenario.recreate();scenario.onActivity(a->assertEquals(ids,new ViewModelProvider(a).get(MediaDraftViewModel.class).ids()));
   JSONObject fixture=new JSONObject();fixture.put("uid",uid);JSONArray assets=new JSONArray();scenario.onActivity(a->{try{for(MediaDraftViewModel.Entry e:new ArrayList<>(model.get().entries)){assets.put(new JSONObject().put("assetId",e.assetId).put("operationId",e.operationId));model.get().remove(e);}}catch(JSONException ex){throw new AssertionError(ex);}});fixture.put("assets",assets);try(FileOutputStream out=new FileOutputStream(new File(context().getNoBackupFilesDir(),"private-media-native.json"))){out.write(fixture.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
   scenario.onActivity(a->assertTrue(model.get().entries.isEmpty()));assertEquals(uid,FirebaseAuth.getInstance().getUid());
  }finally{for(File f:local)f.delete();}
 }
 @Test public void profilePhotoControlsAreReachableWithoutChangingProfile()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicBoolean ready=new AtomicBoolean();waitUntil(()->{scenario.onActivity(a->ready.set(a.findViewById(R.id.bottom_navigation)!=null&&a.findViewById(R.id.home_notifications_btn)!=null));return ready.get();},20000);
   onView(withId(R.id.nav_profile)).perform(click());onView(withText(R.string.media_change_avatar)).perform(scrollTo()).check(matches(isDisplayed()));onView(withId(R.id.bottom_navigation)).check(matches(isDisplayed()));
  }
 }
 @Test public void homePhotoActionOpensNativePickerAndCancelKeepsEmptyDraft()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   AtomicBoolean ready=new AtomicBoolean();waitUntil(()->{scenario.onActivity(a->ready.set(a.findViewById(R.id.home_post_photo_btn)!=null&&a.findViewById(R.id.home_post_photo_btn).isShown()));return ready.get();},20000);
   onView(withId(R.id.home_post_photo_btn)).perform(click());androidx.test.uiautomator.UiDevice device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());waitUntil(()->{String pkg=device.getCurrentPackageName();return pkg!=null&&(pkg.contains("providers.media")||pkg.contains("photopicker"));},20000);device.pressBack();onView(withId(R.id.social_body_input)).check(matches(isDisplayed()));androidx.test.espresso.Espresso.pressBack();
  }
 }
}
