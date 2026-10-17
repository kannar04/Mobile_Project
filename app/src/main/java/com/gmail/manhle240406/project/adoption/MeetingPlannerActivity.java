package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gmail.manhle240406.project.R;

public class MeetingPlannerActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_meeting_planner);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.meeting_planner_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.meeting_planner_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_confirm_agreement).setOnClickListener(v -> {
            Toast.makeText(this, "Đã xác nhận thỏa thuận & sẵn sàng nhận nuôi!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
