package com.gmail.manhle240406.project.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.wiring.MainPageFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Independent private cursor. No copied post payloads or profile directory. */
public final class LikedPostsFragment extends MainPageFragment {
 private static final class Row{final String id;Map<String,Object> source,like;boolean resolved;Row(String id){this.id=id;}}
 private final List<Row> rows=new ArrayList<>();private final ArrayDeque<Runnable> queue=new ArrayDeque<>();private int resolving;private long generation;private boolean busy,end,failed,refreshing;
 private DocumentSnapshot cursor;private DailyViewModel actions;private LikedPostsRepository repository;private AuthorLookup authors;private Adapter adapter;private RecyclerView list;private androidx.swiperefreshlayout.widget.SwipeRefreshLayout refresh;
 private final PendingReactionTracker tracker=new PendingReactionTracker();private Runnable invalidation;

 @Override
 protected void createPage(Bundle state) {
  refresh = new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(requireContext());
  refresh.setBackgroundResource(R.color.anilove_bg);
  setContentView(refresh);
  list = new RecyclerView(requireContext());
  list.setId(R.id.liked_posts_list);
  int inset = SocialViews.dp(requireContext(), 8);
  list.setPadding(inset, inset, inset, inset);
  list.setClipToPadding(false);
  list.addItemDecoration(new RecyclerView.ItemDecoration() {
   @Override public void getItemOffsets(@androidx.annotation.NonNull android.graphics.Rect outRect, @androidx.annotation.NonNull android.view.View view, @androidx.annotation.NonNull RecyclerView parent, @androidx.annotation.NonNull RecyclerView.State state) {
    if (parent.getChildAdapterPosition(view) != RecyclerView.NO_POSITION) outRect.bottom = inset;
   }
  });
  list.setLayoutManager(new LinearLayoutManager(requireContext()));
  list.setItemAnimator(null);
  refresh.addView(list);
  adapter = new Adapter();
  list.setAdapter(adapter);
  actions = new ViewModelProvider(this).get(DailyViewModel.class);
  if (!actions.current()) return;
  repository = new LikedPostsRepository(FirebaseFirestore.getInstance(), actions.sessions, actions.ticket);
  authors = new AuthorLookup(actions.sessions);
  invalidation = () -> {
   generation++;
   rows.clear();
   queue.clear();
   if (adapter != null) adapter.notifyDataSetChanged();
  };
  actions.sessions.onInvalidated(invalidation);
  refresh.setOnRefreshListener(this::reload);list.addOnScrollListener(new RecyclerView.OnScrollListener(){public void onScrolled(RecyclerView r,int dx,int dy){if(dy>0&&!failed&&((LinearLayoutManager)r.getLayoutManager()).findLastVisibleItemPosition()>=rows.size()-4)page();}});
  actions.changes.observe(getViewLifecycleOwner(),n->{if(actions.notice.equals("queued"))rows.removeIf(r->{CommunityOutbox.Entry e=((AniloveApplication)getApplication()).communityOutbox().pendingLike(r.id);return e!=null&&!Boolean.TRUE.equals(e.command.input.get("active"));});adapter.notifyDataSetChanged();});
  ((AniloveApplication)getApplication()).communityOutbox().entries().observe(getViewLifecycleOwner(),pending->{if(!tracker.completedPosts(pending).isEmpty()&&!busy)reload();});if(rows.isEmpty())page();
 }

 private boolean current(long g){return g==generation&&!isDestroyed()&&actions!=null&&actions.current();}
 private void reload(){if(busy)return;generation++;queue.clear();resolving=0;refreshing=true;cursor=null;end=false;failed=false;authors.clear();refresh.setRefreshing(true);page();}
 private void page(){if(busy||end||repository==null||!actions.current())return;busy=true;failed=false;long g=generation;adapter.notifyDataSetChanged();repository.page(cursor).addOnCompleteListener(done->{if(!current(g))return;busy=false;refresh.setRefreshing(false);if(!done.isSuccessful()){failed=true;refreshing=false;adapter.notifyDataSetChanged();return;}if(refreshing){rows.clear();refreshing=false;}List<DocumentSnapshot> refs=done.getResult().getDocuments();end=refs.size()<20;if(!refs.isEmpty())cursor=refs.get(refs.size()-1);for(DocumentSnapshot ref:refs){if(rows.stream().anyMatch(r->r.id.equals(ref.getId())))continue;Row row=new Row(ref.getId());rows.add(row);queue.add(()->repository.resolve(row.id).addOnCompleteListener(r->{if(!current(g))return;resolving--;row.resolved=true;if(r.isSuccessful()){row.source=(Map<String,Object>)r.getResult().get("source");row.like=(Map<String,Object>)r.getResult().get("like");}adapter.notifyDataSetChanged();drain(g);}));}while(rows.size()>100)rows.remove(0);adapter.notifyDataSetChanged();drain(g);});}
 private void drain(long g){while(current(g)&&resolving<3&&!queue.isEmpty()){resolving++;queue.remove().run();}}
 private void open(Row row){startActivity(new Intent(requireActivity(),SocialDetailActivity.class).putExtra("post",row.id));}
 @Override public void onResume(){super.onResume();if(repository!=null&&!busy)reload();}
 @Override public void onDestroyView(){generation++;queue.clear();resolving=0;busy=false;if(authors!=null)authors.close();if(actions!=null&&invalidation!=null)actions.sessions.removeInvalidation(invalidation);adapter=null;refresh=null;list=null;super.onDestroyView();}
 private final class Adapter extends RecyclerView.Adapter<Holder>{
  public int getItemCount(){return rows.size()+1;}

  public int getItemViewType(int position) {
   if (position == rows.size()) return 2;
   return rows.get(position).source == null ? 1 : 0;
  }

  public Holder onCreateViewHolder(ViewGroup p, int type) {
   if (type == 0) {
    SocialPostCard card = new SocialPostCard(p.getContext());
    card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
    return new Holder(card);
   }
   LinearLayout content = SocialViews.column(p.getContext());
   content.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
   return new Holder(content);
  }
  public void onBindViewHolder(Holder h,int position){
   Object key=position==rows.size()?null:Arrays.asList(rows.get(position).id,rows.get(position).source,rows.get(position).like,rows.get(position).resolved,SessionAliases.generation(),actions.busy,actions.notice);
   if (key != null && key.equals(h.cardKey)) return;
   h.cardKey = key;
   if (position == rows.size()) {
    h.content.removeAllViews();
    h.content.addView(SocialViews.text(requireContext(), getString(busy ? R.string.social_loading : failed ? R.string.wiring_failed : rows.isEmpty() ? R.string.feed_no_liked_posts : end ? R.string.social_end : R.string.social_loading), false));
    if (failed)
     h.content.addView(SocialViews.button(requireContext(), getString(R.string.social_retry), () -> page()));
    return;
   }
   Row row = rows.get(position);
   h.itemView.setTag(row.id);
   if(row.source!=null){
    SocialPostCard card = (SocialPostCard) h.itemView;
    card.bind(row.source, row.id, authors, null, actions, false, () -> open(row));
    card.setLikeState(row.like != null && Boolean.TRUE.equals(row.like.get("active")));
    card.setEnabled(!actions.busy);
   } else {
    h.content.removeAllViews();
    h.content.addView(SocialViews.text(requireContext(), getString(row.resolved ? R.string.social_unavailable : R.string.social_loading), false));
   }
  }
 }

 private static final class Holder extends RecyclerView.ViewHolder {
  final LinearLayout content;
  Object cardKey;

  Holder(View view) {
   super(view);
   content = view instanceof SocialPostCard ? null : (LinearLayout) view;
  }
 }
}
