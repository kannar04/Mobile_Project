package com.gmail.manhle240406.project.notifications;
import android.os.Bundle;
import android.content.Intent;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.LinearLayout;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.*;
/** Real inbox and Android channel settings; opening settings never claims push delivery. */
public final class NotificationSettingsActivity extends AppCompatActivity {
 @Override public void onCreate(Bundle saved){super.onCreate(saved);EdgeToEdge.enable(this);LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.notification_settings,this::finish);LinearLayout content=SocialSurface.content(this,root);
  content.addView(SocialViews.text(this,getString(R.string.feedback_inbox_settings),true));content.addView(SocialSurface.muted(this,getString(R.string.feedback_inbox_settings_help)));
  content.addView(SocialSurface.quiet(this,getString(R.string.feedback_open_inbox),()->MainActivity.open(this,"notifications")));
  content.addView(SocialSurface.quiet(this,getString(R.string.feedback_device_notifications),()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName()))));
 }
}
