package com.gmail.manhle240406.project.profile;
import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.preferences.AppearanceRepository;
import com.gmail.manhle240406.project.wiring.AppServices;
import com.gmail.manhle240406.project.community.SocialViews;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

/** Existing settings, acknowledged installation preferences; no fictitious MFA/device-management action. */
public final class EnhancedSettingsActivity extends AppCompatActivity {
 private AppServices services;private AppearanceRepository.State selection;private boolean saving;private androidx.appcompat.app.AlertDialog dialog;
 @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);setContentView(R.layout.activity_enhanced_settings);SocialViews.insets(findViewById(R.id.enhanced_settings_root));
  services=((AniloveApplication)getApplication()).services();services.appearance.read().thenAccept(value->runOnUiThread(()->{if(!isDestroyed())selection=value;}));
  ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.settings_back)).setNavigationOnClickListener(v->finish());
  ((TextView)findViewById(R.id.btn_setting_account)).setText(R.string.profile_title);
  findViewById(R.id.btn_setting_account).setOnClickListener(v->startActivity(new Intent(this,ProfileActivity.class)));
  ((TextView)findViewById(R.id.btn_setting_theme)).setText(R.string.wiring_theme);((TextView)findViewById(R.id.btn_setting_language)).setText(R.string.wiring_language);((TextView)findViewById(R.id.btn_setting_security)).setText(R.string.wiring_security);
  findViewById(R.id.btn_setting_language).setOnClickListener(v->{if(selection==null||saving)return;dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.wiring_language).setSingleChoiceItems(new String[]{getString(R.string.wiring_system),"Ti\u1ebfng Vi\u1ec7t","English"},selection.language.ordinal(),(d,w)->{d.dismiss();if(w==2){dialog=new MaterialAlertDialogBuilder(this).setMessage(R.string.flow_language_pending).setPositiveButton(android.R.string.ok,null).show();return;}save(AppearanceRepository.Language.values()[w],selection.theme);}).setNegativeButton(android.R.string.cancel,null).show();});
  findViewById(R.id.btn_setting_theme).setOnClickListener(v->{if(selection==null||saving)return;dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.wiring_theme).setSingleChoiceItems(new String[]{getString(R.string.wiring_system),getString(R.string.wiring_light),getString(R.string.wiring_dark)},selection.theme.ordinal(),(d,w)->{d.dismiss();save(selection.language,AppearanceRepository.Theme.values()[w]);}).setNegativeButton(android.R.string.cancel,null).show();});
  findViewById(R.id.btn_setting_security).setOnClickListener(v->startActivity(new Intent(this,com.gmail.manhle240406.project.auth.PasswordChangeActivity.class)));
  android.view.View security=findViewById(R.id.btn_setting_security);
  android.view.ViewGroup parent=(android.view.ViewGroup)security.getParent();
  com.google.android.material.button.MaterialButton deletion=SocialViews.button(this,getString(R.string.flow_delete_account),()->startActivity(new Intent(this,AccountDeletionActivity.class)));
  deletion.setTag("account_deletion");parent.addView(deletion);
  parent.addView(SocialViews.button(this,getString(R.string.flow_explore),()->com.gmail.manhle240406.project.MainActivity.open(this,"tools")));

 }
 private void save(AppearanceRepository.Language language,AppearanceRepository.Theme theme){saving=true;services.appearance.set(language,theme).whenComplete((value,error)->runOnUiThread(()->{saving=false;if(isDestroyed()||isFinishing())return;if(error!=null){Snackbar.make(findViewById(R.id.enhanced_settings_root),R.string.wiring_preference_failed,Snackbar.LENGTH_LONG).show();return;}selection=value;services.applyAppearance(value);}));}
 @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();super.onDestroy();}
}
