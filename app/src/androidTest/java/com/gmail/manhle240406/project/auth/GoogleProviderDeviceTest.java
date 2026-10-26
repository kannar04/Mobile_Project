package com.gmail.manhle240406.project.auth;
import android.app.UiAutomation;
import android.content.*;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.*;
import com.gmail.manhle240406.project.R;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import java.io.*;
import java.util.Objects;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.action.ViewActions.click;
/** Existing Google UI smoke: no account selected, credentials captured or real session replaced. */
@RunWith(AndroidJUnit4.class)
public final class GoogleProviderDeviceTest {
 @Test public void configuredGoogleButtonOpensProviderAndCancellationKeepsSession()throws Exception{
  Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
  try(ActivityScenario<AuthActivity> screen=ActivityScenario.launch(new Intent(app,AuthActivity.class).putExtra("account",true))){
   onView(isRoot()).perform(new ViewAction(){public org.hamcrest.Matcher<android.view.View> getConstraints(){return isRoot();}public String getDescription(){return "await settled account state";}public void perform(UiController ui,android.view.View view){long end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){com.gmail.manhle240406.project.auth.AuthState state=((com.gmail.manhle240406.project.AniloveApplication)app.getApplicationContext()).auth().state().getValue();if(state!=null&&!state.pending&&!state.setupLoading&&state.verificationKnown)return;if(state!=null&&state.uid==null&&!state.pending&&!state.setupLoading)return;ui.loopMainThreadForAtLeast(100);}throw new AssertionError("Auth startup did not settle");}});
   String prior=FirebaseAuth.getInstance().getUid();JSONObject facts=new JSONObject().put("configured",true).put("existingSessionPreserved",true).put("successfulGoogleSignInVerified",false);
   screen.onActivity(a->assertTrue(new GoogleCredentialAdapter(a,((com.gmail.manhle240406.project.AniloveApplication)a.getApplication()).auth()).available()));
   if(prior==null){onView(withId(R.id.auth_google)).check(matches(isEnabled())).perform(click());UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();boolean provider=false,broader=false;long end=SystemClock.uptimeMillis()+25000;
    while(SystemClock.uptimeMillis()<end){AccessibilityNodeInfo root=automation.getRootInActiveWindow();String owner=root==null||root.getPackageName()==null?"":root.getPackageName().toString();if(root!=null)root.recycle();if(owner.equals("com.google.android.gms")||owner.equals("android")||owner.equals("com.android.credentialmanager")){provider=true;break;}
     if(!broader){try{onView(withText(R.string.auth_choose_account)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());broader=true;}catch(androidx.test.espresso.NoMatchingViewException|androidx.test.espresso.NoMatchingRootException ignored){}}
     SystemClock.sleep(150);
    }
    assertTrue("Configured Google credential provider did not open",provider);facts.put("credentialProviderOpened",true);automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    long finish=SystemClock.uptimeMillis()+10000;while(((com.gmail.manhle240406.project.AniloveApplication)app.getApplicationContext()).auth().busy()&&SystemClock.uptimeMillis()<finish)SystemClock.sleep(100);
    assertTrue("Cancellation must release the provider operation",!((com.gmail.manhle240406.project.AniloveApplication)app.getApplicationContext()).auth().busy());assertTrue("Cancellation must preserve the original session",Objects.equals(prior,FirebaseAuth.getInstance().getUid()));facts.put("cancellationVerified",true);
   }else facts.put("credentialProviderOpened",false).put("blockedByExistingSession",true);
   File out=new File(app.getExternalFilesDir(null),"google-login-evidence");assertTrue(out.mkdirs()||out.isDirectory());try(FileOutputStream f=new FileOutputStream(new File(out,"result.json"))){f.write(facts.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  }
 }
}
