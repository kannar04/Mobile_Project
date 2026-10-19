package com.gmail.manhle240406.project.adoption;
import android.os.Bundle;
import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
/** Retained route name forwards to the managed native workspace. */
public class AdminCaseToolsActivity extends AppCompatActivity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);startActivity(new Intent(this,CourseAdminActivity.class));finish();}
}
