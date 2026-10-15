package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gmail.manhle240406.project.R;

public class WelfareConcernsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_welfare_concerns);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.welfare_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.welfare_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_send_welfare).setOnClickListener(v -> {
            Toast.makeText(this, "Đã gửi báo cáo phúc lợi khẩn cấp thành công!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
