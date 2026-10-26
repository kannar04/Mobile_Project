package com.gmail.manhle240406.project.community;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.google.firebase.auth.FirebaseAuth;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public final class FeedSurfaceAuditDeviceTest {
 private String variant;private File directory;private final JSONArray facts=new JSONArray();
 private void configure(androidx.appcompat.app.AppCompatActivity a){a.setRequestedOrientation(variant.contains("landscape")?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);a.getDelegate().setLocalNightMode(variant.contains("dark")?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);}
 private void waitFor(java.util.function.BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+25000;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);assertTrue(condition.getAsBoolean());}
 private void capture(String name)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(300,4000);Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);try(FileOutputStream out=new FileOutputStream(new File(directory,variant+"-"+name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();facts.put(new JSONObject().put("surface",name).put("variant",variant));}
 @Test public void newFeedLibrariesAndInlinePagesFitPhoneVariants()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();String uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);variant=InstrumentationRegistry.getArguments().getString("variant","portrait");directory=new File(c.getExternalFilesDir(null),"private-feed-audit");assertTrue(directory.mkdirs()||directory.isDirectory());AtomicBoolean ready=new AtomicBoolean();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){s.onActivity(this::configure);waitFor(()->{s.onActivity(a->ready.set(a.findViewById(R.id.home_notifications_btn)!=null));return ready.get();});s.onActivity(a->{FeedViewModel m=new ViewModelProvider(a.getSupportFragmentManager().findFragmentByTag("home")).get(FeedViewModel.class);ready.set(!m.busy&&!m.window.rows().isEmpty());});waitFor(()->{s.onActivity(a->{FeedViewModel m=new ViewModelProvider(a.getSupportFragmentManager().findFragmentByTag("home")).get(FeedViewModel.class);ready.set(!m.busy&&!m.window.rows().isEmpty());});return ready.get();});s.onActivity(a->((RecyclerView)a.findViewById(R.id.home_posts_recycler)).scrollToPosition(1));waitFor(()->{s.onActivity(a->{View root=a.getWindow().getDecorView();ArrayList<View> names=new ArrayList<>();root.findViewsWithText(names,"UX Fixture Author",View.FIND_VIEWS_WITH_TEXT);boolean pixels=false;for(View name:names){View card=(View)name.getParent().getParent();ImageView avatar=card.findViewById(R.id.post_author_avatar);if(avatar!=null&&avatar.getDrawable() instanceof android.graphics.drawable.BitmapDrawable)pixels=true;}ready.set(pixels);});return ready.get();});capture("home");
   s.onActivity(a->a.navigate("favorites"));Thread.sleep(400);capture("liked-posts");
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(R.string.feed_saved_pets)).perform(androidx.test.espresso.action.ViewActions.click());capture("saved-pets");
   s.onActivity(a->a.navigate("tools"));capture("tools");s.onActivity(a->a.discovery("",false));capture("discovery");s.onActivity(a->{assertTrue(a.findViewById(R.id.bottom_navigation).isShown());assertTrue("Discovery needs a scrollable viewport in landscape",a.findViewById(R.id.discovery_recycler).getHeight()>=SocialViews.dp(a,80));});
  }
  try(ActivityScenario<SocialComposerActivity> s=ActivityScenario.launch(SocialComposerActivity.class)){s.onActivity(this::configure);Thread.sleep(400);s.onActivity(a->{((Spinner)a.findViewById(R.id.post_type_picker)).setSelection(1);assertNotNull(a.findViewById(R.id.social_publish_action));});Thread.sleep(200);capture("adoption-composer");}
  assertEquals(uid,FirebaseAuth.getInstance().getUid());try(FileOutputStream out=new FileOutputStream(new File(directory,variant+"-facts.json"))){out.write(facts.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
