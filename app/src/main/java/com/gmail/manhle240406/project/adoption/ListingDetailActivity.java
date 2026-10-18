package com.gmail.manhle240406.project.adoption;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.*;
import com.google.firebase.firestore.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import java.util.Map;

/** Current allowlisted detail; no copied private profile, fabricated pet, or implicit application. */
public final class ListingDetailActivity extends AppCompatActivity {
 private ListingDiscoveryRepository reader;private FavoritesRepository favorites;
 private SessionGateway sessions;private SessionEpoch.Ticket ticket;
 private LinearLayout content;private String id;private boolean mutation,saved;
 private androidx.appcompat.app.AlertDialog dialog;
 private final Runnable invalidated=()->{if(content!=null)message(R.string.wiring_auth_required);if(dialog!=null)dialog.dismiss();};
 @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);
  LinearLayout root=SocialSurface.page(this);SocialViews.privateView(root);SocialSurface.header(this,root,R.string.flow_detail,this::finish);content=SocialSurface.content(this,root);
  FirebaseEnvironment env=((AniloveApplication)getApplication()).foundation();sessions=env.sessions;ticket=sessions==null?null:sessions.capture();id=getIntent().getStringExtra("listing");
  if(ticket==null||ticket.uid==null){message(R.string.wiring_auth_required);return;}
  if(!com.gmail.manhle240406.project.pet.PetSchema.id(id)){message(R.string.wiring_unavailable_item);return;}
  reader=new ListingDiscoveryRepository(FirebaseFirestore.getInstance(),sessions);favorites=new FavoritesRepository(FirebaseFirestore.getInstance(),sessions);sessions.onInvalidated(invalidated);load();
 }
 private boolean current(){return !isDestroyed()&&!isFinishing()&&sessions!=null&&sessions.current(ticket);}
 private void message(int text){content.removeAllViews();content.addView(SocialSurface.muted(this,getString(text)));}
 private void load(){message(R.string.wiring_loading);reader.detail(ticket,id).addOnCompleteListener(done->{if(!current())return;if(!done.isSuccessful()){message(R.string.wiring_unavailable_item);content.addView(SocialViews.button(this,getString(R.string.wiring_retry),this::load));return;}if(!done.getResult().exists()){message(R.string.wiring_unavailable_item);return;}if(Long.valueOf(2).equals(done.getResult().getLong("schemaVersion"))){startActivity(new Intent(this,AdoptionFlowActivity.class).putExtra("mode","detail").putExtra("listing",id));finish();}else render(done.getResult());});}
 @SuppressWarnings("unchecked") private void render(DocumentSnapshot row){content.removeAllViews();
  content.addView(SocialViews.text(this,row.getString("petName"),true));ImageView image=new ImageView(this);image.setImageResource("cat".equals(row.getString("bundledIllustrationKey"))?R.drawable.profile_avatar_cat:R.drawable.profile_avatar_dog);image.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);content.addView(image,new LinearLayout.LayoutParams(-1,SocialViews.dp(this,180)));
  content.addView(SocialViews.text(this,row.getString("title"),false));content.addView(SocialSurface.muted(this,row.getString("regionLabel")));content.addView(SocialViews.text(this,getString("reserved".equals(row.getString("availability"))?R.string.flow_reserved:R.string.flow_available),true));
  Map<String,Object> cues=(Map<String,Object>)row.get("careCues");String health=cues==null?"unknown":(String)cues.get("healthState");content.addView(SocialViews.text(this,getString("concerns_reported".equals(health)?R.string.flow_care_concerns:"no_concerns_reported".equals(health)?R.string.flow_care_reported:R.string.flow_care_unknown),false));
  TextView host=SocialSurface.muted(this,getString(R.string.flow_host)+": "+getString(R.string.social_member));content.addView(host);
  new com.gmail.manhle240406.project.profile.ProfileRepository(FirebaseFirestore.getInstance(),sessions).readPublic(ticket,row.getString("ownerUid")).addOnCompleteListener(done->{if(current()&&done.isSuccessful()&&done.getResult().code==com.gmail.manhle240406.project.profile.ProfileRepository.Code.OK&&done.getResult().value!=null){Object label=done.getResult().value.get("displayName");if(label instanceof String)host.setText(getString(R.string.flow_host)+": "+label);}});
  MaterialButton save=SocialViews.primaryButton(this,getString(R.string.wiring_loading),()->{});save.setId(R.id.flow_detail_save);save.setEnabled(false);content.addView(save);
  FirebaseFirestore.getInstance().document("users/"+ticket.uid+"/favorites/"+id).get(Source.SERVER).addOnCompleteListener(done->{if(current()&&done.isSuccessful()){saved=done.getResult().exists();save.setText(saved?R.string.wiring_remove_saved:R.string.wiring_save);save.setEnabled(true);}});
  save.setOnClickListener(v->{if(mutation||!current())return;mutation=true;save.setEnabled(false);boolean desired=!saved;FavoriteActions.set(FirebaseFirestore.getInstance(),sessions,ticket,favorites,id,desired).addOnCompleteListener(done->{if(!current())return;mutation=false;if(done.isSuccessful())saved=desired;save.setEnabled(true);save.setText(saved?R.string.wiring_remove_saved:R.string.wiring_save);com.google.android.material.snackbar.Snackbar.make(content,done.isSuccessful()?R.string.wiring_saved:R.string.wiring_unknown,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();});});
  MaterialButton share=SocialViews.button(this,getString(R.string.flow_share),()->DemoSharing.open(this,"l",id));share.setVisibility(com.gmail.manhle240406.project.BuildConfig.DEBUG?android.view.View.VISIBLE:android.view.View.GONE);share.setId(R.id.flow_detail_share);content.addView(share);
  content.addView(SocialViews.button(this,getString(R.string.report_title),()->explain(R.string.report_title,R.string.wiring_service_unavailable)));
  content.addView(SocialViews.button(this,getString(R.string.flow_apply),()->startActivity(new Intent(this,MyApplicationsActivity.class).putExtra("listing",id))));
 }
 private void explain(int title,int message){dialog=new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message).setPositiveButton(android.R.string.ok,null).show();}
 @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();if(reader!=null)reader.close();if(favorites!=null)favorites.close();if(sessions!=null)sessions.removeInvalidation(invalidated);super.onDestroy();}
}
