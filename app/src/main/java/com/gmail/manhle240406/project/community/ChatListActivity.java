package com.gmail.manhle240406.project.community;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public final class ChatListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        com.gmail.manhle240406.project.MainActivity.open(this, "chat");
        finish();
    }
}
