package com.gmail.manhle240406.project.community;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.ParticipantCommunicationRepository;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.MainPageFragment;
import com.google.firebase.firestore.*;
import com.google.android.material.button.MaterialButton;
import java.util.*;

/** Own bounded participant index. Never invents strangers, memberships or message previews. */
public final class ConversationsFragment extends MainPageFragment {
    private DirectInboxAdapter direct;private ParticipantCommunicationRepository repository;
    private SessionGateway sessions; private SessionEpoch.Ticket ticket;
    private final List<DocumentSnapshot> rows=new ArrayList<>();
    private DocumentSnapshot cursor; private boolean busy,more=true; private long generation;
    private TextView status; private MaterialButton retry; private RecyclerView list;
    private final Runnable invalidation=()->{generation++;rows.clear();if(list!=null)list.getAdapter().notifyDataSetChanged();};
    @Override protected void createPage(Bundle saved){
        LinearLayout root=SocialViews.column(requireContext());root.setPadding(0,0,0,0);setContentView(root);
        root.setBackgroundResource(R.color.anilove_bg);SocialViews.privateView(root);
        com.google.android.material.appbar.MaterialToolbar header=SocialSurface.header(requireContext(),root,R.string.nav_chat,this::finish);
        header.setNavigationIcon(null);
        LinearLayout body=SocialViews.column(requireContext());root.addView(body,new LinearLayout.LayoutParams(-1,0,1));com.google.android.material.button.MaterialButton newMessage=SocialSurface.quiet(requireContext(),getString(R.string.oc3_new_message),()->DirectChats.people((androidx.appcompat.app.AppCompatActivity)requireActivity()));newMessage.setId(R.id.oc3_chat_new);newMessage.setIconResource(R.drawable.ic_nav_chat);body.addView(newMessage);body.addView(SocialSurface.muted(requireContext(),getString(R.string.oc3_chat_help)));
        status=SocialSurface.muted(requireContext(),getString(R.string.wiring_loading));body.addView(status);
        retry=SocialSurface.quiet(requireContext(),getString(R.string.wiring_retry),this::load);retry.setVisibility(View.GONE);body.addView(retry);
        list=new RecyclerView(requireContext());list.setId(R.id.conversations_list);list.setLayoutManager(new LinearLayoutManager(requireContext()));list.setAdapter(new Rows());body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        FirebaseEnvironment environment=((AniloveApplication)getApplication()).foundation();sessions=environment.sessions;ticket=sessions==null?null:sessions.capture();
        if(ticket==null||ticket.uid==null){status.setText(R.string.wiring_auth_required);return;}
        direct=new DirectInboxAdapter((androidx.appcompat.app.AppCompatActivity)requireActivity(),sessions);list.setAdapter(new androidx.recyclerview.widget.ConcatAdapter(direct,new Rows()));sessions.onInvalidated(invalidation);repository=new ParticipantCommunicationRepository(FirebaseFirestore.getInstance(),sessions);
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView v,int x,int y){if(y>0&&((LinearLayoutManager)list.getLayoutManager()).findLastVisibleItemPosition()>=rows.size()-5)load();}});
    }
    @Override public void onResume(){super.onResume();if(direct!=null)direct.start();if(repository==null||!current())return;repository.listenInbox(ticket,(snapshot,error)->{if(!current()||list==null)return;if(error!=null){status.setText(R.string.chat_load_failed);retry.setVisibility(View.VISIBLE);return;}if(snapshot==null||snapshot.getMetadata().isFromCache())return;for(DocumentSnapshot row:snapshot.getDocuments()){rows.removeIf(old->old.getId().equals(row.getId()));rows.add(row);}rows.sort((a,b)->{com.google.firebase.Timestamp x=a.getTimestamp("updatedAt"),y=b.getTimestamp("updatedAt");return x==null?1:y==null?-1:y.compareTo(x);});while(rows.size()>100)rows.remove(rows.size()-1);if(cursor==null&&!snapshot.isEmpty()){cursor=snapshot.getDocuments().get(snapshot.size()-1);more=snapshot.size()==20;}list.getAdapter().notifyDataSetChanged();status.setText(R.string.oc3_adoption_messages);});}
    @Override public void onPause(){if(direct!=null)direct.stop();if(repository!=null)repository.stop();super.onPause();}
    private boolean current(){return !isDestroyed()&&!isFinishing()&&sessions!=null&&sessions.current(ticket);}
    private void load(){if(busy||!more||repository==null||!current())return;busy=true;long epoch=++generation;retry.setVisibility(View.GONE);
        repository.inbox(ticket,cursor).addOnCompleteListener(result->{if(!current()||epoch!=generation)return;busy=false;
            if(!result.isSuccessful()){status.setText(R.string.chat_load_failed);retry.setVisibility(View.VISIBLE);return;}
            for(DocumentSnapshot row:result.getResult())if(rows.stream().noneMatch(old->old.getId().equals(row.getId())))rows.add(row);
            if(!result.getResult().isEmpty())cursor=result.getResult().getDocuments().get(result.getResult().size()-1);
            more=result.getResult().size()==20;while(rows.size()>100)rows.remove(0);list.getAdapter().notifyDataSetChanged();
            status.setText(R.string.oc3_adoption_messages);
        });
    }
    private final class Rows extends RecyclerView.Adapter<Holder>{
        @Override public int getItemCount(){return rows.size();}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){MaterialButton button=SocialSurface.quiet(requireContext(),"",()->{});button.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(button);}
        @Override public void onBindViewHolder(Holder holder,int position){DocumentSnapshot row=rows.get(position);MaterialButton button=(MaterialButton)holder.itemView;
            String activity=row.getTimestamp("updatedAt")==null?"":java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT).format(row.getTimestamp("updatedAt").toDate());
            button.setText(getString(R.string.chat_participant_conversation)+(activity.isEmpty()?"":"\n"+activity));button.setIconResource(R.drawable.ic_nav_chat);
            button.setOnClickListener(v->((MainActivity)requireActivity()).conversation(row.getId()));}
    }
    private static final class Holder extends RecyclerView.ViewHolder{Holder(View view){super(view);}}
    @Override public void onDestroyView(){generation++;if(direct!=null)direct.close();if(repository!=null)repository.close();if(sessions!=null)sessions.removeInvalidation(invalidation);list=null;super.onDestroyView();}
}
