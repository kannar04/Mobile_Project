package com.gmail.manhle240406.project.wiring;

import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.gmail.manhle240406.project.*;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Private app-only screenshots plus aggregate safe-area facts; never exports profile identity. */
@RunWith(AndroidJUnit4.class)
public final class ShellSurfaceAuditDeviceTest {
 @Test public void nativeShellVariantsHaveVisibleSafeContentAndOneBar()throws Exception{
  Bundle args=InstrumentationRegistry.getArguments();String variant=args.getString("variant","portrait");
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertNotNull(FirebaseAuth.getInstance().getCurrentUser());String uid=FirebaseAuth.getInstance().getCurrentUser().getUid();
  File dir=new File(context.getExternalFilesDir(null),"private-ux-audit");assertTrue(dir.mkdirs()||dir.isDirectory());JSONArray facts=new JSONArray();
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{if(variant.contains("landscape"))a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);if(variant.contains("dark"))a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);});
   AtomicBoolean ready=new AtomicBoolean();long until=SystemClock.elapsedRealtime()+25000;
   do{scenario.onActivity(a->ready.set(a.findViewById(R.id.bottom_navigation).getVisibility()==View.VISIBLE&&(!variant.contains("landscape")||a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)));if(!ready.get())SystemClock.sleep(100);}while(!ready.get()&&SystemClock.elapsedRealtime()<until);assertTrue("Variant must settle",ready.get());
   for(String page:new String[]{"home","favorites","chat","notifications","profile"}){
    scenario.onActivity(a->a.navigate(page));InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(300,3000);
    scenario.onActivity(a->{try{View bar=a.findViewById(R.id.bottom_navigation),body=a.findViewById(R.id.main_fragment_container);int[] b=new int[2],c=new int[2];bar.getLocationOnScreen(b);body.getLocationOnScreen(c);
     assertEquals(page,a.destination());assertEquals(View.VISIBLE,bar.getVisibility());assertTrue(body.getHeight()>0);assertTrue("Content must end above the bar",c[1]+body.getHeight()<=b[1]);assertTrue("Bar must fit window",b[1]+bar.getHeight()<=a.getWindow().getDecorView().getHeight());
     facts.put(new JSONObject().put("page",page).put("bodyHeight",body.getHeight()).put("barHeight",bar.getHeight()).put("fontScale",a.getResources().getConfiguration().fontScale).put("dark",(a.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES).put("landscape",a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE));
    }catch(JSONException e){throw new AssertionError(e);}});
    Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(screenshot);try(FileOutputStream out=new FileOutputStream(new File(dir,variant+"-"+page+".png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
   }
  }
  assertNotNull(FirebaseAuth.getInstance().getCurrentUser());assertEquals(uid,FirebaseAuth.getInstance().getCurrentUser().getUid());
  try(FileOutputStream out=new FileOutputStream(new File(dir,variant+"-facts.json"))){out.write(facts.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 }
}
