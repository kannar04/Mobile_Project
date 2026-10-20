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
 private androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh;
 private RecyclerView stream;private LinearLayoutManager streamLayout;private View header;private AuthorLookup authors;
 private final androidx.activity.result.ActivityResultLauncher<Intent> composer=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),r->{if(r.getResultCode()==android.app.Activity.RESULT_OK&&this.model!=null)refresh();});
 private final Map<String,Boolean> petSaved=new HashMap<>();private final Set<String> petSavedReads=new HashSet<>(),petSavedFailures=new HashSet<>();private long petSavedGeneration;private long authorEpoch=-1;private String actionNotice="",lastReactionCommit; private DailyViewModel actions; private final Map<String,Map<String,Object>> likes=new HashMap<>(); private final Set<String> reactionReads=new HashSet<>(),reactionFailures=new HashSet<>();
 private final PendingReactionTracker reactionTracker=new PendingReactionTracker();private long reactionGeneration;private FeedViewModel model; private Rows posts,pets; private ListingDiscoveryRepository discovery; private FavoritesRepository favorites; private boolean savingFavorite;
 private SessionGateway sessions; private SessionEpoch.Ticket ticket; private boolean alive; private Runnable invalidation;
 @Override public View onCreateView(@NonNull LayoutInflater i,ViewGroup c,Bundle s){
  androidx.core.widget.NestedScrollView old=(androidx.core.widget.NestedScrollView)i.inflate(R.layout.fragment_home,c,false);
  header=old.getChildAt(0);old.removeView(header);View oldFeed=header.findViewById(R.id.home_posts_recycler);((ViewGroup)oldFeed.getParent()).removeView(oldFeed);
  refresh=new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(requireContext());refresh.setId(R.id.home_refresh);refresh.setBackgroundResource(R.color.anilove_bg);
  stream=new RecyclerView(requireContext());stream.setId(R.id.home_posts_recycler);stream.setClipToPadding(false);stream.setPadding(SocialViews.dp(requireContext(),16),0,SocialViews.dp(requireContext(),16),SocialViews.dp(requireContext(),16));
  header.setPadding(0,SocialViews.dp(requireContext(),16),0,0);refresh.addView(stream,new ViewGroup.LayoutParams(-1,-1));return refresh;
 }
 @Override public void onViewCreated(@NonNull View view,Bundle state){
  FirebaseEnvironment env=((AniloveApplication)requireActivity().getApplication()).foundation();sessions=env.sessions;
  ticket=sessions==null?null:sessions.capture();if(ticket!=null&&ticket.uid!=null)authors=new AuthorLookup(sessions);
  alive=true;actions=new ViewModelProvider(this).get(DailyViewModel.class);
  ((AniloveApplication)requireActivity().getApplication()).communityOutbox().entries().observe(getViewLifecycleOwner(),pending->{java.util.Set<String> completed=reactionTracker.completedPosts(pending);if(!completed.isEmpty()){reactionGeneration++;for(String id:completed){likes.remove(id);reactionFailures.remove(id);}reactionReads.clear();}if(posts!=null)posts.reactionsChanged();});
  actions.changes.observe(getViewLifecycleOwner(),ignored->{if(posts==null)return;if(actions.committed&&!Objects.equals(lastReactionCommit,actions.lastCommittedSocial)){lastReactionCommit=actions.lastCommittedSocial;likes.remove(actions.postId);reactionFailures.remove(actions.postId);}posts.reactionsChanged();
   if(!actions.busy&&(actions.pending!=null||"local_failed".equals(actions.notice))&&!actions.notice.equals(actionNotice)&&getView()!=null){actionNotice=actions.notice;com.google.android.material.snackbar.Snackbar.make(requireView(),DailyCopy.notice(requireContext(),actions.notice),com.google.android.material.snackbar.Snackbar.LENGTH_LONG).setAction(R.string.social_retry,v->actions.retry()).show();}
  });
  com.google.android.material.button.MaterialButton chat=SocialSurface.quiet(requireContext(),"",()->((MainActivity)requireActivity()).navigate("chat"));chat.setIconResource(R.drawable.ic_nav_chat);chat.setContentDescription(getString(R.string.nav_chat));chat.setMinWidth(SocialViews.dp(requireContext(),48));chat.setMinimumHeight(SocialViews.dp(requireContext(),48));((android.view.ViewGroup)header).addView(chat,1);
  View.OnClickListener compose=v->composer.launch(new Intent(requireContext(),SocialComposerActivity.class));
  header.findViewById(R.id.home_create_post_card).setOnClickListener(compose);
  header.findViewById(R.id.home_post_status_btn).setOnClickListener(compose);
  header.findViewById(R.id.home_post_photo_btn).setVisibility(View.GONE);
  header.findViewById(R.id.home_post_photo_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));
  header.findViewById(R.id.home_post_status_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));
  header.findViewById(R.id.home_notifications_btn).setContentDescription(getString(R.string.flow_inbox));
  header.findViewById(R.id.home_filter_btn).setContentDescription(getString(R.string.flow_explore));
  header.findViewById(R.id.home_filter_btn).setOnClickListener(v->((MainActivity)requireActivity()).navigate("tools"));
  header.findViewById(R.id.home_notifications_btn).setOnClickListener(v->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("notifications"));
  View input=header.findViewById(R.id.home_search_input);
  input.setFocusable(false);input.setFocusableInTouchMode(false);input.setOnClickListener(v->browse());input.setContentDescription(getString(R.string.flow_explore));
  RecyclerView feed=stream;streamLayout=new LinearLayoutManager(requireContext());feed.setLayoutManager(streamLayout);feed.setItemAnimator(null);
  posts=new Rows(false);feed.setAdapter(new ConcatAdapter(new Header(),posts));
  refresh.setOnRefreshListener(this::refresh);
  androidx.core.view.MenuHost menus=(androidx.core.view.MenuHost)requireActivity();menus.addMenuProvider(new androidx.core.view.MenuProvider(){public void onCreateMenu(Menu menu,MenuInflater inflater){menu.add(R.string.social_refresh).setOnMenuItemClickListener(item->{refresh();return true;});}public boolean onMenuItemSelected(MenuItem item){return false;}},getViewLifecycleOwner(),androidx.lifecycle.Lifecycle.State.RESUMED);
  feed.addOnScrollListener(new RecyclerView.OnScrollListener(){public void onScrolled(RecyclerView v,int dx,int dy){checkpoint();if(dy>0&&streamLayout.findLastVisibleItemPosition()>=posts.rows.size()-3&&!model.failed)model.load(false);else if(dy<0&&streamLayout.findFirstVisibleItemPosition()<=4)model.previous();}});
  RecyclerView grid=header.findViewById(R.id.home_pet_recycler);GridLayoutManager petLayout=new GridLayoutManager(requireContext(),2);petLayout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){public int getSpanSize(int position){return pets.getItemViewType(position)==1?2:1;}});grid.setLayoutManager(petLayout);
  pets=new Rows(true);grid.setAdapter(pets);grid.setNestedScrollingEnabled(false);
  model=new ViewModelProvider(this).get(FeedViewModel.class);
  model.changes.observe(getViewLifecycleOwner(),n->{if(!alive)return;posts.message=model.failed?R.string.wiring_failed:model.busy?R.string.wiring_loading:R.string.wiring_empty;posts.replace(model.window.rows());refresh.setRefreshing(model.refreshing);int position=model.window.position(model.anchor);if(position>=0)streamLayout.scrollToPositionWithOffset(position+1,model.offset);else if(!model.busy&&model.refreshed){stream.scrollToPosition(0);model.refreshed=false;}});
  // Continue reading in the existing feed, with its bounded pagination and saved anchor.

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
 private void browse(){View input=header.findViewById(R.id.home_search_input);((MainActivity)requireActivity()).discovery(input instanceof EditText?((EditText)input).getText().toString():"",false);}
 private void refresh(){if(model==null||model.busy){if(refresh!=null)refresh.setRefreshing(false);return;}petSavedGeneration++;petSaved.clear();petSavedReads.clear();petSavedFailures.clear();if(pets!=null&&!pets.rows.isEmpty())pets.notifyItemRangeChanged(0,pets.rows.size());reactionGeneration++;reactionReads.clear();reactionFailures.clear();likes.clear();if(authors!=null)authors.clear();if(posts!=null)posts.identitiesChanged();model.refresh();loadPets();}
 private final Runnable refreshListings=()->{if(discovery!=null&&alive&&!isHidden()){discovery.clear();pets.replace(Collections.emptyList());loadPets();}};
 private void loadPets(){if(discovery==null||!alive||!sessions.current(ticket))return;discovery.page(ticket,new DiscoveryQuery("","available",Collections.emptyMap()),null).addOnCompleteListener(done->{if(!alive||!sessions.current(ticket))return;pets.message=done.isSuccessful()?R.string.wiring_empty:R.string.home_listing_unavailable;pets.replace(done.isSuccessful()?done.getResult().getDocuments():Collections.emptyList());});}
 private void checkpoint(){if(streamLayout==null||posts==null||model==null)return;int p=streamLayout.findFirstVisibleItemPosition()-1;View child=streamLayout.findViewByPosition(p+1);if(p>=0&&p<posts.rows.size()&&child!=null)model.checkpoint(posts.rows.get(p).getId(),child.getTop());}
 private void refreshAuthorEpoch(){long epoch=SessionAliases.generation();if(authorEpoch!=epoch){authorEpoch=epoch;if(posts!=null)posts.identitiesChanged();}}
 @Override public void onResume(){super.onResume();refreshAuthorEpoch();}
 @Override public void onStart(){super.onStart();if(model!=null&&!isHidden())model.start();com.gmail.manhle240406.project.adoption.BrowsePulse.add(refreshListings);refreshListings.run();}
 @Override public void onStop(){com.gmail.manhle240406.project.adoption.BrowsePulse.remove(refreshListings);checkpoint();if(model!=null)model.stop();super.onStop();}
 @Override public void onHiddenChanged(boolean hidden){super.onHiddenChanged(hidden);if(hidden)com.gmail.manhle240406.project.adoption.BrowsePulse.remove(refreshListings);else{refreshAuthorEpoch();com.gmail.manhle240406.project.adoption.BrowsePulse.add(refreshListings);refreshListings.run();}if(model!=null){if(hidden){checkpoint();}else if(getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))model.start();}}
 @Override public void onDestroyView(){alive=false;reactionGeneration++;if(sessions!=null&&invalidation!=null)sessions.removeInvalidation(invalidation);if(authors!=null)authors.close();if(discovery!=null)discovery.close();if(favorites!=null)favorites.close();likes.clear();petSavedGeneration++;petSaved.clear();petSavedReads.clear();petSavedFailures.clear();reactionReads.clear();reactionFailures.clear();posts=null;pets=null;stream=null;header=null;refresh=null;super.onDestroyView();}
 private void paintSaved(ImageView heart,boolean active){heart.setImageResource(active?R.drawable.ic_nav_favorite:R.drawable.ic_flow_favorite_outline);heart.setImageTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(),active?R.color.anilove_primary:R.color.anilove_text_muted)));heart.setContentDescription(getString(active?R.string.wiring_remove_saved:R.string.wiring_save));heart.setSelected(active);}
 private void bindReaction(DocumentSnapshot row,TextView heart){
  String id=row.getId();heart.setTag(id);CommunityOutbox.Entry pending=((AniloveApplication)requireActivity().getApplication()).communityOutbox().pendingLike(id);Map<String,Object> previous=likes.get(id);boolean active=pending!=null?Boolean.TRUE.equals(pending.command.input.get("active")):previous!=null&&Boolean.TRUE.equals(previous.get("active"));
  heart.setText(active?R.string.ui_unlike:R.string.ui_like);heart.setContentDescription(getString(active?R.string.social_unlike:R.string.social_like_action)+(pending==null?"":" - "+getString(R.string.community_pending)));heart.setSelected(active);heart.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(requireContext(),active?R.color.anilove_primary:R.color.anilove_text_muted)));
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
   posts.reactionChanged(id);
  });
 }
 private static final class CardRow {
  final DocumentSnapshot document;final String id;final Map<String,Object> data;
  CardRow(Object value){document=value instanceof DocumentSnapshot?(DocumentSnapshot)value:null;if(document!=null){id=document.getId();data=document.getData();}else{com.gmail.manhle240406.project.adoption.ListingRow r=(com.gmail.manhle240406.project.adoption.ListingRow)value;id=r.getId();data=r.getData();}}
  String getId(){return id;}Map<String,Object> getData(){return data;}String getString(String k){return (String)data.get(k);}Object get(String k){return data.get(k);}com.google.firebase.Timestamp getTimestamp(String k){return (com.google.firebase.Timestamp)data.get(k);}
 }
 private final class Rows extends RecyclerView.Adapter<Holder>{
  final boolean listing; final List<CardRow> rows=new ArrayList<>();int message=R.string.wiring_loading;
  Rows(boolean listing){this.listing=listing;}
  void identitiesChanged(){if(!listing&&!rows.isEmpty())notifyItemRangeChanged(0,rows.size(),"identity");}
  void reactionsChanged(){if(!rows.isEmpty())notifyItemRangeChanged(0,rows.size(),"reaction");}
  void reactionChanged(String id){for(int p=0;p<rows.size();p++)if(rows.get(p).getId().equals(id)){notifyItemChanged(p,"reaction");return;}}
  @Override public void onBindViewHolder(@NonNull Holder h,int p,@NonNull List<Object> payloads){if(!listing&&!payloads.isEmpty()&&p<rows.size()){if(payloads.contains("identity"))AuthorIdentity.bind(authors,rows.get(p).getString("authorUid"),h.itemView.findViewById(R.id.post_author_name),h.itemView.findViewById(R.id.post_author_avatar));if(payloads.contains("reaction"))bindReaction(rows.get(p).document,h.itemView.findViewById(R.id.post_like_btn));return;}onBindViewHolder(h,p);}
  void replace(List<?> values){List<CardRow> next=new ArrayList<>();for(Object v:values.subList(0,Math.min(listing?4:100,values.size())))next.add(new CardRow(v));List<CardRow> old=new ArrayList<>(rows);androidx.recyclerview.widget.DiffUtil.DiffResult diff=androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback(){public int getOldListSize(){return old.isEmpty()?1:old.size()+(listing?0:1);}public int getNewListSize(){return next.isEmpty()?1:next.size()+(listing?0:1);}public boolean areItemsTheSame(int a,int b){return a>=old.size()||b>=next.size()?a>=old.size()&&b>=next.size():old.get(a).getId().equals(next.get(b).getId());}public boolean areContentsTheSame(int a,int b){return a<old.size()&&b<next.size()&&Objects.equals(old.get(a).getData(),next.get(b).getData());}});rows.clear();rows.addAll(next);if(listing){Set<String> retained=new HashSet<>();for(CardRow row:rows)retained.add(row.id);petSaved.keySet().retainAll(retained);}if(!listing){Set<String> retained=new HashSet<>();for(CardRow row:rows)retained.add(row.getId());likes.keySet().retainAll(retained);reactionFailures.retainAll(retained);}diff.dispatchUpdatesTo(this);}

  public int getItemCount(){return rows.isEmpty()?1:rows.size()+(listing?0:1);}
  public int getItemViewType(int p){return rows.isEmpty()||p==rows.size()?1:0;}
  @NonNull public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){
   if(type==1){TextView b=SocialViews.text(parent.getContext(),"",false);b.setGravity(Gravity.CENTER);b.setMinHeight(SocialViews.dp(parent.getContext(),48));b.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(b);}
   return new Holder(LayoutInflater.from(parent.getContext()).inflate(listing?R.layout.item_pet_discovery:R.layout.item_post,parent,false));
  }
  public void onBindViewHolder(@NonNull Holder h,int p){
   if(rows.isEmpty()||p==rows.size()){((TextView)h.itemView).setText(rows.isEmpty()?message:model.failed?R.string.social_retry:model.busy?R.string.social_loading:model.end?R.string.social_end:R.string.social_reading);h.itemView.setEnabled(listing||model.failed);h.itemView.setOnClickListener(v->{if(listing)browse();else model.load(false);});return;}
   CardRow row=rows.get(p);
   if(listing){
    ((TextView)h.itemView.findViewById(R.id.pet_name)).setText(row.getString("petName"));
    ((TextView)h.itemView.findViewById(R.id.pet_breed_age)).setText(row.getString("title"));
    ((TextView)h.itemView.findViewById(R.id.pet_region)).setText(row.getString("regionLabel"));
    ((ImageView)h.itemView.findViewById(R.id.pet_image)).setImageResource("cat".equals(row.getString("bundledIllustrationKey"))?R.drawable.profile_avatar_cat:R.drawable.profile_avatar_dog);
    View.OnClickListener open=v->startActivity(new Intent(requireContext(),ListingDetailActivity.class).putExtra("listing",row.getId()));
    h.itemView.setOnClickListener(open);View favorite=h.itemView.findViewById(R.id.pet_favorite_btn);favorite.setTag(row.getId());favorite.setContentDescription(getString(R.string.wiring_save));favorite.setEnabled(!savingFavorite&&petSaved.containsKey(row.getId()));paintSaved((ImageView)favorite,Boolean.TRUE.equals(petSaved.get(row.getId())));if(!petSaved.containsKey(row.getId())&&!petSavedFailures.contains(row.getId())&&petSavedReads.add(row.getId())){long favoriteGeneration=petSavedGeneration;FirebaseFirestore.getInstance().document("users/"+ticket.uid+"/favorites/"+row.getId()).get(Source.SERVER).addOnCompleteListener(read->{if(favoriteGeneration!=petSavedGeneration||!alive||!sessions.current(ticket))return;petSavedReads.remove(row.getId());if(!read.isSuccessful())petSavedFailures.add(row.getId());if(read.isSuccessful()){petSaved.put(row.getId(),read.getResult().exists());if(row.getId().equals(favorite.getTag())){favorite.setEnabled(!savingFavorite);paintSaved((ImageView)favorite,read.getResult().exists());}}});}favorite.setOnClickListener(v->{
     if(savingFavorite||favorites==null||!sessions.current(ticket))return;savingFavorite=true;v.setEnabled(false);
     com.gmail.manhle240406.project.wiring.FavoriteActions.set(FirebaseFirestore.getInstance(),sessions,ticket,favorites,row.getId(),!Boolean.TRUE.equals(petSaved.get(row.getId()))).addOnCompleteListener(done->{
      if(!alive||!sessions.current(ticket))return;savingFavorite=false;if(done.isSuccessful())petSaved.put(row.getId(),!Boolean.TRUE.equals(petSaved.get(row.getId())));if(row.getId().equals(v.getTag())){v.setEnabled(true);paintSaved((ImageView)v,Boolean.TRUE.equals(petSaved.get(row.getId())));}
      com.google.android.material.snackbar.Snackbar.make(requireView(),done.isSuccessful()?R.string.wiring_saved:R.string.wiring_unknown,com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
     });
    });
   }else{
    SocialPostCard.content(requireContext(),h.itemView,row.getData(),row.id,authors,null);

    TextView heart=h.itemView.findViewById(R.id.post_like_btn);heart.setText(R.string.social_like_action);bindReaction(row.document,heart);h.itemView.findViewById(R.id.post_like_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));h.itemView.findViewById(R.id.post_comment_btn).setMinimumHeight(SocialViews.dp(requireContext(),48));
    ((TextView)h.itemView.findViewById(R.id.post_comment_btn)).setText(R.string.social_open);
    View.OnClickListener open=v->startActivity(new Intent(requireContext(),SocialDetailActivity.class).putExtra("post",row.getId()));
    h.itemView.setOnClickListener(open);h.itemView.findViewById(R.id.post_comment_btn).setOnClickListener(open);
    View share=h.itemView.findViewById(R.id.post_share_btn);share.setVisibility(com.gmail.manhle240406.project.BuildConfig.DEBUG?View.VISIBLE:View.GONE);share.setMinimumHeight(SocialViews.dp(requireContext(),48));share.setOnClickListener(v->DemoSharing.open(requireActivity(),"p",row.getId()));
   }
  }
 }
 private final class Header extends RecyclerView.Adapter<Holder>{public int getItemCount(){return 1;}public Holder onCreateViewHolder(ViewGroup p,int type){return new Holder(header);}public void onBindViewHolder(Holder h,int p){}}
 private static final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
}
