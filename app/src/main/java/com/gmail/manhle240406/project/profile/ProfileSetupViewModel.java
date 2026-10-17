package com.gmail.manhle240406.project.profile;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.*;

/** Private name setup; UID-scoped in memory only, no public consent or provider-profile import. */
public final class ProfileSetupViewModel extends AndroidViewModel {
    public final MutableLiveData<Long> changes=new MutableLiveData<>(0L);
    public String name="";
    public boolean busy,checked,ready,lost,conflict;
    public String existingName="";
    public ProfileRepository.Code code=ProfileRepository.Code.OK;
    private Map<String,Object> draft;
    private ProfileCommand pending;
    private final ProfileRepository repository;
    private final SessionGateway sessions;
    private final SessionEpoch.Ticket ticket;
    private long event,generation;
    private final Runnable invalidation=()->{generation++;name="";existingName="";conflict=false;draft=null;pending=null;busy=false;ready=false;checked=false;lost=true;changed();};
    public ProfileSetupViewModel(@NonNull Application app){
        super(app);FirebaseEnvironment env=((AniloveApplication)app).foundation();sessions=env.sessions;
        ticket=sessions==null?null:sessions.capture();repository=sessions==null?null:new ProfileRepository(FirebaseFirestore.getInstance(),sessions);
        if(sessions!=null)sessions.onInvalidated(invalidation);
    }
    private boolean current(long epoch){return epoch==generation&&!lost&&ticket!=null&&ticket.uid!=null&&sessions.current(ticket);}
    private void changed(){changes.setValue(++event);}
    public void load(){
        if(busy||repository==null||ticket==null||ticket.uid==null||lost)return;
        boolean comparing=conflict;busy=true;code=ProfileRepository.Code.OK;long epoch=generation;changed();
        repository.ownDraft(ticket).addOnCompleteListener(result->{if(!current(epoch))return;busy=false;
            if(!result.isSuccessful()){code=ProfileRepository.Code.UNAVAILABLE;changed();return;}
            ProfileRepository.Result<Map<String,Object>> value=result.getResult();code=value.code;
            if(code==ProfileRepository.Code.OK){draft=value.value;checked=true;boolean valid=draft!=null&&ProfileSchema.validContent(draft);existingName=valid?(String)draft.get("displayName"):"";ready=valid&&!comparing;if(ready)name=existingName;if(comparing)code=ProfileRepository.Code.CONFLICT;}
            changed();
        });
    }
    public boolean validName(){String trimmed=name.trim();return ProfileSchema.length(trimmed)>=2&&ProfileSchema.length(trimmed)<=40;}
    public void save(){
        if(busy||!checked||!validName()||repository==null||lost)return;
        if(pending==null){conflict=false;Map<String,Object> content=new HashMap<>(draft==null?ProfileSchema.content(name,"default",null,""):ProfileSchema.publicContent(draft));content.put("displayName",name.trim());content.put("submission","draft");pending=new ProfileCommand(ProfileCommand.Kind.DRAFT,ProfileSchema.version(draft),UUID.randomUUID().toString(),content);}
        busy=true;long epoch=generation;changed();
        repository.execute(ticket,pending).addOnCompleteListener(result->{if(!current(epoch))return;busy=false;
            if(result.isSuccessful()){code=result.getResult().code;if(code==ProfileRepository.Code.OK){draft=result.getResult().value;ready=true;pending=null;}}
            else code=ProfileRepository.Code.UNAVAILABLE;
            // Conflict is explicit: reload before creating a new command; never silently overwrite.
            if(code==ProfileRepository.Code.CONFLICT){conflict=true;checked=false;pending=null;}
            changed();
        });
    }
    public boolean pending(){return pending!=null;}
    public void useExisting(){if(conflict&&checked&&!busy&&!existingName.isEmpty()){name=existingName;ready=true;conflict=false;changed();}}
    @Override protected void onCleared(){if(sessions!=null)sessions.removeInvalidation(invalidation);generation++;name="";existingName="";draft=null;pending=null;}
}
