package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;

/** Dedicated entry point for account administration inside the shared managed Admin workspace. */
public final class AdminAccountActivity extends CourseAdminActivity {
    @Override
    public void onCreate(Bundle state) {
        getIntent().putExtra("adminSection", "accounts");
        super.onCreate(state);
    }
}
