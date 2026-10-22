package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class AdminDashboardActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new android.content.Intent(this,CourseAdminActivity.class));
        finish();
    }
}
