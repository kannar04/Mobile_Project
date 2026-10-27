package com.gmail.manhle240406.project.community;

import android.os.Bundle;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.*;
import static com.gmail.manhle240406.project.community.SocialViews.*;

/** Native reading surface. All cards come from authorized schema2 server results. */
public class CommunityFeedActivity extends AppCompatActivity {
    private final PendingReactionTracker reactionTracker=new PendingReactionTracker();private long reactionGeneration,mediaGeneration;private AuthorLookup authors;
    private FeedViewModel model;
    private DailyViewModel actions;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh;
    private RecyclerView list;
    private LinearLayout root;
    private TextView service; private LinearLayout pendingPreview;
    private Adapter adapter;
    private LinearLayoutManager manager;
    private String reported,reacted;
    private final LinkedHashMap<String,Long> counts=new LinkedHashMap<>();
    private final LinkedHashMap<String,Map<String,Object>> likes=new LinkedHashMap<>();
    private final HashSet<String> rowReads=new HashSet<>();
    private androidx.appcompat.app.AlertDialog dialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_social_feed);root=findViewById(R.id.social_feed_root);insets(root);
        findViewById(R.id.social_feed_auth).setOnClickListener(v->startActivity(new Intent(this,AuthActivity.class)));
        findViewById(R.id.social_feed_compose).setOnClickListener(v->{
            com.gmail.manhle240406.project.auth.AuthState auth=((AniloveApplication)getApplication()).auth().state().getValue();
            startActivity(new Intent(this,auth==null||auth.uid==null?AuthActivity.class:SocialComposerActivity.class));
        });
        service=findViewById(R.id.social_feed_service);
        findViewById(R.id.social_feed_menu).setOnClickListener(v->menu());
        pendingPreview=findViewById(R.id.social_feed_pending);
        ((AniloveApplication)getApplication()).communityOutbox().entries().observe(this,queued->{Set<String> completed=reactionTracker.completedPosts(queued);if(!completed.isEmpty()){reactionGeneration++;for(String id:completed){likes.remove(id);counts.remove(id);}rowReads.clear();}checkpoint();pendingPreview.removeAllViews();int shown=0;for(CommunityOutbox.Entry e:queued)if("publish".equals(e.command.command)&&shown++<2){TextView draft=text(this,(String)e.command.input.get("body"),false);draft.setMaxLines(2);draft.setEllipsize(android.text.TextUtils.TruncateAt.END);pendingPreview.addView(draft);}if(!queued.isEmpty())pendingPreview.addView(button(this,getString(R.string.community_pending_actions)+" ("+queued.size()+")",()->startActivity(new Intent(this,PendingCommunityActivity.class))));if(adapter!=null)adapter.notifyDataSetChanged();});
        list=findViewById(R.id.social_feed_list);manager=new LinearLayoutManager(this);list.setLayoutManager(manager);list.setItemAnimator(null);list.setSaveEnabled(false);
        adapter=new Adapter();list.setAdapter(adapter);refresh=findViewById(R.id.social_feed_refresh);refresh.setOnRefreshListener(()->{mediaGeneration++;com.gmail.manhle240406.project.media.SessionImageCache.clear();counts.clear();likes.clear();if(authors!=null)authors.clear();adapter.notifyDataSetChanged();model.refresh();});
        model=new ViewModelProvider(this).get(FeedViewModel.class);
        actions=new ViewModelProvider(this).get(DailyViewModel.class);
        if(!model.lost)authors=new AuthorLookup(actions.sessions);
        model.changes.observe(this,x->render());
        actions.changes.observe(this,x->{
            if(actions.lost){counts.clear();likes.clear();rowReads.clear();if(dialog!=null)dialog.dismiss();return;}
            if(actions.lastCommittedSocial!=null&&!actions.lastCommittedSocial.equals(reacted)){reacted=actions.lastCommittedSocial;counts.remove(actions.postId);likes.remove(actions.postId);checkpoint();render();int updated=model.window.position(actions.postId);if(updated>=0)adapter.notifyItemChanged(updated,"reaction");Snackbar.make(root,R.string.social_committed,Snackbar.LENGTH_SHORT).show();}
            else if(actions.pending!=null||actions.restored)service.setText(DailyCopy.notice(this,actions.notice));
            if(actions.lastCommittedReport!=null&&!actions.lastCommittedReport.equals(reported)) {
                reported=actions.lastCommittedReport;
                Snackbar.make(root,R.string.report_received,Snackbar.LENGTH_LONG).setAction(R.string.report_mine,v->startActivity(new Intent(this,MyReportsActivity.class))).show();
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView v,int dx,int dy){
            checkpoint();if(dy>0&&manager.findLastVisibleItemPosition()>=adapter.rows.size()-5)model.load(false);
            else if(dy<0&&manager.findFirstVisibleItemPosition()<=5)model.previous();
        }});
        ((AniloveApplication)getApplication()).auth().state().observe(this,s->{
            if(s.uid==null){service.setText(R.string.social_signin);model.stop();}
            else if(model.lost)recreate();
        });
        privateView(root);
    }
    private void menu(){
        PopupMenu menu=new PopupMenu(this,service);
        if(actions.pending!=null||actions.restored){menu.getMenu().add(0,4,3,R.string.social_retry);menu.getMenu().add(0,5,4,R.string.social_reconcile);}
        menu.getMenu().add(0,7,6,R.string.community_notices);menu.getMenu().add(0,6,5,R.string.community_pending_actions);menu.getMenu().add(0,1,0,R.string.social_refresh);menu.getMenu().add(0,2,1,R.string.social_drafts);menu.getMenu().add(0,3,2,R.string.report_mine);
        menu.setOnMenuItemClickListener(item->{
            if(item.getItemId()==7){startActivity(new Intent(this,ModerationNoticesActivity.class));}
            else if(item.getItemId()==6){startActivity(new Intent(this,PendingCommunityActivity.class));}
            else if(item.getItemId()==1){counts.clear();likes.clear();model.refresh();}
            else if(item.getItemId()==2)startActivity(new Intent(this,SocialComposerActivity.class).putExtra("own",true));
            else if(item.getItemId()==3)startActivity(new Intent(this,MyReportsActivity.class));else if(item.getItemId()==4)actions.retry();else actions.reconcile();return true;
        });menu.show();
    }
    private void checkpoint(){
        if(model==null||manager==null||adapter==null)return;int p=manager.findFirstVisibleItemPosition();View v=manager.findViewByPosition(p);
        if(p>=0&&p<adapter.rows.size()&&v!=null)model.checkpoint(adapter.rows.get(p).getId(),v.getTop());
    }
    private void render(){
        if(model.lost){adapter.submit(Collections.emptyList());service.setText(R.string.social_signin);return;}
        refresh.setRefreshing(model.refreshing);adapter.submit(model.window.rows());if(model.refreshed){list.scrollToPosition(0);model.refreshed=false;}int p=model.window.position(model.anchor);
        if(p>=0)manager.scrollToPositionWithOffset(p,model.offset);
        service.setText(model.boundaryLost?R.string.social_boundary:model.newPosts?R.string.social_new_posts:R.string.social_reading);SocialSurface.hideEmpty(service);
    }
    private void like(DocumentSnapshot row){if(actions.busy||actions.pending!=null||actions.restored||!likes.containsKey(row.getId()))return;Map<String,Object> previous=likes.get(row.getId());CommunityOutbox.Entry queued=((AniloveApplication)getApplication()).communityOutbox().pendingLike(row.getId());boolean desired=queued!=null?!Boolean.TRUE.equals(queued.command.input.get("active")):previous==null||!Boolean.TRUE.equals(previous.get("active"));com.gmail.manhle240406.project.auth.AuthState auth=((AniloveApplication)getApplication()).auth().state().getValue();if(desired&&(auth==null||!auth.interactionReady())){startActivity(new Intent(this,AuthActivity.class));return;}actions.send(new SocialCommand("set_like",row.getId(),null,SocialRepository.number(row.getData(),"contentVersion"),0,SocialRepository.number(previous,"likeVersion"),SocialRepository.map("active",desired),UUID.randomUUID().toString()));}
    private void readReaction(DocumentSnapshot row,Holder holder,android.widget.TextView label,com.google.android.material.button.MaterialButton button,long binding){
        String id=row.getId();if(rowReads.size()>=4||!rowReads.add(id))return;
        long requestGeneration=reactionGeneration;com.google.android.gms.tasks.Task<com.google.firebase.firestore.DocumentSnapshot> reaction=actions.social.read("communityPosts/"+id+"/likes/"+actions.ticket.uid),count=actions.social.read("communityCounts/"+id);
        com.google.android.gms.tasks.Tasks.whenAllComplete(reaction,count).addOnCompleteListener(done->{if(requestGeneration!=reactionGeneration)return;rowReads.remove(id);if(!actions.current())return;
            if(reaction.isSuccessful())likes.put(id,reaction.getResult().getData());
            if(count.isSuccessful()){Map<String,Object> value=count.getResult().getData();if(value!=null&&value.get("likeCount") instanceof Long&&(Long)value.get("likeCount")>=0)counts.put(id,(Long)value.get("likeCount"));}
            while(likes.size()>100)likes.remove(likes.keySet().iterator().next());while(counts.size()>100)counts.remove(counts.keySet().iterator().next());
            if(holder.binding!=binding)return;Map<String,Object> current=likes.get(id);button.setText(current!=null&&Boolean.TRUE.equals(current.get("active"))?R.string.social_unlike:R.string.social_like_action);button.setEnabled(likes.containsKey(id)&&!actions.busy&&actions.pending==null&&!actions.restored);
            if(counts.containsKey(id))label.setText(getString(R.string.social_likes,counts.get(id)));CommunityOutbox.Entry pending=((AniloveApplication)getApplication()).communityOutbox().pendingLike(id);if(pending!=null){button.setText(Boolean.TRUE.equals(pending.command.input.get("active"))?R.string.social_unlike:R.string.social_like_action);button.setIconResource(R.drawable.ic_social_pending);button.setEnabled(pending.replaceable()&&!actions.busy);}
        });
    }
    private void options(DocumentSnapshot d,View anchor){
        checkpoint();PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add(0,1,0,R.string.report_title);if(com.gmail.manhle240406.project.BuildConfig.DEBUG)menu.getMenu().add(0,7,4,R.string.flow_share);menu.getMenu().add(0,2,1,R.string.profile_title);menu.getMenu().add(0,8,5,R.string.community_notices);
        if(actions.ticket.uid.equals(d.getString("authorUid")))menu.getMenu().add(0,3,2,R.string.social_edit);
        menu.setOnMenuItemClickListener(item->{
            if(item.getItemId()==8)startActivity(new Intent(this,ModerationNoticesActivity.class));else if(item.getItemId()==7)DemoSharing.open(this,"p",d.getId());else if(item.getItemId()==1)ReportSheet.target("post",d.getId(),null,SocialRepository.number(d.getData(),"contentVersion"),null).show(getSupportFragmentManager(),"report");
            else if(item.getItemId()==3)startActivity(new Intent(this,SocialComposerActivity.class).putExtra("post",d.getId()));
            else {authors.clear();authors.get(d.getString("authorUid")).addOnSuccessListener(profile->{
                if(actions.lost||isDestroyed()||isFinishing())return;
                String content=profile==null?getString(R.string.social_member):profile.get("displayName")+"\n"+profile.get("bio");
                dialog=new MaterialAlertDialogBuilder(this).setTitle(R.string.profile_title).setMessage(content).setPositiveButton(android.R.string.ok,null).show();
            });}return true;
        });menu.show();
    }
    @Override protected void onStart(){super.onStart();if(model!=null)model.start();}
    @Override protected void onStop(){checkpoint();model.stop();super.onStop();}
    @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();if(authors!=null)authors.close();super.onDestroy();}

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        List<DocumentSnapshot> rows=new ArrayList<>();private final LinkedHashMap<String,Long> stableIds=new LinkedHashMap<>();private long nextId=1;Adapter(){setHasStableIds(true);}
        void submit(List<DocumentSnapshot> next){List<DocumentSnapshot> old=rows;androidx.recyclerview.widget.DiffUtil.DiffResult diff=androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback(){public int getOldListSize(){return old.size()+1;}public int getNewListSize(){return next.size()+1;}public boolean areItemsTheSame(int a,int b){return a==old.size()||b==next.size()?a==old.size()&&b==next.size():old.get(a).getId().equals(next.get(b).getId());}public boolean areContentsTheSame(int a,int b){return a!=old.size()&&b!=next.size()&&old.get(a).getData().equals(next.get(b).getData());}});rows=new ArrayList<>(next);diff.dispatchUpdatesTo(this);}

        public long getItemId(int p){if(p==rows.size())return Long.MIN_VALUE;String id=rows.get(p).getId();if(!stableIds.containsKey(id))stableIds.put(id,nextId++);while(stableIds.size()>200){String first=stableIds.keySet().iterator().next();if(rows.stream().anyMatch(r->r.getId().equals(first)))break;stableIds.remove(first);}return stableIds.get(id);}
        public int getItemCount(){return rows.size()+1;}
        @Override public int getItemViewType(int position){return position==rows.size()?1:0;}
        public Holder onCreateViewHolder(ViewGroup parent,int type){if(type==1){LinearLayout col=column(parent.getContext());col.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(col,true);}View card=getLayoutInflater().inflate(R.layout.item_social_feed_card,parent,false);return new Holder((LinearLayout)card,false);}
        public void onBindViewHolder(Holder h,int p){
            Object key=p==rows.size()?null:java.util.Arrays.asList(rows.get(p).getId(),rows.get(p).getData(),mediaGeneration,authors==null?null:authors.identityGeneration(),likes.get(rows.get(p).getId()),counts.get(rows.get(p).getId()),actions.busy,actions.pending,((AniloveApplication)getApplication()).communityOutbox().pendingLike(rows.get(p).getId()));
            if(key!=null&&key.equals(h.cardKey))return;h.cardKey=key;
            h.binding++;if(h.status)h.col.removeAllViews();
            if(p==rows.size()){
                h.col.addView(text(CommunityFeedActivity.this,getString(model.busy?R.string.social_loading:model.failed?R.string.social_retry:model.end?R.string.social_end:R.string.social_loading),false));
                if(model.failed)h.col.addView(button(CommunityFeedActivity.this,getString(R.string.social_retry),()->model.load(false)));return;
            }
            DocumentSnapshot d=rows.get(p);
            ImageView avatar=h.avatar;avatar.setImageResource(R.drawable.profile_avatar_default);avatar.setImageTintList(null);
            TextView author=h.author;author.setText(R.string.social_member);
            h.more.setOnClickListener(v->options(d,v));
            String uid=d.getString("authorUid");long bind=h.binding;
            if(uid!=null&&authors!=null&&!model.lost)authors.get(uid).addOnSuccessListener(profile->{
                if(h.binding!=bind||model.lost||profile==null)return;author.setText((String)profile.get("displayName"));
                avatar.setImageResource("cat".equals(profile.get("avatarKey"))?R.drawable.profile_avatar_cat:"dog".equals(profile.get("avatarKey"))?R.drawable.profile_avatar_dog:R.drawable.profile_avatar_default);if(profile.get("avatarAssetId") instanceof String){avatar.setImageTintList(null);com.gmail.manhle240406.project.media.MediaGrid.load(avatar,(String)profile.get("avatarAssetId"),"thumbnail");}
            });
            String time=d.getTimestamp("firstPublishedAt")==null?"":java.text.DateFormat.getDateTimeInstance().format(d.getTimestamp("firstPublishedAt").toDate());
            h.meta.setText(time+" Â· "+getString("everyone".equals(d.get("audience"))?R.string.community_everyone:R.string.social_audience)+(d.get("editedAt")!=null?" Â· "+getString(R.string.social_edited):""));
            h.body.setText(d.getString("body"));
            h.gallery.bind(d.get("mediaAssetIds"),"post:"+d.getId());
            h.adoption.bind(d.getData());
            String media=d.getString("bundledIllustrationKey");
            h.illustration.setVisibility(media==null?View.GONE:View.VISIBLE);if(media!=null)h.illustration.setImageResource(media.equals("cat")?R.drawable.profile_avatar_cat:media.equals("dog")?R.drawable.profile_avatar_dog:R.drawable.profile_avatar_default);
            com.google.android.material.button.MaterialButton reaction=h.reaction;reaction.setIcon(null);reaction.setContentDescription(null);reaction.setOnClickListener(v->like(d));reaction.setEllipsize(android.text.TextUtils.TruncateAt.END);Map<String,Object> ownLike=likes.get(d.getId());reaction.setText(ownLike!=null&&Boolean.TRUE.equals(ownLike.get("active"))?R.string.social_unlike:R.string.social_like_action);reaction.setEnabled(likes.containsKey(d.getId())&&!actions.busy&&actions.pending==null&&!actions.restored);h.open.setOnClickListener(v->{checkpoint();startActivity(new Intent(CommunityFeedActivity.this,SocialDetailActivity.class).putExtra("post",d.getId()));});TextView counter=h.counter;counter.setText(counts.containsKey(d.getId())?getString(R.string.social_likes,counts.get(d.getId())):getString(R.string.social_count_loading));CommunityOutbox.Entry pendingLike=((AniloveApplication)getApplication()).communityOutbox().pendingLike(d.getId());if(pendingLike!=null){reaction.setText(Boolean.TRUE.equals(pendingLike.command.input.get("active"))?R.string.social_unlike:R.string.social_like_action);reaction.setIconResource(R.drawable.ic_social_pending);reaction.setContentDescription(reaction.getText()+" - "+getString(R.string.community_pending));reaction.setEnabled(!actions.busy&&pendingLike.replaceable());}if(!likes.containsKey(d.getId())||!counts.containsKey(d.getId()))readReaction(d,h,counter,reaction,bind);
        }
        public void onViewRecycled(Holder h){/* RecyclerView owns bounded holder reuse; no Activity-wide view cache. */}
    }
    private static final class Holder extends RecyclerView.ViewHolder {final LinearLayout col;final boolean status;final ImageView avatar,illustration;final TextView author,meta,body,counter;final com.google.android.material.button.MaterialButton more,reaction,open;final com.gmail.manhle240406.project.media.MediaGrid gallery;final AdoptionPostCard adoption;long binding;Object cardKey;Holder(LinearLayout v,boolean status){super(v);col=v;this.status=status;if(status){avatar=illustration=null;author=meta=body=counter=null;more=reaction=open=null;gallery=null;adoption=null;}else{avatar=v.findViewById(R.id.social_post_avatar);illustration=v.findViewById(R.id.social_post_illustration);author=v.findViewById(R.id.social_post_author);meta=v.findViewById(R.id.social_post_meta);body=v.findViewById(R.id.social_post_body);counter=v.findViewById(R.id.social_post_count);more=v.findViewById(R.id.social_post_more);reaction=v.findViewById(R.id.social_post_reaction);open=v.findViewById(R.id.social_post_open);gallery=v.findViewById(R.id.social_post_gallery);adoption=v.findViewById(R.id.social_post_adoption);}}}
}
