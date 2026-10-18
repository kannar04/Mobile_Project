package com.gmail.manhle240406.project.community;
import android.content.*;
import android.view.*;
import android.widget.*;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.*;
import java.util.Map;
/** A current listing reference, never a copied/private pet or application record. */
public final class AdoptionPostCard extends LinearLayout {
 private Object binding;private Map<String,Object> post;private ListingDiscoveryRepository reader;private Runnable refresh;private SessionGateway sessions;private Runnable invalidation;private String listing;private final TextView label;private final com.google.android.material.button.MaterialButton open;
 public AdoptionPostCard(Context c){super(c);setOrientation(VERTICAL);label=SocialViews.text(c,"",true);addView(label);open=SocialSurface.quiet(c,c.getString(R.string.feed_open_listing),()->{});addView(open);}
 public void bind(Map<String,Object> post){this.post=post;String id=post!=null&&"adoption".equals(post.get("type"))?(String)post.get("listingId"):null;if(id!=null&&id.equals(listing)&&reader!=null)return;stop();setVisibility(id==null?GONE:VISIBLE);open.setVisibility(GONE);if(id==null)return;listing=id;label.setText(R.string.social_loading);Object b=new Object();binding=b;
  sessions=((AniloveApplication)getContext().getApplicationContext()).foundation().sessions;SessionEpoch.Ticket ticket=sessions==null?null:sessions.capture();if(ticket==null||ticket.uid==null){label.setText(R.string.feed_listing_unavailable);return;}
  invalidation=()->{stop();label.setText(R.string.feed_listing_unavailable);open.setVisibility(GONE);};sessions.onInvalidated(invalidation);
  reader=new ListingDiscoveryRepository(FirebaseFirestore.getInstance(),sessions);
  refresh=()->{if(binding!=b||!isShown())return;label.setText(R.string.social_loading);open.setVisibility(GONE);reader.clear();reader.detail(ticket,id).addOnCompleteListener(done->{if(binding!=b||!sessions.current(ticket))return;if(!done.isSuccessful()||done.getResult()==null){label.setText(R.string.feed_listing_unavailable);return;}Map<String,Object> d=done.getResult().getData();label.setText(getContext().getString(R.string.feed_listing_selected,d.get("petName"))+" ? "+getContext().getString("reserved".equals(d.get("availability"))?R.string.flow_reserved:R.string.flow_available));open.setVisibility(VISIBLE);open.setOnClickListener(v->{if(sessions.current(ticket))getContext().startActivity(new Intent(getContext(),ListingDetailActivity.class).putExtra("listing",id));});});};refresh.run();BrowsePulse.add(refresh);
 }
 private void stop(){binding=null;listing=null;if(refresh!=null){BrowsePulse.remove(refresh);refresh=null;}if(reader!=null){reader.close();reader=null;}if(sessions!=null&&invalidation!=null){sessions.removeInvalidation(invalidation);invalidation=null;}}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(reader==null)bind(post);else if(refresh!=null)refresh.run();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=VISIBLE)stop();else post(()->{if(isShown()&&isAttachedToWindow()&&reader==null&&post!=null)bind(post);});}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
