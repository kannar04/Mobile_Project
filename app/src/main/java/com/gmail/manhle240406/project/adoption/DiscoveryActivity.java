package com.gmail.manhle240406.project.adoption;
import android.os.Bundle;
/** Compatibility route; the main shell owns discovery and its current-source lifecycle. */
public final class DiscoveryActivity extends androidx.appcompat.app.AppCompatActivity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);com.gmail.manhle240406.project.MainActivity.open(this,"discovery");finish();}
}
