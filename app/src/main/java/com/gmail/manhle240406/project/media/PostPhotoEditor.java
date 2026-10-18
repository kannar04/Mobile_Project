package com.gmail.manhle240406.project.media;
import android.view.*;
import android.widget.*;
import androidx.activity.result.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
public final class PostPhotoEditor {
 public final MediaDraftViewModel model;private final AppCompatActivity activity;private final LinearLayout rows;private long observed=-1;private final Runnable changed;private final ActivityResultLauncher<PickVisualMediaRequest> picker;
 public PostPhotoEditor(AppCompatActivity activity,LinearLayout parent,Runnable changed){this.activity=activity;this.changed=changed;model=new ViewModelProvider(activity).get(MediaDraftViewModel.class);picker=activity.registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(10),uris->{if(!uris.isEmpty()){model.add(uris);changed.run();}});
  parent.addView(SocialSurface.quiet(activity,activity.getString(R.string.media_add_photos),()->picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build())));rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);parent.addView(rows);model.changes.observe(activity,x->{render();if(observed>=0)changed.run();observed=x;});}
 public void select(){picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());}
 private void render(){rows.removeAllViews();int index=0;for(MediaDraftViewModel.Entry e:model.entries){final int number=++index;LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(activity.getString(R.string.media_photo_position,number,model.entries.size()));row.addView(image,new LinearLayout.LayoutParams(SocialViews.dp(activity,64),SocialViews.dp(activity,64)));if(e.state.equals("ready"))MediaGrid.load(image,e.assetId,"thumbnail");else image.setImageResource(R.drawable.profile_avatar_default);TextView status=SocialViews.text(activity,activity.getString(e.state.equals("ready")?R.string.media_ready:e.state.equals("uploading")?R.string.media_uploading:e.error.contains("capacity_full")?R.string.media_capacity_full:R.string.media_upload_failed),false);row.addView(status,new LinearLayout.LayoutParams(0,-2,1));row.addView(SocialSurface.more(activity,()->new MaterialAlertDialogBuilder(activity).setItems(new String[]{activity.getString(R.string.media_earlier),activity.getString(R.string.media_later),activity.getString(R.string.media_image_retry),activity.getString(R.string.media_remove)},(d,which)->{if(which==0)model.move(e,-1);else if(which==1)model.move(e,1);else if(which==2)model.upload(e);else model.remove(e);changed.run();}).show()));rows.addView(row);}}
}
