package com.gmail.manhle240406.project.wiring;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.auth.*;
import com.gmail.manhle240406.project.profile.*;
import com.gmail.manhle240406.project.community.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;

/** Rendering audit only. Direct launches deliberately do NOT certify operation-sequence reachability. */
@RunWith(AndroidJUnit4.class)
public final class DeviceSurfaceAuditTest {
 @BeforeClass public static void signedInFixture()throws Exception{NativeFlowJourneyTest.session();}
 @AfterClass public static void cleanupFixture()throws Exception{NativeFlowJourneyTest.cleanup();}
 private File folder(){File out=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"flow-surfaces");assertTrue(out.mkdirs()||out.isDirectory());return out;}
 private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(300,5000);Bitmap frame=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(frame);try(FileOutputStream out=new FileOutputStream(new File(folder(),name+".png"))){frame.compress(Bitmap.CompressFormat.PNG,100,out);}frame.recycle();}
 private void ready(java.util.function.Predicate<View> condition){onView(isRoot()).perform(new ViewAction(){public org.hamcrest.Matcher<View> getConstraints(){return isRoot();}public String getDescription(){return "await rendered surface";}public void perform(UiController ui,View root){long end=SystemClock.uptimeMillis()+15000;while(SystemClock.uptimeMillis()<end){if(condition.test(root))return;ui.loopMainThreadForAtLeast(100);}throw new AssertionError("surface not ready");}});}
 private boolean text(View v,String part){if(v instanceof TextView&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)if(text(group.getChildAt(i),part))return true;}return false;}
 private void surface(Class<? extends Activity> type,String name)throws Exception{try(ActivityScenario<?> screen=ActivityScenario.launch(type)){ready(v->v.isLaidOut());capture(name);}}
 @Test public void changedNativeSurfacesRenderWithoutInputInjection()throws Exception{
  surface(FlowHubActivity.class,"explore");surface(PendingCommunityActivity.class,"pending");surface(GuestStoriesActivity.class,"guest-paused");
  for(int group=0;group<FlowRoutes.GROUPS.length;group++)try(ActivityScenario<FlowHubActivity> screen=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),FlowHubActivity.class).putExtra("group",group))){ready(v->v.isLaidOut());capture("group-"+group);}
  surface(PasswordChangeActivity.class,"password");surface(AccountDeletionActivity.class,"delete");surface(DiscoveryActivity.class,"discovery");surface(FavoritesActivity.class,"favorites");surface(EnhancedSettingsActivity.class,"settings");surface(SocialComposerActivity.class,"composer");
 }
 @Test public void liveSeededStoryRendersAndKeepsBundledContentOnRotation()throws Exception{
  Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SocialDetailActivity.class).putExtra("post","cb2de5c3-c41d-4341-ab4a-afa9b9751a6a");
  try(ActivityScenario<SocialDetailActivity> screen=ActivityScenario.launch(intent)){
   ready(v->text(v,"[DEMO"));capture("story-portrait");
   screen.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));ready(v->v.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE&&text(v,"[DEMO"));capture("story-landscape");
   screen.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
  }
 }
 @Test public void androidNormalizerNoLongerCrashes(){assertEquals("meo do",DiscoveryQuery.normalize(" M\u00e8o\u00a0\u0110\u1ecf ",32));assertNotNull(new DiscoveryQuery("","available",java.util.Collections.emptyMap()));}
 @Test public void clearingFiltersKeepsTheSearchAndRotationState()throws Exception{
  try(ActivityScenario<DiscoveryActivity> screen=ActivityScenario.launch(DiscoveryActivity.class)){
   onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.discovery_search)).perform(androidx.test.espresso.action.ViewActions.replaceText("Bong"),androidx.test.espresso.action.ViewActions.pressImeActionButton(),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
   onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.flow_clear_filters)).perform(androidx.test.espresso.action.ViewActions.click());
   onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.discovery_search)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.withText("Bong")));
   screen.recreate();onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.discovery_search)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.withText("Bong")));
  }
 }
 @Test public void profileDoesNotInventAnUnapprovedVerificationBadge()throws Exception{
  try(ActivityScenario<ProfileActivity> screen=ActivityScenario.launch(ProfileActivity.class)){
   ready(v->v.isLaidOut());
   onView(androidx.test.espresso.matcher.ViewMatchers.withText("\u0110\u00e3 x\u00e1c minh")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
   screen.onActivity(a->{com.google.android.material.button.MaterialButton edit=a.findViewById(R.id.profile_edit);int background=edit.getBackgroundTintList().getDefaultColor();int foreground=edit.getTextColors().getDefaultColor();assertTrue("Primary text contrast",androidx.core.graphics.ColorUtils.calculateContrast(foreground,background)>=4.5);});
   capture("profile-without-trust-badge");
  }
 }
 @Test public void composerIsReachableFromFeedAndKeepsDraftOnRotation()throws Exception{
  try(ActivityScenario<CommunityFeedActivity> screen=ActivityScenario.launch(CommunityFeedActivity.class)){
   onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.social_compose)).perform(androidx.test.espresso.action.ViewActions.click());
   onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.social_body_input)).perform(androidx.test.espresso.action.ViewActions.scrollTo(),androidx.test.espresso.action.ViewActions.replaceText("Synthetic unsent story draft"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
   final Activity[] composer={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))if(a instanceof SocialComposerActivity)composer[0]=a;});
   assertNotNull(composer[0]);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->composer[0].recreate());
   onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.social_body_input)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.withText("Synthetic unsent story draft")));
   capture("composer-unsent-draft");
   InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{for(Activity a:androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))a.finish();});
  }
 }
 @Test public void commentDraftAndContextualReportReflectCurrentIntake()throws Exception{
  Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),SocialDetailActivity.class).putExtra("post","cb2de5c3-c41d-4341-ab4a-afa9b9751a6a");
  try(ActivityScenario<SocialDetailActivity> screen=ActivityScenario.launch(intent)){
   ready(v->text(v,"[DEMO"));
   onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.social_comment_input)).perform(androidx.test.espresso.action.ViewActions.replaceText("Synthetic unsent comment"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
   screen.recreate();onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.social_comment_input)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.withText("Synthetic unsent comment")));
   ready(v->text(v,"[DEMO"));
   onView(androidx.test.espresso.matcher.ViewMatchers.withContentDescription(R.string.social_menu)).perform(androidx.test.espresso.action.ViewActions.click());
   onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.report_title)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
   Boolean intake=com.google.android.gms.tasks.Tasks.await(com.google.firebase.firestore.FirebaseFirestore.getInstance().document("system/reportPolicy").get(com.google.firebase.firestore.Source.SERVER),20,java.util.concurrent.TimeUnit.SECONDS).getBoolean("intakeEnabled");assertEquals(Boolean.TRUE,intake);
   onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.report_submit)).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isEnabled()));
   onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.flow_report_paused)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
   screen.onActivity(a->{ReportSheet sheet=(ReportSheet)a.getSupportFragmentManager().findFragmentByTag("report");assertNotNull(sheet);android.view.Window window=sheet.getDialog().getWindow();boolean dark=(a.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;assertEquals(!dark,androidx.core.view.WindowCompat.getInsetsController(window,window.getDecorView()).isAppearanceLightNavigationBars());});
   capture("report-intake-disabled");
  }
 }
}
