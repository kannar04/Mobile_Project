package com.gmail.manhle240406.project.notifications;
import android.os.Bundle;import android.content.Intent;import android.provider.Settings;import android.view.*;import android.widget.*;
import androidx.activity.EdgeToEdge;import androidx.appcompat.app.AppCompatActivity;import androidx.appcompat.widget.SwitchCompat;import androidx.core.app.NotificationManagerCompat;
import com.gmail.manhle240406.project.*;import com.gmail.manhle240406.project.community.*;import com.google.android.material.button.MaterialButton;
/** Real inbox and Android channel settings with an explicit device-status summary. */
public final class NotificationSettingsActivity extends AppCompatActivity {
 private SwitchCompat enabled;private TextView feedback;private boolean syncing;
 @Override public void onCreate(Bundle saved){super.onCreate(saved);EdgeToEdge.enable(this);LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.notification_settings,this::finish);LinearLayout content=SocialSurface.content(this,root);
  content.addView(SocialSurface.quiet(this,getString(R.string.feedback_open_inbox),()->MainActivity.open(this,"notifications")));
  enabled=new SwitchCompat(this);enabled.setId(R.id.notification_enabled_switch);enabled.setText(R.string.notifications_enabled);enabled.setMinHeight(SocialViews.dp(this,48));content.addView(enabled);
  feedback=SocialSurface.muted(this,"");feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(feedback);
  enabled.setOnCheckedChangeListener((button,on)->{if(syncing)return;if(on)AdoptionPushService.enable(this,feedback);else{feedback.setText(R.string.notification_status_off);openSystemSettings();}});
  MaterialButton advanced=SocialSurface.quiet(this,getString(R.string.notification_advanced),this::openSystemSettings);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);params.gravity=Gravity.END;int margin=SocialViews.dp(this,16);params.setMargins(margin,margin,margin,margin);root.addView(advanced,params);
 }
 @Override protected void onResume(){super.onResume();if(enabled==null)return;boolean on=NotificationManagerCompat.from(this).areNotificationsEnabled();syncing=true;enabled.setChecked(on);syncing=false;feedback.setText(on?R.string.notification_status_on:R.string.notification_status_off);}
 private void openSystemSettings(){startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));}
}
