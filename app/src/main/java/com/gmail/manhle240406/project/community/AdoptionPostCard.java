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
 public AdoptionPostCard(Context c){this(c,null);}
 public AdoptionPostCard(Context c,android.util.AttributeSet attrs){super(c,attrs);setOrientation(VERTICAL);label=SocialViews.text(c,"",true);addView(label);open=SocialSurface.quiet(c,c.getString(R.string.feed_open_listing),()->{});addView(open,new LinearLayout.LayoutParams(-1,-2));}
 public void bind(Map<String,Object> post){this.post=post;String id=post!=null&&"adoption".equals(post.get("type"))?(String)post.get("listingId"):null;if(id!=null&&id.equals(listing)&&reader!=null)return;stop();setVisibility(id==null?GONE:VISIBLE);open.setVisibility(GONE);if(id==null)return;listing=id;label.setText(R.string.social_loading);Object b=new Object();binding=b;
  sessions=((AniloveApplication)getContext().getApplicationContext()).foundation().sessions;if(sessions==null){label.setText(R.string.feed_listing_unavailable);return;}invalidation=()->{stop();label.setText(R.string.social_loading);post(()->{if(isAttachedToWindow()&&isShown()&&post!=null)bind(post);});};sessions.onInvalidated(invalidation);SessionEpoch.Ticket ticket=sessions.capture();if(ticket.uid==null){label.setText(R.string.feed_listing_unavailable);open.setText(R.string.social_retry);open.setVisibility(VISIBLE);open.setOnClickListener(v->invalidation.run());return;}
  reader=new ListingDiscoveryRepository(FirebaseFirestore.getInstance(),sessions);
  refresh=()->{if(binding!=b||!isShown())return;label.setText(R.string.social_loading);open.setVisibility(GONE);reader.clear();reader.reference(ticket,id).addOnCompleteListener(done->{if(binding!=b)return;if(!sessions.current(ticket)){invalidation.run();return;}if(done.isSuccessful()&&"adopted".equals(done.getResult().terminalStatus)){label.setText(R.string.feed_listing_adopted);open.setVisibility(GONE);return;}if(!done.isSuccessful()||done.getResult().row==null){if(!done.isSuccessful())android.util.Log.w("AdoptionPostCard","Listing load failed: "+id,done.getException());label.setText(R.string.feed_listing_unavailable);open.setText(R.string.social_retry);open.setVisibility(VISIBLE);open.setOnClickListener(v->refresh.run());return;}Map<String,Object> d=done.getResult().row.getData();label.setText(getContext().getString(R.string.feed_listing_selected,d.get("petName"))+" - "+getContext().getString("reserved".equals(d.get("availability"))?R.string.flow_reserved:R.string.flow_available));open.setText(R.string.feed_open_listing);open.setVisibility(VISIBLE);open.setOnClickListener(v->{if(sessions.current(ticket))getContext().startActivity(new Intent(getContext(),ListingDetailActivity.class).putExtra("listing",id));else invalidation.run();});});};refresh.run();BrowsePulse.add(refresh);
 }
 private void stop(){binding=null;listing=null;if(refresh!=null){BrowsePulse.remove(refresh);refresh=null;}if(reader!=null){reader.close();reader=null;}if(sessions!=null&&invalidation!=null){sessions.removeInvalidation(invalidation);invalidation=null;}}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(reader==null)bind(post);else if(refresh!=null)refresh.run();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility!=VISIBLE)stop();else post(()->{if(isShown()&&isAttachedToWindow()&&reader==null&&post!=null)bind(post);});}
 @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}
