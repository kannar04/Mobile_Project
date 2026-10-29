package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.gmail.manhle240406.project.R;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);
        findViewById(R.id.admin_dashboard_back)
                .setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }
}
