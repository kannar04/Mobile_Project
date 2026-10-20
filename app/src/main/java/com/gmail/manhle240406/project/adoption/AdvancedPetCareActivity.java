package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gmail.manhle240406.project.R;

public class AdvancedPetCareActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_advanced_pet_care);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.advanced_pet_care_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.advanced_pet_care_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save_f08).setOnClickListener(v -> {
            Toast.makeText(this, "Đã lưu thông tin sức khỏe và hành vi", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
