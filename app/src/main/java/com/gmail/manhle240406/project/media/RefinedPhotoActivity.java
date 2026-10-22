package com.gmail.manhle240406.project.media;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gmail.manhle240406.project.R;

public class RefinedPhotoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.gmail.manhle240406.project.wiring.SparkCapabilities.uploadsEnabled()) { com.gmail.manhle240406.project.wiring.UnavailableScreen.show(this, com.gmail.manhle240406.project.R.string.wiring_uploads_paused); return; }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_refined_photo);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.refined_photo_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.refined_photo_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_pick_more_photos).setOnClickListener(v -> {
            Toast.makeText(this, "Đã mở trình chọn ảnh", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
