package com.gmail.manhle240406.project.profile;

import android.content.Intent;
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

public final class ProfileFragment extends com.gmail.manhle240406.project.wiring.MainPageFragment {
    private com.gmail.manhle240406.project.media.AvatarControl avatarControl;
    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> avatarPicker=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),uri->{if(uri!=null&&avatarControl!=null)avatarControl.model.choose(uri);});
    private ProfileViewModel model;
    private TextInputEditText name,bio,phone;
    private Spinner avatar,region;
    private boolean rendering;
    private long renderedForm=-1,shownConflict=-1;
    private androidx.appcompat.app.AlertDialog dialog;
    private TextView verificationStatus;
    private MaterialButton verifyEmail,refreshEmail;
    @Override protected void createPage(Bundle saved) {setContentView(R.layout.activity_profile);
        // Suppress hierarchy serialization for the entire private screen, including nested field widgets.
        disableSavedState(findViewById(R.id.profile_root));
        name=findViewById(R.id.profile_name);bio=findViewById(R.id.profile_bio);phone=findViewById(R.id.profile_phone);
        LinearLayout verification=new LinearLayout(requireContext());verification.setOrientation(LinearLayout.VERTICAL);
        verificationStatus=com.gmail.manhle240406.project.community.SocialSurface.muted(requireContext(),"");verificationStatus.setId(R.id.profile_verification_status);verification.addView(verificationStatus);
        verifyEmail=com.gmail.manhle240406.project.community.SocialSurface.quiet(requireContext(),getString(R.string.profile_verify_email),()->((AniloveApplication)getApplication()).auth().sendVerification());verifyEmail.setId(R.id.profile_verify_email);verification.addView(verifyEmail);
        refreshEmail=com.gmail.manhle240406.project.community.SocialSurface.quiet(requireContext(),getString(R.string.profile_refresh_verification),()->((AniloveApplication)getApplication()).auth().refreshVerification());refreshEmail.setId(R.id.profile_refresh_verification);verification.addView(refreshEmail);
        ((android.view.ViewGroup)findViewById(R.id.profile_summary).getParent()).addView(verification);
        avatar=findViewById(R.id.profile_avatar);region=findViewById(R.id.profile_region);avatar.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.profile_avatar_labels)));region.setAdapter(new ArrayAdapter<>(requireContext(),android.R.layout.simple_spinner_dropdown_item,getResources().getStringArray(R.array.profile_region_labels)));
        model=new ViewModelProvider(this).get(ProfileViewModel.class);
        LinearLayout photoControls=new LinearLayout(requireContext());photoControls.setOrientation(LinearLayout.VERTICAL);
        android.view.ViewGroup identity=(android.view.ViewGroup)findViewById(R.id.profile_summary).getParent();identity.addView(photoControls,identity.indexOfChild(findViewById(R.id.profile_summary))+1);
        avatarControl=new com.gmail.manhle240406.project.media.AvatarControl((AppCompatActivity)requireActivity(),this,photoControls,findViewById(R.id.profile_avatar_image),()->model.load(false),avatarPicker);

        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){capture();}public void afterTextChanged(Editable e){}};
        name.addTextChangedListener(watcher);bio.addTextChangedListener(watcher);phone.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener selection=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int position,long id){capture();updateAvatar();}public void onNothingSelected(AdapterView<?> p){}};
        avatar.setOnItemSelectedListener(selection);region.setOnItemSelectedListener(selection);
        button(R.id.profile_save).setOnClickListener(v->{capture();if(validateContent())model.saveDraft();});
        button(R.id.profile_save_contact).setOnClickListener(v->{capture();if(!ProfileSchema.validPhone(ProfileSchema.phone(model.phone)))field(R.id.profile_phone_layout).setError(getString(R.string.profile_invalid_phone));else{field(R.id.profile_phone_layout).setError(null);model.saveContact();}});
        button(R.id.profile_preview).setOnClickListener(v->preview());
        button(R.id.profile_hide).setOnClickListener(v->model.visibility(false));
        button(R.id.profile_show).setOnClickListener(v->{dialog=new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.profile_show).setMessage(R.string.profile_visibility_help).setPositiveButton(R.string.profile_show,(d,w)->model.visibility(true)).setNegativeButton(R.string.auth_cancelled,null).show();});
        button(R.id.profile_edit).setOnClickListener(v->editPersonal());
        button(R.id.profile_options).setOnClickListener(v->startActivity(new Intent(requireActivity(), EnhancedSettingsActivity.class)));
        button(R.id.profile_reviews).setOnClickListener(v->model.history(false));
        button(R.id.profile_more).setOnClickListener(v->model.history(true));
        button(R.id.profile_reload).setOnClickListener(v->discard(()->model.load(true)));
        button(R.id.profile_retry).setOnClickListener(v->model.retry());
        button(R.id.profile_resolve).setOnClickListener(v->showConflict());
        button(R.id.profile_pets).setOnClickListener(v -> startActivity(new Intent(requireActivity(), com.gmail.manhle240406.project.pet.PetActivity.class)));
        button(R.id.profile_notifications).setOnClickListener(v -> startActivity(new Intent(requireActivity(), com.gmail.manhle240406.project.notifications.NotificationSettingsActivity.class)));
        button(R.id.profile_catalogs).setOnClickListener(v -> startActivity(new Intent(requireActivity(), com.gmail.manhle240406.project.adoption.CatalogActivity.class)));
        button(R.id.profile_review).setOnClickListener(v -> startActivity(new Intent(requireActivity(), com.gmail.manhle240406.project.adoption.ListingReviewActivity.class)));
        button(R.id.profile_discovery).setOnClickListener(v -> ((com.gmail.manhle240406.project.MainActivity)requireActivity()).discovery("",false));
        button(R.id.profile_pet_care).setOnClickListener(v -> startActivity(new Intent(requireActivity(), com.gmail.manhle240406.project.adoption.PetCareActivity.class)));
        button(R.id.profile_listings).setOnClickListener(v -> startActivity(new Intent(requireActivity(), com.gmail.manhle240406.project.community.SocialComposerActivity.class).putExtra("own",true)));
        button(R.id.profile_tools).setOnClickListener(v -> ((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("tools"));
        button(R.id.profile_settings).setOnClickListener(v -> startActivity(new Intent(requireActivity(), EnhancedSettingsActivity.class)));
        button(R.id.profile_logout).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireActivity())
            .setMessage(R.string.listing_logout)
            .setPositiveButton(R.string.listing_logout_now, (d, w) -> logout())
            .setNegativeButton(android.R.string.cancel, null).show());
        button(R.id.profile_back).setOnClickListener(v->finish());

        for(int id:new int[]{R.id.profile_notifications,R.id.profile_discovery,R.id.profile_tools,R.id.profile_pet_care,R.id.profile_catalogs})findViewById(id).setVisibility(View.GONE);
        button(R.id.profile_listings).setText(R.string.feedback_my_posts);
        button(R.id.profile_options).setText(R.string.wiring_settings_title);
        findViewById(R.id.profile_settings).setVisibility(View.GONE);
        button(R.id.profile_review).setVisibility(View.GONE);
        button(R.id.profile_pets).setVisibility(View.GONE);
        LinearLayout shortcuts=(LinearLayout)findViewById(R.id.profile_pets).getParent();
        MaterialButton contacts=com.gmail.manhle240406.project.community.SocialSurface.quiet(requireContext(),"",()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("chat"));contacts.setId(View.generateViewId());contacts.setIconResource(R.drawable.ic_nav_chat);contacts.setContentDescription(getString(R.string.nav_chat));
        android.view.ViewGroup toolbar=(android.view.ViewGroup)findViewById(R.id.profile_options).getParent();View gear=findViewById(R.id.profile_options);toolbar.removeView(gear);android.widget.RelativeLayout top=(android.widget.RelativeLayout)findViewById(R.id.profile_back).getParent();android.widget.RelativeLayout.LayoutParams gp=new android.widget.RelativeLayout.LayoutParams(48*getResources().getDisplayMetrics().densityDpi/160,48*getResources().getDisplayMetrics().densityDpi/160);gp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);top.addView(gear,gp);android.widget.RelativeLayout.LayoutParams cp=new android.widget.RelativeLayout.LayoutParams(gp.width,gp.height);cp.addRule(android.widget.RelativeLayout.START_OF,R.id.profile_options);top.addView(contacts,cp);for(int i=0;i<top.getChildCount();i++)if(top.getChildAt(i) instanceof TextView&&!(top.getChildAt(i) instanceof MaterialButton)){TextView heading=(TextView)top.getChildAt(i);android.widget.RelativeLayout.LayoutParams hp=(android.widget.RelativeLayout.LayoutParams)heading.getLayoutParams();hp.removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END);hp.addRule(android.widget.RelativeLayout.START_OF,contacts.getId());heading.setLayoutParams(hp);heading.setMaxLines(2);}
        button(R.id.profile_options).setText("");button(R.id.profile_options).setIconResource(R.drawable.ic_shell_settings);button(R.id.profile_options).setContentDescription(getString(R.string.wiring_settings_title));
        button(R.id.profile_logout).setText(R.string.oc2_logout);
        button(R.id.profile_logout).setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),R.color.anilove_badge_red_text));
        button(R.id.profile_logout).setStrokeColor(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(),R.color.anilove_badge_red_text)));
        button(R.id.profile_options).setStrokeWidth(0);button(R.id.profile_options).setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        android.view.View scrollRoot=findViewById(R.id.profile_root);android.view.ViewGroup pageHost=(android.view.ViewGroup)scrollRoot.getParent();android.widget.LinearLayout profileShell=new android.widget.LinearLayout(requireContext());profileShell.setOrientation(android.widget.LinearLayout.VERTICAL);profileShell.setBackgroundResource(R.color.anilove_bg);int slot=pageHost.indexOfChild(scrollRoot);android.view.ViewGroup.LayoutParams original=scrollRoot.getLayoutParams();pageHost.removeView(scrollRoot);pageHost.addView(profileShell,slot,original);profileShell.addView(scrollRoot,new android.widget.LinearLayout.LayoutParams(-1,0,1));MaterialButton logout=button(R.id.profile_logout);((android.view.ViewGroup)logout.getParent()).removeView(logout);android.widget.LinearLayout.LayoutParams logoutLayout=new android.widget.LinearLayout.LayoutParams(-1,-2);int spacing=com.gmail.manhle240406.project.community.SocialViews.dp(requireContext(),20);logoutLayout.setMargins(spacing,8,spacing,8);profileShell.addView(logout,logoutLayout);

        photoControls.setVisibility(View.GONE);findViewById(R.id.profile_avatar_image).setOnClickListener(v->editPersonal());
        button(R.id.profile_back).setText("");button(R.id.profile_back).setIconResource(R.drawable.ic_social_back);button(R.id.profile_back).setContentDescription(getString(R.string.auth_back));button(R.id.profile_back).setCornerRadius(com.gmail.manhle240406.project.community.SocialViews.dp(requireContext(),24));
        model.changes.observe(getViewLifecycleOwner(),ignored->render());
        ((AniloveApplication)getApplication()).auth().state().observe(getViewLifecycleOwner(),s->{
            if(s!=null && s.uid!=null)render();
        });
    }
    // Switching tabs preserves the private in-memory edit instead of discarding or prompting twice.
    @Override public void leave(Runnable action){capture();action.run();}
    private View modalFields,modalContacts;
    @Override protected <T extends View> T findViewById(int id){T view=super.findViewById(id);if(view==null&&modalFields!=null)view=modalFields.findViewById(id);if(view==null&&modalContacts!=null)view=modalContacts.findViewById(id);return view;}
    private android.view.ViewGroup editHome;private int editPosition;
    private void editPersonal(){
        if(dialog!=null&&dialog.isShowing())return;
        View fields=findViewById(R.id.profile_public_fields);modalFields=fields;editHome=(android.view.ViewGroup)fields.getParent();editPosition=editHome.indexOfChild(fields);editHome.removeView(fields);
        LinearLayout panel=new LinearLayout(requireContext());panel.setOrientation(LinearLayout.VERTICAL);int pad=(int)(20*getResources().getDisplayMetrics().density);panel.setPadding(pad,0,pad,0);panel.addView(fields);fields.setVisibility(View.VISIBLE);model.editingProfile=true;
        LinearLayout avatarActions=new LinearLayout(requireContext());avatarActions.setOrientation(LinearLayout.VERTICAL);panel.addView(avatarActions);
        avatarActions.addView(com.gmail.manhle240406.project.community.SocialSurface.quiet(requireContext(),getString(R.string.media_change_avatar),()->avatarPicker.launch(new androidx.activity.result.PickVisualMediaRequest.Builder().setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build())));
        PersonalDetailsEditor personal=new PersonalDetailsEditor(requireContext(),panel,getViewLifecycleOwner());
        field(R.id.profile_bio_layout).setHint(R.string.oc2_interests);for(int id:new int[]{R.id.profile_save,R.id.profile_preview,R.id.profile_save_contact})findViewById(id).setVisibility(View.GONE);
        View contact=findViewById(R.id.profile_contact_group);modalContacts=contact;android.view.ViewGroup contactHome=(android.view.ViewGroup)contact.getParent();int contactPosition=contactHome.indexOfChild(contact);contactHome.removeView(contact);panel.addView(contact);contact.setVisibility(View.VISIBLE);
        ScrollView scroll=new ScrollView(requireContext());scroll.addView(panel);
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.social_edit).setView(scroll).setPositiveButton(R.string.profile_save,null).setNegativeButton(R.string.oc2_cancel,null).create();
        final androidx.appcompat.app.AlertDialog editor=dialog;final boolean[] saving={false};
        Runnable close=()->{if(saving[0])return;capture();if(model.dirty()||personal.dirty())new MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.oc2_unsaved).setPositiveButton(R.string.feedback_discard,(d,w)->{editor.dismiss();model.load(true);}).setNegativeButton(R.string.feedback_continue_editing,null).show();else editor.dismiss();};
        editor.setCancelable(false);
        editor.setOnKeyListener((d,key,event)->{if(key==android.view.KeyEvent.KEYCODE_BACK&&event.getAction()==android.view.KeyEvent.ACTION_UP){close.run();return true;}return false;});
        editor.setOnShowListener(d->{editor.getButton(-2).setOnClickListener(v->close.run());editor.getButton(-1).setOnClickListener(v->{capture();if(!validateContent()||!ProfileSchema.validPhone(ProfileSchema.phone(model.phone)))return;saving[0]=true;editor.getButton(-1).setEnabled(false);editor.getButton(-2).setEnabled(false);Runnable failed=()->{saving[0]=false;editor.getButton(-1).setEnabled(true);editor.getButton(-2).setEnabled(true);com.google.android.material.snackbar.Snackbar.make(panel,R.string.oc2_save_failed,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();};personal.save(ok->{if(ok)model.saveAll(()->{saving[0]=false;editor.dismiss();},failed);else failed.run();});});});
        dialog.setOnDismissListener(d->{capture();model.editingProfile=false;panel.removeView(fields);editHome.addView(fields,Math.min(editPosition,editHome.getChildCount()));fields.setVisibility(View.GONE);panel.removeView(contact);contactHome.addView(contact,Math.min(contactPosition,contactHome.getChildCount()));contact.setVisibility(View.GONE);modalFields=null;modalContacts=null;});dialog.show();
    }
    private void options(View anchor){PopupMenu menu=new PopupMenu(requireActivity(),anchor);menu.getMenu().add(0,1,0,R.string.profile_contact);menu.getMenu().add(0,2,1,R.string.profile_hide);menu.getMenu().add(0,3,2,R.string.profile_visibility);menu.getMenu().add(0,4,3,R.string.profile_reload);menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)findViewById(R.id.profile_contact_group).setVisibility(View.VISIBLE);else if(item.getItemId()==2)model.visibility(false);else if(item.getItemId()==3)findViewById(R.id.profile_visibility_group).setVisibility(View.VISIBLE);else discard(()->model.load(true));return true;});menu.show();}
    private void disableSavedState(View v) {v.setSaveEnabled(false);v.setSaveFromParentEnabled(false);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)disableSavedState(g.getChildAt(i));}}
    private MaterialButton button(int id){return findViewById(id);}
    private TextInputLayout field(int id){return findViewById(id);}
    private String value(TextInputEditText v){return v.getText()==null?"":v.getText().toString();}
    private void capture(){if(rendering || model==null)return;model.name=value(name);model.bio=value(bio);model.phone=value(phone);model.avatar=Math.max(0,avatar.getSelectedItemPosition());model.region=Math.max(0,region.getSelectedItemPosition());}
    private void updateAvatar(){if(avatarControl!=null&&avatarControl.model.assetId!=null){com.gmail.manhle240406.project.media.MediaGrid.load(findViewById(R.id.profile_avatar_image),avatarControl.model.assetId,"thumbnail");return;}((ImageView)findViewById(R.id.profile_avatar_image)).setImageResource(model.avatar==1?R.drawable.profile_avatar_cat:model.avatar==2?R.drawable.profile_avatar_dog:R.drawable.profile_avatar_default);}
    private boolean validateContent(){
        field(R.id.profile_name_layout).setError(null);field(R.id.profile_bio_layout).setError(null);
        int length=ProfileSchema.length(model.name.trim());
        if(length<2 || length>40){field(R.id.profile_name_layout).setError(getString(R.string.profile_name_error));name.requestFocus();return false;}
        if(ProfileSchema.length(model.bio.trim())>500){field(R.id.profile_bio_layout).setError(getString(R.string.profile_bio_error));bio.requestFocus();return false;}
        return ProfileSchema.validContent(model.content());
    }
    private String contentText(Map<String,Object> m){if(m==null)return getString(R.string.profile_none);return getResources().getStringArray(R.array.profile_avatar_labels)[ProfileSchema.AVATARS.indexOf(m.get("avatarKey"))]+" · "+m.get("displayName")+"\n"+getResources().getStringArray(R.array.profile_region_labels)[m.get("regionCode")==null?0:ProfileSchema.REGIONS.indexOf(m.get("regionCode"))+1]+"\n"+m.get("bio");}
    private void preview(){capture();if(!validateContent())return;
        LinearLayout layout=new LinearLayout(requireActivity());layout.setOrientation(LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);
        previewText(layout,getString(R.string.profile_candidate),true);previewText(layout,contentText(model.content()),false);
        previewText(layout,getString(R.string.profile_published),true);previewText(layout,model.snapshot.published==null?getString(R.string.profile_no_public):contentText(model.snapshot.published),false);
        previewText(layout,getString(R.string.profile_audience),false);
        // This separate whitelisted view has no contact field or private-record child.
        ScrollView scroll=new ScrollView(requireActivity());scroll.addView(layout);
        dialog=new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.profile_preview).setView(scroll)
            .setPositiveButton(R.string.profile_submit_confirm,(d,w)->{
                AuthState s=((AniloveApplication)getApplication()).auth().state().getValue();
                if(ProfileSchema.sensitive(model.content())){field(R.id.profile_bio_layout).setError(getString(R.string.profile_sensitive));message(R.string.profile_sensitive);}
                else if(s==null || s.pending || !s.verified || !s.verificationKnown || !s.communityReady())message(R.string.profile_denied);
                else model.publishEdited();
            }).setNegativeButton(R.string.profile_keep,null).show();
    }
    private void previewText(LinearLayout parent,String value,boolean heading){TextView t=new TextView(requireActivity());t.setText(value);t.setTextAppearance(heading?com.google.android.material.R.style.TextAppearance_Material3_TitleMedium:com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);ViewCompat.setAccessibilityHeading(t,heading);t.setPadding(0,16,0,16);t.setSaveEnabled(false);parent.addView(t);}
    private void message(int id){((TextView)findViewById(R.id.profile_status)).setText(id);}
    private void discard(Runnable action){capture();if(!model.dirty() && !model.busy && model.retry==null){action.run();return;}dialog=new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.profile_discard_title).setMessage(R.string.profile_discard_body).setPositiveButton(R.string.profile_discard,(d,w)->action.run()).setNegativeButton(R.string.profile_keep,null).show();}
    private void clearAndFinish(){rendering=true;findViewById(R.id.profile_root).setVisibility(View.INVISIBLE);name.setText("");bio.setText("");phone.setText("");((TextView)findViewById(R.id.profile_review_text)).setText("");if(dialog!=null)dialog.dismiss();finish();}
    private void render(){
        if(model.sessionLost){clearAndFinish();return;}
        if(renderedForm!=model.formRevision){rendering=true;name.setText(model.name);bio.setText(model.bio);phone.setText(model.phone);avatar.setSelection(model.avatar);region.setSelection(model.region);rendering=false;renderedForm=model.formRevision;updateAvatar();}
        AuthState auth=((AniloveApplication)getApplication()).auth().state().getValue();
        if(auth!=null){
            String copy=getString(auth.verified&&auth.verificationKnown?R.string.profile_email_verified:R.string.profile_email_unverified);
            if(auth.notice==AuthState.Notice.SENT)copy+="\n"+getString(R.string.auth_send_success);
            else if(auth.notice==AuthState.Notice.SEND_FAILED)copy+="\n"+getString(R.string.auth_send_error);
            else if(auth.notice==AuthState.Notice.COOLDOWN)copy+="\n"+getString(R.string.auth_cooldown);
            verificationStatus.setText(copy);verifyEmail.setVisibility(auth.verified&&auth.verificationKnown?View.GONE:View.VISIBLE);
            refreshEmail.setVisibility(auth.verified&&auth.verificationKnown?View.GONE:View.VISIBLE);
            verifyEmail.setEnabled(auth.canSend&&!auth.pending&&!auth.setupLoading);refreshEmail.setEnabled(!auth.pending&&!auth.setupLoading);
        }
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
        message(notice);findViewById(R.id.profile_status).setVisibility(model.loaded&&!model.busy&&model.code==ProfileRepository.Code.OK&&!model.committed&&!model.submitted?View.GONE:View.VISIBLE);
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
        dialog=new MaterialAlertDialogBuilder(requireActivity()).setTitle(R.string.profile_conflict).setMessage(getString(R.string.profile_compare,getString(R.string.profile_conflict),privateText(model.snapshot),privateText(model.conflict),edited)).setPositiveButton(R.string.profile_keep,(d,w)->model.resolveConflict(true)).setNegativeButton(R.string.profile_discard,(d,w)->model.resolveConflict(false)).setCancelable(true).show();}
    private void logout() {
        ((AniloveApplication)getApplication()).auth().logout();
    }
    @Override public void onDestroyView(){if(dialog!=null)dialog.dismiss();super.onDestroyView();}
}
