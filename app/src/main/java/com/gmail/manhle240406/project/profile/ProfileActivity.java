package com.gmail.manhle240406.project.profile;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;
import java.util.*;

public final class ProfileActivity extends AppCompatActivity {
    private ProfileViewModel model;
    private TextInputEditText name,bio,phone;
    private Spinner avatar,region;
    private boolean rendering;
    private long renderedForm=-1,shownConflict=-1;
    private androidx.appcompat.app.AlertDialog dialog;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);EdgeToEdge.enable(this);setContentView(R.layout.activity_profile);
        // Suppress hierarchy serialization for the entire private screen, including nested field widgets.
        disableSavedState(findViewById(R.id.profile_root));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_root),(v,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            Insets ime=insets.getInsets(WindowInsetsCompat.Type.ime());v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,ime.bottom));return insets;
        });
        name=findViewById(R.id.profile_name);bio=findViewById(R.id.profile_bio);phone=findViewById(R.id.profile_phone);
        avatar=findViewById(R.id.profile_avatar);region=findViewById(R.id.profile_region);
        model=new ViewModelProvider(this).get(ProfileViewModel.class);
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){capture();}public void afterTextChanged(Editable e){}};
        name.addTextChangedListener(watcher);bio.addTextChangedListener(watcher);phone.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener selection=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){capture();updateAvatar();}public void onNothingSelected(AdapterView<?> p){}};
        avatar.setOnItemSelectedListener(selection);region.setOnItemSelectedListener(selection);
        button(R.id.profile_save).setOnClickListener(v->{capture();if(validateContent())model.saveDraft();});
        button(R.id.profile_save_contact).setOnClickListener(v->{capture();if(!ProfileSchema.validPhone(ProfileSchema.phone(model.phone)))field(R.id.profile_phone_layout).setError(getString(R.string.profile_invalid_phone));else{field(R.id.profile_phone_layout).setError(null);model.saveContact();}});
        button(R.id.profile_preview).setOnClickListener(v->preview());
        button(R.id.profile_hide).setOnClickListener(v->model.visibility(false));
        button(R.id.profile_show).setOnClickListener(v->{dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.profile_show).setMessage(R.string.profile_visibility_help).setPositiveButton(R.string.profile_show,(d,w)->model.visibility(true)).setNegativeButton(R.string.auth_cancelled,null).show();});
        button(R.id.profile_edit).setOnClickListener(v->{model.editingProfile=true;render();});
        button(R.id.profile_options).setOnClickListener(v->options(v));
        button(R.id.profile_reviews).setOnClickListener(v->model.history(false));
        button(R.id.profile_more).setOnClickListener(v->model.history(true));
        button(R.id.profile_reload).setOnClickListener(v->discard(()->model.load(true)));
        button(R.id.profile_retry).setOnClickListener(v->model.retry());
        button(R.id.profile_resolve).setOnClickListener(v->showConflict());
        button(R.id.profile_back).setOnClickListener(v->discard(this::finish));
        getOnBackPressedDispatcher().addCallback(this,new OnBackPressedCallback(true){public void handleOnBackPressed(){discard(ProfileActivity.this::finish);}});
        model.changes.observe(this,ignored->render());
        ((AniloveApplication)getApplication()).auth().state().observe(this,s->{if(s.uid==null || !s.setupReady)clearAndFinish();else render();});
    }
    private void options(View anchor){PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(0,1,0,R.string.profile_contact);menu.getMenu().add(0,2,1,R.string.profile_hide);menu.getMenu().add(0,3,2,R.string.profile_visibility);menu.getMenu().add(0,4,3,R.string.profile_reload);menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)findViewById(R.id.profile_contact_group).setVisibility(View.VISIBLE);else if(item.getItemId()==2)model.visibility(false);else if(item.getItemId()==3)findViewById(R.id.profile_visibility_group).setVisibility(View.VISIBLE);else discard(()->model.load(true));return true;});menu.show();}
    private void disableSavedState(View v) {v.setSaveEnabled(false);v.setSaveFromParentEnabled(false);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)disableSavedState(g.getChildAt(i));}}
    private MaterialButton button(int id){return findViewById(id);}
    private TextInputLayout field(int id){return findViewById(id);}
    private String value(TextInputEditText v){return v.getText()==null?"":v.getText().toString();}
    private void capture(){if(rendering || model==null)return;model.name=value(name);model.bio=value(bio);model.phone=value(phone);model.avatar=Math.max(0,avatar.getSelectedItemPosition());model.region=Math.max(0,region.getSelectedItemPosition());}
    private void updateAvatar(){((ImageView)findViewById(R.id.profile_avatar_image)).setImageResource(model.avatar==1?R.drawable.profile_avatar_cat:model.avatar==2?R.drawable.profile_avatar_dog:R.drawable.profile_avatar_default);}
    private boolean validateContent(){
        field(R.id.profile_name_layout).setError(null);field(R.id.profile_bio_layout).setError(null);
        int length=ProfileSchema.length(model.name.trim());
        if(length<2 || length>40){field(R.id.profile_name_layout).setError(getString(R.string.profile_name_error));name.requestFocus();return false;}
        if(ProfileSchema.length(model.bio.trim())>500){field(R.id.profile_bio_layout).setError(getString(R.string.profile_bio_error));bio.requestFocus();return false;}
        return ProfileSchema.validContent(model.content());
    }
    private String contentText(Map<String,Object> m){if(m==null)return getString(R.string.profile_none);return getResources().getStringArray(R.array.profile_avatar_labels)[ProfileSchema.AVATARS.indexOf(m.get("avatarKey"))]+" · "+m.get("displayName")+"\n"+getResources().getStringArray(R.array.profile_region_labels)[m.get("regionCode")==null?0:ProfileSchema.REGIONS.indexOf(m.get("regionCode"))+1]+"\n"+m.get("bio");}
    private void preview(){capture();if(!validateContent())return;
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);
        previewText(layout,getString(R.string.profile_candidate),true);previewText(layout,contentText(model.content()),false);
        previewText(layout,getString(R.string.profile_published),true);previewText(layout,model.snapshot.published==null?getString(R.string.profile_no_public):contentText(model.snapshot.published),false);
        previewText(layout,getString(R.string.profile_audience),false);
        // This separate whitelisted view has no contact field or private-record child.
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.profile_preview).setView(scroll)
            .setPositiveButton(R.string.profile_submit_confirm,(d,w)->{
                AuthState s=((AniloveApplication)getApplication()).auth().state().getValue();
                if(ProfileSchema.sensitive(model.content())){field(R.id.profile_bio_layout).setError(getString(R.string.profile_sensitive));message(R.string.profile_sensitive);}
                else if(s==null || s.pending || !s.verified || !s.verificationKnown || !s.communityReady())message(R.string.profile_denied);
                else model.publishEdited();
            }).setNegativeButton(R.string.profile_keep,null).show();
    }
    private void previewText(LinearLayout parent,String value,boolean heading){TextView t=new TextView(this);t.setText(value);t.setTextAppearance(heading?com.google.android.material.R.style.TextAppearance_Material3_TitleMedium:com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);ViewCompat.setAccessibilityHeading(t,heading);t.setPadding(0,16,0,16);t.setSaveEnabled(false);parent.addView(t);}
    private void message(int id){((TextView)findViewById(R.id.profile_status)).setText(id);}
    private void discard(Runnable action){capture();if(!model.dirty() && !model.busy && model.retry==null){action.run();return;}dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.profile_discard_title).setMessage(R.string.profile_discard_body).setPositiveButton(R.string.profile_discard,(d,w)->action.run()).setNegativeButton(R.string.profile_keep,null).show();}
    private void clearAndFinish(){rendering=true;findViewById(R.id.profile_root).setVisibility(View.INVISIBLE);name.setText("");bio.setText("");phone.setText("");((TextView)findViewById(R.id.profile_review_text)).setText("");if(dialog!=null)dialog.dismiss();finish();}
    private void render(){
        if(model.sessionLost){clearAndFinish();return;}
        if(renderedForm!=model.formRevision){rendering=true;name.setText(model.name);bio.setText(model.bio);phone.setText(model.phone);avatar.setSelection(model.avatar);region.setSelection(model.region);rendering=false;renderedForm=model.formRevision;updateAvatar();}
        AuthState auth=((AniloveApplication)getApplication()).auth().state().getValue();
        boolean canAct=model.loaded && !model.busy && !model.coldPending && !model.publicationPending() && !model.conflictReady && model.retry==null && auth!=null && !auth.pending;
        boolean enabled=canAct && model.snapshot.issue==ProfileRepository.Code.OK;
        name.setEnabled(enabled);bio.setEnabled(enabled);phone.setEnabled(enabled);avatar.setEnabled(enabled);region.setEnabled(enabled);
        for(int id:new int[]{R.id.profile_save,R.id.profile_save_contact,R.id.profile_preview,R.id.profile_hide,R.id.profile_show,R.id.profile_reviews,R.id.profile_more})button(id).setEnabled(enabled);
        button(R.id.profile_hide).setEnabled(canAct);
        button(R.id.profile_show).setEnabled(enabled && model.snapshot!=null && model.snapshot.control!=null && model.snapshot.published!=null && auth.communityReady());
        button(R.id.profile_reload).setEnabled(!model.busy && model.retry==null && !model.conflictReady);
        button(R.id.profile_retry).setVisibility(model.retry!=null||model.coldPending||model.publicationPending()?View.VISIBLE:View.GONE);button(R.id.profile_retry).setEnabled((model.retry!=null||model.coldPending||model.publicationPending()) && !model.busy);
        button(R.id.profile_resolve).setVisibility(model.conflictReady?View.VISIBLE:View.GONE);
        findViewById(R.id.profile_progress).setVisibility(model.busy?View.VISIBLE:View.GONE);
        int notice=model.busy?R.string.profile_loading:model.loaded?R.string.profile_loaded:R.string.profile_signin;
        switch(model.code){case CONFLICT:notice=R.string.profile_conflict;break;case DENIED:notice=R.string.profile_denied;break;case MALFORMED:notice=R.string.profile_malformed;break;case UNAVAILABLE:notice=R.string.profile_unavailable;break;case UNKNOWN:notice=R.string.profile_unknown;break;default:if(model.submitted)notice=R.string.profile_submitted;else if(model.committed)notice=R.string.profile_saved;else if(model.loaded && model.snapshot!=null && !model.busy)notice=R.string.profile_loaded;}
        message(notice);
        findViewById(R.id.profile_public_fields).setVisibility(model.editingProfile?View.VISIBLE:View.GONE);
        ((TextView)findViewById(R.id.profile_summary)).setText(model.name.isEmpty()?getString(R.string.profile_none):model.name);
        if(model.snapshot!=null){Map<String,Object> d=model.snapshot.draft,c=model.snapshot.control;
            ((TextView)findViewById(R.id.profile_draft_status)).setText(d==null?getString(R.string.profile_none):getString("submitted".equals(d.get("submission"))?R.string.profile_pending:R.string.profile_draft));
            ((TextView)findViewById(R.id.profile_visibility_state)).setText(c==null?getString(R.string.profile_no_controls):getString("public".equals(c.get("visibility"))?R.string.profile_public_visible:R.string.profile_public_hidden,ProfileSchema.version(c)));
        }
        if(model.reviews!=null){StringBuilder b=new StringBuilder();for(Map<String,Object> r:model.reviews.rows){b.append(getString("hide".equals(r.get("command"))?R.string.profile_hide:R.string.profile_submit_confirm)).append(" · ").append(java.text.DateFormat.getDateTimeInstance().format(((com.google.firebase.Timestamp)r.get("committedAt")).toDate())).append("\n\n");}((TextView)findViewById(R.id.profile_review_text)).setText(b.length()==0?getString(R.string.profile_empty_reviews):b.toString());}
        else ((TextView)findViewById(R.id.profile_review_text)).setText("");
        button(R.id.profile_more).setVisibility(model.reviews!=null && model.reviews.cursor!=null?View.VISIBLE:View.GONE);
        if(model.conflictReady && shownConflict!=model.conflictRevision){shownConflict=model.conflictRevision;showConflict();}
    }
    private String privateText(ProfileRepository.Snapshot s){return contentText(s.draft)+"\n"+getString(R.string.profile_phone)+": "+(s.contact==null||s.contact.get("phoneE164")==null?getString(R.string.profile_none):s.contact.get("phoneE164"))+"\n"+getString(R.string.profile_visibility)+": "+(s.control==null?getString(R.string.profile_no_controls):getString("public".equals(s.control.get("visibility"))?R.string.profile_public_visible:R.string.profile_public_hidden,ProfileSchema.version(s.control)));}
    private void showConflict(){String edited=contentText(model.content())+"\n"+getString(R.string.profile_phone)+": "+model.phone;
        if(model.conflictedCommand!=null && model.conflictedCommand.kind==ProfileCommand.Kind.VISIBILITY)
            edited+="\n"+getString("public".equals(model.conflictedCommand.payload.get("visibility"))?R.string.profile_show:R.string.profile_hide);
        dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.profile_conflict).setMessage(getString(R.string.profile_compare,getString(R.string.profile_conflict),privateText(model.snapshot),privateText(model.conflict),edited)).setPositiveButton(R.string.profile_keep,(d,w)->model.resolveConflict(true)).setNegativeButton(R.string.profile_discard,(d,w)->model.resolveConflict(false)).setCancelable(true).show();}
    @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();super.onDestroy();}
}
