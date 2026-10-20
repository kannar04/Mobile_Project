package com.gmail.manhle240406.project.notifications;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Compatibility entry point; primary destinations live in the single main shell. */
public final class InboxActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    com.gmail.manhle240406.project.MainActivity.open(this, "notifications");
    finish();
  }
}
