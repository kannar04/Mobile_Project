package com.gmail.manhle240406.project.adoption;

import android.app.*;import android.content.*;import android.os.*;import androidx.test.core.app.ActivityScenario;import androidx.test.ext.junit.runners.AndroidJUnit4;import androidx.test.platform.app.InstrumentationRegistry;import androidx.test.uiautomator.*;import com.gmail.manhle240406.project.R;import com.gmail.manhle240406.project.notifications.NotificationSettingsActivity;import com.google.firebase.auth.FirebaseAuth;import org.junit.*;import org.junit.runner.RunWith;import java.util.*;
import static org.junit.Assert.*;import static androidx.test.espresso.Espresso.*;import static androidx.test.espresso.action.ViewActions.*;import static androidx.test.espresso.matcher.ViewMatchers.*;

/** Live FCM/Android display checks, separate from the isolated lifecycle transport. */
@RunWith(AndroidJUnit4.class) public final class TwoDeviceNotificationTest {
 private final Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();private String uid;
 @Before public void begin(){uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);AdoptionApi.testTransport=null;}
 @After public void end(){assertEquals("Identity preserved",uid,FirebaseAuth.getInstance().getUid());}
 @Test public void bindInstallation(){((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();try(ActivityScenario<NotificationSettingsActivity> s=ActivityScenario.launch(NotificationSettingsActivity.class)){
  s.onActivity(a->{androidx.appcompat.widget.SwitchCompat enabled=a.findViewById(R.id.notification_enabled_switch);if(!enabled.isChecked())enabled.performClick();else com.gmail.manhle240406.project.notifications.AdoptionPushService.enable(a,new android.widget.TextView(a));});long end=SystemClock.elapsedRealtime()+60000;
  while(SystemClock.elapsedRealtime()<end&&c.getSharedPreferences("adoption_push",0).getString("epoch",null)==null)SystemClock.sleep(200);
  assertNotNull("FCM challenge confirmed",c.getSharedPreferences("adoption_push",0).getString("epoch",null));
 }}
 @Test public void realNotificationsOpenInbox()throws Exception{
  NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);Set<String> texts=new HashSet<>();PendingIntent open=null;long end=SystemClock.elapsedRealtime()+90000;
  do{texts.clear();for(android.service.notification.StatusBarNotification n:manager.getActiveNotifications()){CharSequence text=n.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);if(text!=null)texts.add(text.toString());if(c.getString(R.string.oc4_notification_message).contentEquals(text==null?"":text))open=n.getNotification().contentIntent;}if(texts.contains(c.getString(R.string.oc4_notification_message))&&texts.contains(c.getString(R.string.oc4_notification_like))&&texts.contains(c.getString(R.string.wiring_inbox_comment)))break;SystemClock.sleep(300);}while(SystemClock.elapsedRealtime()<end);
  assertTrue("Message displayed",texts.contains(c.getString(R.string.oc4_notification_message)));assertTrue("Like displayed",texts.contains(c.getString(R.string.oc4_notification_like)));assertTrue("Comment displayed",texts.contains(c.getString(R.string.wiring_inbox_comment)));assertNotNull(open);open.send();UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());assertTrue("Tap opens general inbox",device.wait(Until.hasObject(By.res(c.getPackageName(),"inbox_root")),20000));device.takeScreenshot(new java.io.File(c.getExternalFilesDir(null),"two-device-notification-inbox.png"));
 }
}
