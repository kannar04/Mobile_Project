package com.gmail.manhle240406.project.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.*;

/** Registration ends with an acknowledged private name, never an email-verification screen. */
public final class ProfileSetupActivity extends AppCompatActivity {
    private ProfileSetupViewModel model;private TextInputEditText name;private MaterialButton next,retry;
    private TextInputLayout field;private TextView status;private boolean leaving;private MaterialButton useExisting;
    @Override protected void onCreate(Bundle saved){super.onCreate(saved);EdgeToEdge.enable(this);
        LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.setup_profile_title,this::leaveApp);
        LinearLayout body=SocialSurface.content(this,root);body.addView(SocialViews.text(this,getString(R.string.setup_profile_body),false));
        ImageView avatarPreview=new ImageView(this);avatarPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);avatarPreview.setImageResource(R.drawable.profile_avatar_default);avatarPreview.setContentDescription(getString(R.string.media_change_avatar));body.addView(avatarPreview,new LinearLayout.LayoutParams(SocialViews.dp(this,88),SocialViews.dp(this,88)));
        new com.gmail.manhle240406.project.media.AvatarControl(this,body,avatarPreview,()->{});
        field=(TextInputLayout)getLayoutInflater().inflate(R.layout.pet_field,body,false);field.setHint(R.string.profile_name);name=(TextInputEditText)field.getEditText();name.setId(R.id.setup_display_name);name.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);body.addView(field);
        status=SocialSurface.muted(this,"");status.setId(R.id.setup_status);body.addView(status);
        next=SocialViews.primaryButton(this,getString(R.string.setup_continue),this::save);next.setId(R.id.setup_continue);body.addView(next);
        retry=SocialSurface.quiet(this,getString(R.string.wiring_retry),this::retry);body.addView(retry);
        useExisting=SocialSurface.quiet(this,getString(R.string.setup_use_saved),()->model.useExisting());body.addView(useExisting);
        body.addView(SocialSurface.quiet(this,getString(R.string.auth_logout),()->{((AniloveApplication)getApplication()).auth().logout();startActivity(new Intent(this,com.gmail.manhle240406.project.auth.AuthActivity.class));finish();}));
        SocialViews.privateView(root);model=new ViewModelProvider(this).get(ProfileSetupViewModel.class);name.setText(model.name);
        name.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){model.name=s.toString();field.setError(null);}public void afterTextChanged(Editable e){}});
        name.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){save();return true;}return false;});
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){leaveApp();}});
        model.changes.observe(this,ignored->render());
        ((AniloveApplication)getApplication()).auth().state().observe(this,auth->{if(auth==null||auth.uid==null){if(!leaving){leaving=true;startActivity(new Intent(this,com.gmail.manhle240406.project.auth.AuthActivity.class));finish();}}else if(auth.setupReady&&!auth.pending&&!model.checked&&!model.busy&&model.code==ProfileRepository.Code.OK)model.load();});
    }
    private void leaveApp(){moveTaskToBack(true);}
    private void save(){model.name=name.getText()==null?"":name.getText().toString();if(!model.validName()){field.setError(getString(R.string.profile_name_error));name.requestFocus();return;}model.save();}
    private void retry(){if(model.pending())model.save();else{((AniloveApplication)getApplication()).auth().refreshReadiness();model.load();}}
    private void render(){
        if(model.ready&&!leaving){leaving=true;startActivity(new Intent(this,MainActivity.class).putExtra(MainActivity.PAGE,getIntent().getStringExtra(MainActivity.PAGE)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();return;}
        next.setEnabled(!model.busy&&model.checked&&!model.lost);name.setEnabled(!model.busy&&!model.pending());
        next.setText(model.conflict&&model.checked?R.string.setup_reapply_name:R.string.setup_continue);
        useExisting.setVisibility(model.conflict&&model.checked&&!model.busy&&!model.existingName.isEmpty()?View.VISIBLE:View.GONE);
        int copy=model.busy?R.string.profile_loading:model.code==ProfileRepository.Code.CONFLICT?R.string.profile_conflict:model.code==ProfileRepository.Code.OK?0:R.string.setup_load_failed;
        status.setText(copy==0?"":getString(copy));status.setVisibility(copy==0?View.GONE:View.VISIBLE);
        if(model.conflict&&model.checked&&!model.busy)status.setText(getString(R.string.setup_conflict_comparison,model.existingName,model.name));
        retry.setVisibility(!model.busy&&(!model.checked||model.code!=ProfileRepository.Code.OK)?View.VISIBLE:View.GONE);
    }
}
