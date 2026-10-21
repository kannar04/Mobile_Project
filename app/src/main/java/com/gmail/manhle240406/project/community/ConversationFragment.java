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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.*;
import java.util.*;

/** Scoped retained-history reader. Real sending remains behind the approved rollout adapters. */
public final class ConversationFragment extends MainPageFragment {
    private ParticipantCommunicationRepository repository; private SessionGateway sessions;private AdoptionChatModel flowModel;private boolean flowMode;
    private SessionEpoch.Ticket ticket; private String reservation;
    private final List<DocumentSnapshot> rows=new ArrayList<>();
    private DocumentSnapshot cursor;private boolean busy,more=true;private long generation;
    private ChatSendModel composer;private com.google.android.material.textfield.TextInputEditText message;private MaterialButton send;private TextView status;private RecyclerView list;private MaterialButton older;
    private androidx.appcompat.app.AlertDialog dialog;
    private final Runnable invalidation=()->{generation++;rows.clear();if(list!=null)list.getAdapter().notifyDataSetChanged();};
    public static ConversationFragment create(String id){ConversationFragment f=new ConversationFragment();Bundle b=new Bundle();b.putString("reservation",id);f.setArguments(b);return f;}
    public static ConversationFragment createFlow(String id){ConversationFragment f=new ConversationFragment();Bundle b=new Bundle();b.putString("reservation",id);b.putBoolean("flow",true);f.setArguments(b);return f;}
    @Override protected void createPage(Bundle state){
        reservation=requireArguments().getString("reservation");flowMode=requireArguments().getBoolean("flow",false);
        LinearLayout root=SocialViews.column(requireContext());root.setPadding(0,0,0,0);root.setBackgroundResource(R.color.anilove_bg);setContentView(root);SocialViews.privateView(root);
        SocialSurface.header(requireContext(),root,R.string.chat_participant_conversation,()->((MainActivity)requireActivity()).up());
        LinearLayout body=SocialViews.column(requireContext());root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        status=SocialSurface.muted(requireContext(),getString(R.string.wiring_loading));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        older=SocialSurface.quiet(requireContext(),getString(R.string.chat_older),this::load);body.addView(older);
        list=new RecyclerView(requireContext());list.setLayoutManager(new LinearLayoutManager(requireContext()));list.setAdapter(new Messages());body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        message=SocialViews.field(requireContext(),body,getString(R.string.feedback_chat_message),2);message.setTag("chat_message_input");message.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});message.setSaveEnabled(false);
        send=SocialViews.primaryButton(requireContext(),getString(R.string.feedback_chat_send),()->{if(flowMode&&flowModel!=null)flowModel.send();else if(composer!=null)composer.send(reservation);});send.setTag("chat_send");body.addView(send);

        MaterialButton block=SocialSurface.quiet(requireContext(),getString(R.string.chat_manage_block),this::manageBlock);body.addView(block);block.setVisibility(flowMode?View.GONE:View.VISIBLE);
        FirebaseEnvironment env=((AniloveApplication)getApplication()).foundation();sessions=env.sessions;ticket=sessions==null?null:sessions.capture();
        if(ticket==null||ticket.uid==null){status.setText(R.string.wiring_auth_required);return;}
        sessions.onInvalidated(invalidation);if(flowMode){flowModel=new androidx.lifecycle.ViewModelProvider(this).get(AdoptionChatModel.class);message.setText(flowModel.body);message.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){if(flowModel!=null&&!flowModel.pending())flowModel.body=s.toString();}});flowModel.changes.observe(getViewLifecycleOwner(),x->{if(!current())return;message.setEnabled(!flowModel.sending&&!flowModel.readOnly);send.setEnabled(!flowModel.sending&&!flowModel.readOnly);send.setText(flowModel.pending()?R.string.ad_retry_same:R.string.feedback_chat_send);older.setVisibility(flowModel.next==null?View.GONE:View.VISIBLE);older.setEnabled(!flowModel.busy);status.setText(!flowModel.error.isEmpty()?R.string.chat_load_failed:flowModel.readOnly?R.string.lc_readonly:flowModel.messages.isEmpty()&&flowModel.loaded?R.string.chat_no_messages:R.string.chat_private_participants);list.getAdapter().notifyDataSetChanged();if(!message.getText().toString().equals(flowModel.body))message.setText(flowModel.body);});flowModel.open(reservation);return;}repository=new ParticipantCommunicationRepository(FirebaseFirestore.getInstance(),sessions);composer=new androidx.lifecycle.ViewModelProvider(this).get(ChatSendModel.class);message.setText(composer.body);message.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){if(composer!=null&&!composer.pending())composer.body=s.toString();}});composer.changes.observe(getViewLifecycleOwner(),x->{if(!current())return;message.setEnabled(!composer.busy&&!composer.pending());send.setEnabled(!composer.busy);send.setText(composer.pending()?R.string.ad_retry_same:R.string.feedback_chat_send);if(!composer.error.isEmpty())status.setText(R.string.ad_unknown_outcome);if(composer.committed){composer.committed=false;message.setText("");}});
    }
    @Override public void onResume(){super.onResume();if(flowMode){if(flowModel!=null)flowModel.load(false);return;}if(repository==null||!current())return;repository.listen(ticket,reservation,(snapshot,error)->{if(!current()||list==null)return;if(error!=null){status.setText(R.string.chat_load_failed);return;}if(snapshot==null||snapshot.getMetadata().isFromCache())return;for(DocumentSnapshot row:snapshot.getDocuments()){rows.removeIf(old->old.getId().equals(row.getId()));rows.add(row);}rows.sort(Comparator.comparingLong(d->d.getLong("sequence")==null?0:d.getLong("sequence")));while(rows.size()>100)rows.remove(0);if(cursor==null&&!snapshot.isEmpty()){cursor=snapshot.getDocuments().get(snapshot.size()-1);more=snapshot.size()==20;}older.setVisibility(more?View.VISIBLE:View.GONE);list.getAdapter().notifyDataSetChanged();status.setText(rows.isEmpty()?R.string.chat_no_messages:R.string.chat_private_participants);});}
    @Override public void onPause(){if(repository!=null)repository.stop();super.onPause();}
    private boolean current(){return !isDestroyed()&&!isFinishing()&&sessions!=null&&sessions.current(ticket);}
    private void load(){if(flowMode){if(flowModel!=null)flowModel.load(true);return;}if(busy||!more||repository==null||!current())return;busy=true;older.setEnabled(false);long epoch=++generation;
        repository.messages(ticket,reservation,cursor).addOnCompleteListener(result->{if(!current()||epoch!=generation)return;busy=false;older.setEnabled(true);
            if(!result.isSuccessful()){status.setText(R.string.chat_load_failed);older.setText(R.string.wiring_retry);return;}
            List<DocumentSnapshot> page=result.getResult().getDocuments();if(!page.isEmpty())cursor=page.get(page.size()-1);
            for(DocumentSnapshot row:page)if(rows.stream().noneMatch(old->old.getId().equals(row.getId())))rows.add(row);
            rows.sort(Comparator.comparingLong(d->d.getLong("sequence")==null?0:d.getLong("sequence")));
            while(rows.size()>100)rows.remove(rows.size()-1);more=page.size()==20;older.setVisibility(more?View.VISIBLE:View.GONE);
            list.getAdapter().notifyDataSetChanged();status.setText(rows.isEmpty()?R.string.chat_no_messages:R.string.chat_private_participants);
        });
    }
    private void manageBlock(){if(busy||!current())return;busy=true;long epoch=generation;
        FirebaseFirestore.getInstance().document("conversationBlocks/"+reservation+"/members/"+ticket.uid).get(Source.SERVER).addOnCompleteListener(result->{
            if(!current()||epoch!=generation)return;busy=false;if(!result.isSuccessful()){status.setText(R.string.chat_load_failed);return;}
            DocumentSnapshot own=result.getResult();boolean blocked=Boolean.TRUE.equals(own.getBoolean("blocked"));Long version=own.getLong("version");
            String operation=UUID.randomUUID().toString();
            dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(blocked?R.string.chat_unblock:R.string.chat_block).setMessage(R.string.chat_block_explanation)
                .setNegativeButton(android.R.string.cancel,null).setPositiveButton(blocked?R.string.chat_unblock:R.string.chat_block,(d,w)->{
                    if(!current()||busy)return;busy=true;repository.block(ticket,reservation,operation,version==null?0:version,!blocked).addOnCompleteListener(done->{if(!current())return;busy=false;status.setText(done.isSuccessful()?R.string.chat_block_updated:R.string.chat_load_failed);});
                }).show();
        });
    }
    private final class Messages extends RecyclerView.Adapter<Message>{
        public int getItemCount(){return flowMode&&flowModel!=null?flowModel.messages.size():rows.size();}
        public Message onCreateViewHolder(ViewGroup p,int type){TextView text=SocialViews.text(requireContext(),"",false);int pad=SocialViews.dp(requireContext(),16);text.setPadding(pad,pad,pad,pad);return new Message(text);}
        public void onBindViewHolder(Message holder,int position){String sender,body;if(flowMode&&flowModel!=null){Map<String,Object> row=flowModel.messages.get(position);sender=Objects.toString(row.get("senderUid"),"");body=Objects.toString(row.get("body"),"");}else{DocumentSnapshot row=rows.get(position);sender=row.getString("senderUid");body=Objects.toString(row.getString("body"),"");}((TextView)holder.itemView).setText(getString(ticket.uid.equals(sender)?R.string.chat_me:R.string.chat_participant)+"\n"+body);}
    }
    private static final class Message extends RecyclerView.ViewHolder{Message(View v){super(v);}}
    @Override public void onDestroyView(){generation++;if(dialog!=null)dialog.dismiss();if(repository!=null)repository.close();if(sessions!=null)sessions.removeInvalidation(invalidation);list=null;super.onDestroyView();}
}
