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

/** Bounded adaptive photo grid; rendering never starts an upload or a Firestore query. */
public final class PostPhotoEditor {
 public final MediaDraftViewModel model;
 private final AppCompatActivity activity;private final GridLayout grid;private final TextView count;
 private final com.google.android.material.button.MaterialButton add;
 private boolean locked;public void setLocked(boolean next){if(locked!=next){locked=next;render();}}
 private long observed=-1;private int columns=2;private final Runnable changed;
 private final ActivityResultLauncher<PickVisualMediaRequest> picker;
 public PostPhotoEditor(AppCompatActivity activity,LinearLayout parent,Runnable changed){this(activity,parent,changed,10,"post_image");}
 public PostPhotoEditor(AppCompatActivity activity,LinearLayout parent,Runnable changed,int maximum,String purpose){
  this.activity=activity;this.changed=changed;model=new ViewModelProvider(activity).get(MediaDraftViewModel.class);model.limit=maximum;model.purpose=purpose;
  picker=activity.registerForActivityResult(new ActivityResultContracts.PickMultipleVisualMedia(maximum),uris->{if(!uris.isEmpty())model.add(uris);});
  count=SocialSurface.muted(activity,"");parent.addView(count);
  add=SocialSurface.quiet(activity,activity.getString(R.string.media_add_photos),this::select);parent.addView(add);
  grid=new GridLayout(activity);grid.setColumnCount(columns);parent.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int next=(r-l)>=SocialViews.dp(activity,440)?3:2;if(next!=columns){columns=next;render();}});
  model.changes.observe(activity,x->{render();if(observed>=0)changed.run();observed=x;});
 }
 public void select(){if(!locked&&model.entries.size()<model.limit)picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());}
 private void render(){
  grid.removeAllViews();grid.setColumnCount(columns);count.setText(activity.getString(R.string.feedback_photo_count,model.entries.size(),model.limit));add.setEnabled(!locked&&model.entries.size()<model.limit&&!model.lost);
  if(model.entries.isEmpty()){TextView hint=SocialSurface.muted(activity,activity.getString(R.string.feedback_photo_hint));hint.setGravity(Gravity.CENTER);hint.setPadding(16,32,16,32);hint.setMinHeight(SocialViews.dp(activity,112));hint.setBackgroundResource(R.drawable.feedback_photo_outline);GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(0),GridLayout.spec(0,columns,1f));lp.width=0;grid.addView(hint,lp);return;}
  int index=0;for(MediaDraftViewModel.Entry e:model.entries){final int number=++index;
   LinearLayout cell=new LinearLayout(activity);cell.setOrientation(LinearLayout.VERTICAL);cell.setPadding(SocialViews.dp(activity,4),SocialViews.dp(activity,4),SocialViews.dp(activity,4),SocialViews.dp(activity,12));
   FrameLayout photo=new FrameLayout(activity);ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setContentDescription(activity.getString(R.string.media_photo_position,number,model.entries.size()));photo.addView(image,new FrameLayout.LayoutParams(-1,-1));
   if(e.state.equals("ready"))MediaGrid.load(image,e.assetId,"thumbnail");else image.setImageResource(R.drawable.ic_shell_photo);
   com.google.android.material.button.MaterialButton more=SocialSurface.more(activity,()->options(e));more.setContentDescription(activity.getString(R.string.feedback_photo_actions,number));more.setBackgroundTintList(android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(more,com.google.android.material.R.attr.colorSurface)));
   FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(SocialViews.dp(activity,48),SocialViews.dp(activity,48),Gravity.TOP|Gravity.END);photo.addView(more,mp);cell.addView(photo,new LinearLayout.LayoutParams(-1,SocialViews.dp(activity,144)));
   TextView status=SocialSurface.muted(activity,activity.getString(e.state.equals("ready")?number==1?R.string.feedback_cover_photo:R.string.media_ready:e.state.equals("uploading")?R.string.media_uploading:e.error.contains("capacity_full")?R.string.media_capacity_full:R.string.media_upload_failed));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);cell.addView(status);
   GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec((number-1)/columns),GridLayout.spec((number-1)%columns,1f));lp.width=0;lp.height=-2;grid.addView(cell,lp);
  }
 }
 private void options(MediaDraftViewModel.Entry e){if(locked)return;List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();int i=model.entries.indexOf(e);
  if(i>0){labels.add(activity.getString(R.string.media_earlier));actions.add(()->model.move(e,-1));}if(i<model.entries.size()-1){labels.add(activity.getString(R.string.media_later));actions.add(()->model.move(e,1));}
  if("failed".equals(e.state)){labels.add(activity.getString(R.string.media_image_retry));actions.add(()->model.upload(e));}if(!"uploading".equals(e.state)){labels.add(activity.getString(R.string.media_remove));actions.add(()->model.remove(e));}
  new MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.feedback_photo_actions,i+1)).setItems(labels.toArray(new String[0]),(d,w)->actions.get(w).run()).setNegativeButton(android.R.string.cancel,null).show();
 }
}
