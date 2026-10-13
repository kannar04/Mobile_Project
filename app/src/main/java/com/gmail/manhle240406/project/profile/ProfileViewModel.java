package com.gmail.manhle240406.project.profile;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.auth.AuthState;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.*;

/** Private form state retained only by this screen's ViewModel during rotation. */
public final class ProfileViewModel extends AndroidViewModel {
    public final MutableLiveData<Long> changes=new MutableLiveData<>(0L);
    public String name="",bio="",phone="";
    public int avatar=0,region=0;
    public ProfileRepository.Snapshot snapshot,conflict;
    public ProfileCommand retry;
    public ProfileCommand conflictedCommand;
    public ProfileRepository.Reviews reviews;
    public ProfileRepository.Code code=ProfileRepository.Code.OK;
    public boolean editingProfile,busy,loaded,sessionLost,submitted,committed,conflictReady;
    private Map<String,Object> consentedCandidate;
    private String consentedSave;
    public long formRevision,conflictRevision;
    public boolean coldPending;
    private final androidx.lifecycle.SavedStateHandle saved;
    private final SessionGateway sessions;
    private final ProfileRepository repository;
    private final SessionEpoch.Ticket ticket;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable invalidation=this::invalidate;
    private long generation,event;
    public ProfileViewModel(@NonNull Application app,androidx.lifecycle.SavedStateHandle restoredState) {
        super(app);saved=restoredState;
        FirebaseEnvironment env=((AniloveApplication)app).foundation();
        sessions=env.sessions;
        repository=sessions==null?null:new ProfileRepository(FirebaseFirestore.getInstance(),sessions);
        ticket=sessions==null?null:sessions.capture();
        if(sessions!=null) sessions.onInvalidated(invalidation);
        AuthState state=((AniloveApplication)app).auth().state().getValue();
        if(repository==null || ticket.uid==null || state==null || !ticket.uid.equals(state.uid) || !state.setupReady || state.pending) invalidate();
        else {String owner=saved.get("profile_owner");if(owner!=null&&!owner.equals(ticket.uid)){saved.remove("profile_op");saved.remove("profile_owner");}coldPending=saved.contains("profile_op");load(true);if(coldPending)reconcilePublication();}
    }
    private void changed() {changes.setValue(++event);}
    private boolean current(long g) {return g==generation && !sessionLost && sessions!=null && sessions.current(ticket);}
    private void invalidate() {
        generation++;consentedCandidate=null;consentedSave=null;saved.remove("profile_op");saved.remove("profile_owner");coldPending=false;publicationOp=null;publicationCandidate=null;name="";bio="";phone="";avatar=0;region=0;snapshot=null;conflict=null;retry=null;conflictedCommand=null;reviews=null;
        busy=false;loaded=false;sessionLost=true;code=ProfileRepository.Code.SESSION_CHANGED;changed();
    }
    public Map<String,Object> content() {return ProfileSchema.content(name,ProfileSchema.AVATARS.get(avatar),region==0?null:ProfileSchema.REGIONS.get(region-1),bio);}
    public boolean dirty() {
        Map<String,Object> d=snapshot==null?null:snapshot.draft,c=snapshot==null?null:snapshot.contact;
        boolean draft=d==null?!name.isEmpty()||!bio.isEmpty()||avatar!=0||region!=0:!ProfileSchema.samePayload(d,content());
        return draft || !Objects.equals(ProfileSchema.phone(phone),c==null?null:c.get("phoneE164"));
    }
    public boolean draftDirty() {return snapshot==null || snapshot.draft==null || !ProfileSchema.samePayload(snapshot.draft,content());}
    private void fill() {
        Map<String,Object> d=snapshot.draft,c=snapshot.contact;
        name=d==null?"":(String)d.get("displayName");bio=d==null?"":(String)d.get("bio");
        avatar=d==null?0:ProfileSchema.AVATARS.indexOf(d.get("avatarKey"));
        region=d==null || d.get("regionCode")==null?0:ProfileSchema.REGIONS.indexOf(d.get("regionCode"))+1;
        phone=c==null || c.get("phoneE164")==null?"":(String)c.get("phoneE164");formRevision++;
    }
    public void load(boolean discard) {
        if(busy || sessionLost || retry!=null) return;
        busy=true;long g=generation;code=ProfileRepository.Code.OK;changed();
        repository.load(ticket).addOnSuccessListener(r->{
            if(!current(g)) return;
            busy=false;code=r.code;
            if(r.code==ProfileRepository.Code.OK){snapshot=r.value;loaded=true;submitted=false;committed=false;reviews=null;code=snapshot.issue;if(discard && snapshot.issue==ProfileRepository.Code.OK)fill();if(snapshot.draft==null)editingProfile=true;
                if(consentedCandidate!=null){Map<String,Object> candidate=consentedCandidate;consentedCandidate=null;consentedSave=null;if(snapshot.draft!=null&&ProfileSchema.publicContent(snapshot.draft).equals(candidate)&&snapshot.moderation!=null){submit();return;}code=ProfileRepository.Code.CONFLICT;}
            }
            changed();
        });
    }
    public void resolveConflict(boolean keep) {
        if(!conflictReady || busy) return;
        snapshot=conflict;conflict=null;conflictReady=false;retry=null;conflictedCommand=null;code=snapshot.issue;
        if(!keep)fill();changed();
    }
    public void saveDraft() {
        Map<String,Object> m=new HashMap<>(content());m.put("submission","draft");
        String op=UUID.randomUUID().toString();if(consentedCandidate!=null)consentedSave=op;send(new ProfileCommand(ProfileCommand.Kind.DRAFT,ProfileSchema.version(snapshot.draft),op,m));
    }
    public void saveContact() {
        Map<String,Object> m=new HashMap<>();m.put("phoneE164",ProfileSchema.phone(phone));
        send(new ProfileCommand(ProfileCommand.Kind.CONTACT,ProfileSchema.version(snapshot.contact),UUID.randomUUID().toString(),m));
    }
    public void publishEdited(){if(busy||coldPending||sessionLost||retry!=null)return;if(draftDirty()){consentedCandidate=new HashMap<>(content());saveDraft();}else submit();}
    private String publicationOp;
    private Map<String,Object> publicationCandidate;
    private long publicationDraft,publicationControl,publicationVersion,publicationModeration;
    public void submit() {
        if(busy || sessionLost || coldPending || draftDirty())return;
        if(publicationOp==null){publicationOp=UUID.randomUUID().toString();saved.set("profile_op",publicationOp);saved.set("profile_owner",ticket.uid);publicationCandidate=content();publicationDraft=ProfileSchema.version(snapshot.draft);publicationControl=ProfileSchema.version(snapshot.control);publicationVersion=snapshot.published==null?0:(Long)snapshot.published.get("publicationVersion");publicationModeration=snapshot.moderation==null?0:(Long)snapshot.moderation.get("moderationVersion");}
        busy=true;long g=generation;changed();String submitting=publicationOp;Runnable timeout=()->{if(current(g)&&busy&&Objects.equals(submitting,publicationOp)){code=ProfileRepository.Code.UNKNOWN;changed();}};main.postDelayed(timeout,15000);
        repository.publish(ticket,publicationOp,publicationDraft,publicationControl,publicationVersion,publicationModeration,publicationCandidate).addOnSuccessListener(r->{
            main.removeCallbacks(timeout);if(!current(g))return;busy=false;code=r.code;
            if(r.code==ProfileRepository.Code.OK){saved.remove("profile_op");publicationOp=null;publicationCandidate=null;load(false);}
            else if(r.code!=ProfileRepository.Code.UNKNOWN){saved.remove("profile_op");publicationOp=null;publicationCandidate=null;if(r.code==ProfileRepository.Code.CONFLICT){busy=true;repository.load(ticket).addOnSuccessListener(fresh->{if(!current(g))return;busy=false;if(fresh.code==ProfileRepository.Code.OK){conflict=fresh.value;conflictReady=true;conflictRevision++;}changed();});}}
            changed();
        });
    }
    public void visibility(boolean visible) {
        if(visible){submit();return;}
        send(new ProfileCommand(ProfileCommand.Kind.VISIBILITY,ProfileSchema.version(snapshot.control),UUID.randomUUID().toString(),Collections.singletonMap("visibility","hidden")));
    }
    public boolean publicationPending(){return publicationOp!=null;}
    private void reconcilePublication(){String op=saved.get("profile_op");if(op==null)return;long g=generation;repository.operation(ticket,op).addOnSuccessListener(r->{if(!current(g))return;code=r.code;if(r.code==ProfileRepository.Code.OK){coldPending=false;saved.remove("profile_op");committed=r.value!=null;load(false);}changed();});}
    public void retry() {if(coldPending){reconcilePublication();return;}if(publicationOp!=null)submit();else if(retry!=null)send(retry);}
    private void send(ProfileCommand command) {
        if(busy || sessionLost || coldPending || publicationOp!=null || !loaded || conflictReady || retry!=null && retry!=command) return;
        busy=true;retry=command;code=ProfileRepository.Code.OK;submitted=false;committed=false;
        long g=generation;changed();
        Runnable timeout=()->{if(current(g) && busy && retry==command){code=ProfileRepository.Code.UNKNOWN;changed();}};
        main.postDelayed(timeout,15000);
        repository.execute(ticket,command).addOnSuccessListener(r->{
            main.removeCallbacks(timeout);
            if(!current(g)) return;
            busy=false;code=r.code;
            if(r.code==ProfileRepository.Code.OK) {
                retry=null;
                Map<String,Object> d=snapshot.draft,c=snapshot.contact,v=snapshot.control;
                if(command.kind==ProfileCommand.Kind.CONTACT)c=r.value;
                else if(command.kind==ProfileCommand.Kind.VISIBILITY)v=r.value;
                else d=r.value;
                Map<String,Object> moderation=snapshot.moderation;snapshot=new ProfileRepository.Snapshot(d,c,v,snapshot.published,snapshot.issue);snapshot.moderation=moderation;
                if(command.operationId.equals(consentedSave)||command.kind==ProfileCommand.Kind.DRAFT&&moderation==null){load(false);return;}
                submitted=command.kind==ProfileCommand.Kind.SUBMIT;
                committed=true;
            } else if(r.code==ProfileRepository.Code.CONFLICT) {
                // Retain original/base and current inputs while obtaining the conflicting version.
                consentedSave=null;consentedCandidate=null;retry=null;conflictedCommand=command;busy=true;
                repository.load(ticket).addOnSuccessListener(fresh->{
                    if(!current(g))return;busy=false;
                    if(fresh.code==ProfileRepository.Code.OK){conflict=fresh.value;conflictReady=true;conflictRevision++;code=ProfileRepository.Code.CONFLICT;}
                    else code=fresh.code;
                    changed();
                });
            } else if(r.code!=ProfileRepository.Code.UNKNOWN){retry=null;consentedSave=null;consentedCandidate=null;}
            changed();
        });
    }
    public void history(boolean next) {
        if(busy || sessionLost || !loaded || retry!=null || conflictReady) return;
        busy=true;long g=generation;changed();
        repository.operations(ticket,next && reviews!=null?reviews.cursor:null).addOnSuccessListener(r->{
            if(!current(g))return;busy=false;code=r.code;if(r.code==ProfileRepository.Code.OK)reviews=r.value;changed();
        });
    }
    @Override protected void onCleared() {generation++;main.removeCallbacksAndMessages(null);if(sessions!=null)sessions.removeInvalidation(invalidation);name="";bio="";phone="";snapshot=null;conflict=null;retry=null;conflictedCommand=null;reviews=null;}
}
