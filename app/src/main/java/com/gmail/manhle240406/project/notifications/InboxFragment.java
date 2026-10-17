package com.gmail.manhle240406.project.notifications;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.UnavailableScreen;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;
import java.util.*;

/** Own real inbox only; no seeded notification, push banner or fake read receipt. */
public final class InboxFragment extends com.gmail.manhle240406.project.wiring.MainPageFragment {
 private InboxRepository repository;private SessionGateway sessions;private SessionEpoch.Ticket ticket;
 private final List<DocumentSnapshot> rows=new ArrayList<>();private RecyclerView list;private Adapter adapter;private ViewSwitcher switcher;
 private TextView loadStatus;private com.google.android.material.button.MaterialButton retry;
 private boolean busy,more=true;private DocumentSnapshot cursor;private long generation;
 private final Runnable invalidation=()->{generation++;rows.clear();if(adapter!=null)adapter.notifyDataSetChanged();if(switcher!=null)switcher.setDisplayedChild(1);};
 @Override protected void createPage(Bundle state){setContentView(R.layout.activity_inbox);SocialViews.privateView(findViewById(R.id.inbox_root));
  LinearLayout root=findViewById(R.id.inbox_root);loadStatus=SocialSurface.muted(requireContext(),getString(R.string.wiring_loading));loadStatus.setPadding(SocialViews.dp(requireContext(),20),SocialViews.dp(requireContext(),12),SocialViews.dp(requireContext(),20),0);root.addView(loadStatus,1);
  retry=SocialSurface.quiet(requireContext(),getString(R.string.wiring_retry),this::load);retry.setVisibility(View.GONE);root.addView(retry,2);
  findViewById(R.id.inbox_back).setOnClickListener(v->finish());findViewById(R.id.inbox_push_banner).setVisibility(View.GONE);findViewById(R.id.inbox_toggle_empty).setVisibility(View.GONE);
  findViewById(R.id.inbox_settings_btn).setOnClickListener(v->startActivity(new Intent(requireActivity(),NotificationSettingsActivity.class)));
  FirebaseEnvironment env=((AniloveApplication)getApplication()).foundation();sessions=env.sessions;ticket=sessions==null?null:sessions.capture();if(ticket==null||ticket.uid==null){finish();return;}
  repository=new InboxRepository(FirebaseFirestore.getInstance(),sessions);sessions.onInvalidated(invalidation);switcher=findViewById(R.id.inbox_switcher);list=findViewById(R.id.inbox_recycler);list.setLayoutManager(new LinearLayoutManager(requireActivity()));adapter=new Adapter();list.setAdapter(adapter);switcher.setVisibility(View.GONE);load();
  list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView v,int x,int y){if(y>0&&((LinearLayoutManager)list.getLayoutManager()).findLastVisibleItemPosition()>=rows.size()-5)load();}});
 }
 private boolean current(){return !isDestroyed()&&!isFinishing()&&sessions!=null&&sessions.current(ticket);}
 private void load(){if(busy||!more||!current())return;busy=true;retry.setVisibility(View.GONE);loadStatus.setVisibility(rows.isEmpty()?View.VISIBLE:View.GONE);loadStatus.setText(R.string.wiring_loading);long g=++generation;repository.page(ticket,cursor,Timestamp.now()).addOnCompleteListener(done->{if(!current()||g!=generation)return;busy=false;if(!done.isSuccessful()){loadStatus.setText(R.string.wiring_failed);loadStatus.setVisibility(View.VISIBLE);retry.setVisibility(View.VISIBLE);return;}for(DocumentSnapshot d:done.getResult().visible)if(InboxContract.route(d.getString("kind"),d.getString("routeType"),d.getString("targetId"),d.getString("templateKey"))&&rows.stream().noneMatch(x->x.getId().equals(d.getId())))rows.add(d);while(rows.size()>100)rows.remove(0);cursor=done.getResult().rawCursor;more=done.getResult().moreCandidates;adapter.notifyDataSetChanged();loadStatus.setVisibility(View.GONE);switcher.setVisibility(View.VISIBLE);switcher.setDisplayedChild(rows.isEmpty()?1:0);});}
 private void open(DocumentSnapshot row){if(busy||!current())return;Long version=row.getLong("itemVersion");if(version==null)return;busy=true;repository.targetAvailable(ticket,row.getId(),Timestamp.now()).addOnCompleteListener(result->{if(!current())return;busy=false;if(!result.isSuccessful()||!Boolean.TRUE.equals(result.getResult())){Snackbar.make(list,R.string.wiring_unavailable_item,Snackbar.LENGTH_LONG).show();return;}repository.markRead(ticket,Collections.singletonMap(row.getId(),version));String route=row.getString("routeType");if("post".equals(route))startActivity(new Intent(requireActivity(),SocialDetailActivity.class).putExtra("post",row.getString("targetId")));else if("own_report".equals(route))startActivity(new Intent(requireActivity(),MyReportsActivity.class));});}
 @Override public void onDestroyView(){generation++;if(repository!=null)repository.close();if(sessions!=null)sessions.removeInvalidation(invalidation);super.onDestroyView();}
 private final class Adapter extends RecyclerView.Adapter<Holder>{public int getItemCount(){return rows.size();}public Holder onCreateViewHolder(ViewGroup p,int type){return new Holder(getLayoutInflater().inflate(R.layout.item_inbox,p,false));}public void onBindViewHolder(Holder h,int p){DocumentSnapshot d=rows.get(p);((TextView)h.itemView.findViewById(R.id.inbox_title)).setText("comment".equals(d.getString("kind"))?R.string.wiring_inbox_comment:R.string.wiring_inbox_activity);((TextView)h.itemView.findViewById(R.id.inbox_message)).setText("own_report".equals(d.getString("routeType"))?R.string.report_received:R.string.wiring_inbox_open);((TextView)h.itemView.findViewById(R.id.inbox_time)).setText(d.getTimestamp("createdAt")==null?"":java.text.DateFormat.getDateTimeInstance().format(d.getTimestamp("createdAt").toDate()));h.itemView.findViewById(R.id.inbox_unread_dot).setVisibility(d.get("readAt")==null?View.VISIBLE:View.INVISIBLE);h.itemView.setOnClickListener(v->open(d));}}
 private static final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
}
