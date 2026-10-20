package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Compatibility entry point; primary destinations live in the single main shell. */
public final class FavoritesActivity extends AppCompatActivity {
  @Override
  protected void onCreate(Bundle saved) {
    super.onCreate(saved);
    com.gmail.manhle240406.project.MainActivity.open(this, "favorites");
    finish();
  }
}
