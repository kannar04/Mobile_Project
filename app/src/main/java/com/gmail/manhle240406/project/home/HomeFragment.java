package com.gmail.manhle240406.project.home;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.*;
import java.util.*;

/** Existing home layout bound to authorized listings and the retained real community feed. */
public final class HomeFragment extends Fragment {
 private String actionNotice=""; private DailyViewModel actions; private final Map<String,Map<String,Object>> likes=new HashMap<>(); private final Set<String> reactionReads=new HashSet<>(),reactionFailures=new HashSet<>();
 private final PendingReactionTracker reactionTracker=new PendingReactionTracker();private long reactionGeneration;private FeedViewModel model; private Rows posts,pets; private ListingDiscoveryRepository discovery; private FavoritesRepository favorites; private boolean savingFavorite;
 private SessionGateway sessions; private SessionEpoch.Ticket ticket; private boolean alive; private Runnable invalidation;
 @Override public View onCreateView(@NonNull LayoutInflater i,ViewGroup c,Bundle s){return i.inflate(R.layout.fragment_home,c,false);}
 @Override public void onViewCreated(@NonNull View view,Bundle state){
  alive=true;actions=new ViewModelProvider(this).get(DailyViewModel.class);
  ((AniloveApplication)requireActivity().getApplication()).communityOutbox().entries().observe(getViewLifecycleOwner(),pending->{java.util.Set<String> completed=reactionTracker.completedPosts(pending);if(!completed.isEmpty()){reactionGeneration++;for(String id:completed){likes.remove(id);reactionFailures.remove(id);}reactionReads.clear();}if(posts!=null)posts.notifyDataSetChanged();});
  actions.changes.observe(getViewLifecycleOwner(),ignored->{if(posts==null)return;if(actions.committed){likes.clear();reactionFailures.clear();}posts.notifyDataSetChanged();
   if(!actions.busy&&(actions.pending!=null||"local_failed".equals(actions.notice))&&!actions.notice.equals(actionNotice)&&getView()!=null){actionNotice=actions.notice;com.google.android.material.snackbar.Snackbar.make(requireView(),DailyCopy.notice(requireContext(),actions.notice),com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setAction(R.string.social_retry,v->actions.retry()).show();}
  });
  View.OnClickListener compose=v->startActivity(new Intent(requireContext(),SocialComposerActivity.class));
  view.findViewById(R.id.home_create_post_card).setOnClickListener(compose);
  view.findViewById(R.id.home_post_status_btn).setOnClickListener(compose);
  view.findViewById(R.id.home_post_photo_btn).setOnClickListener(v->startActivity(new Intent(requireContext(),com.gmail.manhle240406.project.media.PhotoGalleryActivity.class)));
  view.findViewById(R.id.home_post_photo_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));
  view.findViewById(R.id.home_post_status_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));
  view.findViewById(R.id.home_notifications_btn).setContentDescription(getString(R.string.flow_inbox));
  view.findViewById(R.id.home_filter_btn).setContentDescription(getString(R.string.flow_explore));
  view.findViewById(R.id.home_filter_btn).setOnClickListener(v->startActivity(new Intent(requireContext(),com.gmail.manhle240406.project.wiring.FlowHubActivity.class)));
  view.findViewById(R.id.home_notifications_btn).setOnClickListener(v->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("notifications"));
  View input=view.findViewById(R.id.home_search_input);
  if(input instanceof EditText)((EditText)input).setOnEditorActionListener((v,action,event)->{browse();return true;});
  RecyclerView feed=view.findViewById(R.id.home_posts_recycler);feed.setLayoutManager(new LinearLayoutManager(requireContext()));
  posts=new Rows(false);feed.setAdapter(posts);feed.setNestedScrollingEnabled(false);
  RecyclerView grid=view.findViewById(R.id.home_pet_recycler);GridLayoutManager petLayout=new GridLayoutManager(requireContext(),2);petLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){public int getSpanSize(int position){return pets.getItemViewType(position)==1?2:1;}});grid.setLayoutManager(petLayout);
  pets=new Rows(true);grid.setAdapter(pets);grid.setNestedScrollingEnabled(false);
  model=new ViewModelProvider(this).get(FeedViewModel.class);
  model.changes.observe(getViewLifecycleOwner(),n->{posts.replace(model.window.rows());posts.message=model.failed?R.string.wiring_failed:model.busy?R.string.wiring_loading:R.string.wiring_empty;posts.notifyDataSetChanged();});
  // Continue reading in the existing feed, with its bounded pagination and saved anchor.
  feed.setOnClickListener(v->openFeed());
  FirebaseEnvironment env=((AniloveApplication)requireActivity().getApplication()).foundation();sessions=env.sessions;
  ticket=sessions==null?null:sessions.capture();
  if(ticket!=null&&ticket.uid!=null){
   invalidation=()->{if(alive){posts.replace(Collections.emptyList());pets.replace(Collections.emptyList());}};sessions.onInvalidated(invalidation);
   discovery=new ListingDiscoveryRepository(FirebaseFirestore.getInstance(),sessions);favorites=new FavoritesRepository(FirebaseFirestore.getInstance(),sessions);
   discovery.page(ticket,new DiscoveryQuery("","available",Collections.emptyMap()),null).addOnCompleteListener(done->{
    if(!alive||!sessions.current(ticket))return;
    pets.message=done.isSuccessful()?R.string.wiring_empty:R.string.home_listing_unavailable;
    pets.replace(done.isSuccessful()?done.getResult().getDocuments():Collections.emptyList());
   });
  }
 }
 private void browse(){Intent intent=new Intent(requireContext(),DiscoveryActivity.class);View input=requireView().findViewById(R.id.home_search_input);if(input instanceof EditText)intent.putExtra("search",((EditText)input).getText().toString());startActivity(intent);}
 private void openFeed(){startActivity(new Intent(requireContext(),SocialFeedActivity.class));}
 @Override public void onStart(){super.onStart();if(model!=null&&!isHidden())model.start();}
 @Override public void onStop(){if(model!=null)model.stop();super.onStop();}
 @Override public void onHiddenChanged(boolean hidden){super.onHiddenChanged(hidden);if(model!=null){if(hidden)model.stop();else if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))model.start();}}
 @Override public void onDestroyView(){alive=false;reactionGeneration++;if(sessions!=null&&invalidation!=null)sessions.removeInvalidation(invalidation);if(discovery!=null)discovery.close();if(favorites!=null)favorites.close();likes.clear();reactionReads.clear();reactionFailures.clear();posts=null;pets=null;super.onDestroyView();}
 private void bindReaction(DocumentSnapshot row,TextView heart){
  String id=row.getId();heart.setTag(id);CommunityOutbox.Entry pending=((AniloveApplication)requireActivity().getApplication()).communityOutbox().pendingLike(id);Map<String,Object> previous=likes.get(id);boolean active=pending!=null?Boolean.TRUE.equals(pending.command.input.get("active")):previous!=null&&Boolean.TRUE.equals(previous.get("active"));
  heart.setText(active?R.string.social_unlike:R.string.social_like_action);heart.setContentDescription(heart.getText()+(pending==null?"":" - "+getString(R.string.community_pending)));heart.setSelected(active);
  heart.setCompoundDrawablesRelativeWithIntrinsicBounds(pending!=null?R.drawable.ic_social_pending:active?R.drawable.ic_nav_favorite:R.drawable.ic_flow_favorite_outline,0,0,0);
  heart.setEnabled(likes.containsKey(id)&&!actions.busy&&actions.pending==null&&!actions.restored&&(pending==null||pending.replaceable()));
  androidx.core.view.ViewCompat.setAccessibilityDelegate(heart,new androidx.core.view.AccessibilityDelegateCompat(){@Override public void onInitializeAccessibilityNodeInfo(View host,androidx.core.view.accessibility.AccessibilityNodeInfoCompat info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(android.widget.Button.class.getName());}});
  heart.setOnClickListener(v->{
   if(!alive||!actions.current()||actions.busy||actions.pending!=null||actions.restored||!likes.containsKey(id))return;
   Map<String,Object> current=likes.get(id);CommunityOutbox.Entry queued=((AniloveApplication)requireActivity().getApplication()).communityOutbox().pendingLike(id);boolean desired=queued!=null?!Boolean.TRUE.equals(queued.command.input.get("active")):current==null||!Boolean.TRUE.equals(current.get("active"));
   com.gmail.manhle240406.project.auth.AuthState auth=((AniloveApplication)requireActivity().getApplication()).auth().state().getValue();
   if(desired&&(auth==null||!auth.interactionReady())){startActivity(new Intent(requireContext(),com.gmail.manhle240406.project.auth.AuthActivity.class).putExtra("account",true));return;}
   actions.send(new SocialCommand("set_like",id,null,SocialRepository.number(row.getData(),"contentVersion"),0,SocialRepository.number(current,"likeVersion"),SocialRepository.map("active",desired),UUID.randomUUID().toString()));
  });
  if(likes.containsKey(id)||reactionReads.size()>=4||reactionFailures.contains(id)||!actions.current()||!reactionReads.add(id))return;
  long requestGeneration=reactionGeneration;actions.social.read("communityPosts/"+id+"/likes/"+actions.ticket.uid).addOnCompleteListener(done->{
   if(!alive||!actions.current()||requestGeneration!=reactionGeneration)return;reactionReads.remove(id);
   if(done.isSuccessful())likes.put(id,done.getResult().getData());else reactionFailures.add(id);
   posts.notifyDataSetChanged();
  });
 }
 private final class Rows extends RecyclerView.Adapter<Holder>{
  final boolean listing; final List<DocumentSnapshot> rows=new ArrayList<>();int message=R.string.wiring_loading;
  Rows(boolean listing){this.listing=listing;}
  void replace(List<DocumentSnapshot> values){rows.clear();rows.addAll(values.subList(0,Math.min(listing?4:20,values.size())));notifyDataSetChanged();}
  public int getItemCount(){return rows.isEmpty()?1:rows.size()+(listing?0:1);}
  public int getItemViewType(int p){return rows.isEmpty()||p==rows.size()?1:0;}
  @NonNull public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
   if(type==1){com.google.android.material.button.MaterialButton b=SocialViews.button(parent.getContext(),"",()->{});b.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(b);}
   return new Holder(LayoutInflater.from(parent.getContext()).inflate(listing?R.layout.item_pet_discovery:R.layout.item_post,parent,false));
  }
  public void onBindViewHolder(@NonNull Holder h,int p){
   if(rows.isEmpty()||p==rows.size()){((TextView)h.itemView).setText(rows.isEmpty()?message:R.string.wiring_more);h.itemView.setOnClickListener(v->{if(listing)browse();else openFeed();});return;}
   DocumentSnapshot row=rows.get(p);
   if(listing){
    ((TextView)h.itemView.findViewById(R.id.pet_name)).setText(row.getString("petName"));
    ((TextView)h.itemView.findViewById(R.id.pet_breed_age)).setText(row.getString("title"));
    ((TextView)h.itemView.findViewById(R.id.pet_region)).setText(row.getString("regionLabel"));
    ((ImageView)h.itemView.findViewById(R.id.pet_image)).setImageResource("cat".equals(row.getString("bundledIllustrationKey"))?R.drawable.profile_avatar_cat:R.drawable.profile_avatar_dog);
    View.OnClickListener open=v->startActivity(new Intent(requireContext(),DiscoveryActivity.class).putExtra("listing",row.getId()));
    h.itemView.setOnClickListener(open);View favorite=h.itemView.findViewById(R.id.pet_favorite_btn);favorite.setTag(row.getId());favorite.setContentDescription(getString(R.string.wiring_save));favorite.setEnabled(!savingFavorite);favorite.setOnClickListener(v->{
     if(savingFavorite||favorites==null||!sessions.current(ticket))return;savingFavorite=true;v.setEnabled(false);
     com.gmail.manhle240406.project.wiring.FavoriteActions.set(FirebaseFirestore.getInstance(),sessions,ticket,favorites,row.getId(),true).addOnCompleteListener(done->{
      if(!alive||!sessions.current(ticket))return;savingFavorite=false;if(row.getId().equals(v.getTag()))v.setEnabled(true);
      com.google.android.material.snackbar.Snackbar.make(requireView(),done.isSuccessful()?R.string.wiring_saved:R.string.wiring_unknown,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
     });
    });
   }else{
    ((TextView)h.itemView.findViewById(R.id.post_author_name)).setText(R.string.social_member);
    ((TextView)h.itemView.findViewById(R.id.post_time)).setText(row.getTimestamp("firstPublishedAt")==null?"":java.text.DateFormat.getDateTimeInstance().format(row.getTimestamp("firstPublishedAt").toDate()));
    ((TextView)h.itemView.findViewById(R.id.post_body)).setText(row.getString("body"));
    ((ImageView)h.itemView.findViewById(R.id.post_image)).setImageResource("cat".equals(row.getString("bundledIllustrationKey"))?R.drawable.profile_avatar_cat:R.drawable.profile_avatar_dog);
    h.itemView.findViewById(R.id.post_image).setVisibility(row.getString("bundledIllustrationKey")==null?View.GONE:View.VISIBLE);
    TextView heart=h.itemView.findViewById(R.id.post_like_btn);heart.setText(R.string.social_like_action);bindReaction(row,heart);h.itemView.findViewById(R.id.post_like_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));h.itemView.findViewById(R.id.post_comment_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));
    ((TextView)h.itemView.findViewById(R.id.post_comment_btn)).setText(R.string.social_open);
    View.OnClickListener open=v->startActivity(new Intent(requireContext(),SocialDetailActivity.class).putExtra("post",row.getId()));
    h.itemView.setOnClickListener(open);h.itemView.findViewById(R.id.post_comment_btn).setOnClickListener(open);
    View share=h.itemView.findViewById(R.id.post_share_btn);share.setVisibility(com.gmail.manhle240406.project.BuildConfig.DEBUG?View.VISIBLE:View.GONE);share.setMinimumHeight(SocialViews.dp(requireContext(),48));share.setOnClickListener(v->DemoSharing.open(requireActivity(),"p",row.getId()));
   }
  }
 }
 private static final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
}
