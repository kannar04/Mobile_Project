package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gmail.manhle240406.project.R;

/** Honest empty state until a real lost-pet provider and evidence receiver are configured. */
public final class LostAndFoundActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lost_and_found);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lost_root), (v, insets) -> {
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        findViewById(R.id.lost_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }
}
