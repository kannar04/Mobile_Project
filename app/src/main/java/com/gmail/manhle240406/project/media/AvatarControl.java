package com.gmail.manhle240406.project.media;
import android.widget.*;
import android.view.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;

/** One avatar action; replace/remove/provider options remain contextual. */
public final class AvatarControl {
 public final AvatarViewModel model;
 public AvatarControl(AppCompatActivity a,LinearLayout parent,ImageView image,Runnable saved){this(a,null,parent,image,saved,null);}
 public AvatarControl(AppCompatActivity a,Fragment fragment,LinearLayout parent,ImageView image,Runnable saved,ActivityResultLauncher<PickVisualMediaRequest> supplied){
  model=fragment==null?new ViewModelProvider(a).get(AvatarViewModel.class):new ViewModelProvider(fragment).get(AvatarViewModel.class);
  ActivityResultCallback<android.net.Uri> callback=uri->{if(uri!=null)model.choose(uri);};ActivityResultLauncher<PickVisualMediaRequest> picker=supplied!=null?supplied:a.registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),callback);
  parent.addView(SocialSurface.muted(a,a.getString(fragment==null?R.string.media_private_default:R.string.media_private_avatar)));
  Runnable options=()->{
   List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();labels.add(a.getString(R.string.media_pick_avatar));actions.add(()->picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
   if(model.assetId!=null){labels.add(a.getString(R.string.media_remove_avatar));actions.add(model::remove);}
   com.google.firebase.auth.FirebaseUser user=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();if(user!=null&&user.getProviderData().stream().anyMatch(p->"google.com".equals(p.getProviderId()))){labels.add(a.getString(R.string.media_google_avatar));actions.add(()->model.google(false));}
   new MaterialAlertDialogBuilder(a).setTitle(R.string.media_change_avatar).setItems(labels.toArray(new String[0]),(d,which)->actions.get(which).run()).setNegativeButton(android.R.string.cancel,null).show();
  };
  com.google.android.material.button.MaterialButton change=SocialSurface.quiet(a,a.getString(R.string.media_change_avatar),options);parent.addView(change);
  image.setContentDescription(a.getString(R.string.media_change_avatar));image.setOnClickListener(v->{if(!model.busy&&model.loaded&&!model.hasPending())options.run();});
  com.google.android.material.button.MaterialButton retry=SocialSurface.quiet(a,a.getString(R.string.media_image_retry),model::retry);parent.addView(retry);
  TextView status=SocialSurface.muted(a,"");status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);parent.addView(status);final long[] rendered={-1};final String[] renderedAsset={null};
  model.changes.observe(fragment==null?a:fragment.getViewLifecycleOwner(),x->{
   change.setEnabled(model.loaded&&!model.busy&&!model.lost&&!model.hasPending());retry.setVisibility(model.hasPending()&&!model.busy?View.VISIBLE:View.GONE);
   int copy=model.busy?R.string.media_uploading:!model.error.isEmpty()?model.error.contains("capacity_full")?R.string.media_capacity_full:R.string.media_upload_failed:0;status.setText(copy==0?"":a.getString(copy));status.setVisibility(copy==0?View.GONE:View.VISIBLE);
   if(!Objects.equals(renderedAsset[0],model.assetId)){renderedAsset[0]=model.assetId;image.setTag(new Object());if(model.assetId!=null){image.setImageTintList(null);MediaGrid.load(image,model.assetId,"thumbnail");}else image.setImageResource(R.drawable.profile_avatar_default);}
   if(rendered[0]>=0&&rendered[0]!=model.version){SessionAliases.clear();saved.run();}rendered[0]=model.version;
  });
 }
}
