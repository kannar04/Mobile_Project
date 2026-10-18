package com.gmail.manhle240406.project.media;
import android.app.Application;
import android.net.Uri;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.Files;

/** Ordered photos survive rotation in memory only; no automatic reconnect/reboot upload. */
public final class MediaDraftViewModel extends AndroidViewModel {
 public static final class Entry {public final String assetId,operationId;public final Uri uri;public boolean importedPet;public CandidatePreparer.Candidate candidate;public String state="local",error="";Entry(Uri uri){this.uri=uri;assetId=UUID.randomUUID().toString();operationId=UUID.randomUUID().toString();}Entry(String id){assetId=id;operationId=null;uri=null;state="ready";}}
 public final MutableLiveData<Long> changes=new MutableLiveData<>(0L);public final List<Entry> entries=new ArrayList<>();
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private final SessionGateway sessions;private final SessionEpoch.Ticket ticket;private final CandidatePreparer preparer;private long event;public boolean lost;
 private final Runnable invalidation=()->{lost=true;entries.clear();changed();};
 public MediaDraftViewModel(Application app){super(app);sessions=((AniloveApplication)app).foundation().sessions;ticket=sessions==null?null:sessions.capture();CandidatePreparer prepared=null;try{if(ticket!=null)prepared=new CandidatePreparer(app,sessions);}catch(java.io.IOException unavailable){lost=true;}preparer=prepared;if(sessions!=null)sessions.onInvalidated(invalidation);}
 private boolean current(){return !lost&&ticket!=null&&ticket.uid!=null&&sessions.current(ticket);}
 private void changed(){changes.setValue(++event);}
 public void clearPetPhotos(){entries.removeIf(e->e.importedPet);changed();}
 public void merge(Object ids){entries.removeIf(e->e.importedPet);for(String id:MediaGrid.ids(ids)){if(entries.size()>=limit)break;if(entries.stream().noneMatch(e->e.assetId.equals(id))){Entry imported=new Entry(id);imported.importedPet=true;entries.add(imported);}}changed();}
 public void restore(Object ids){if(!entries.isEmpty())return;for(String id:MediaGrid.ids(ids))entries.add(new Entry(id));changed();}
 public void add(List<Uri> uris){if(!current())return;for(Uri uri:uris){if(entries.size()>=limit)break;if(entries.stream().anyMatch(e->uri.equals(e.uri)))continue;Entry e=new Entry(uri);entries.add(e);upload(e);}changed();}
 private int uploading;
 public void upload(Entry e){if(!current()||!entries.contains(e)||e.state.equals("uploading")||e.state.equals("ready"))return;e.state="queued";drain();changed();}
 private void drain(){for(Entry e:new ArrayList<>(entries)){if(uploading>=2)return;if(e.state.equals("queued")){uploading++;performUpload(e);}}}
 private void performUpload(Entry e){if(!current()||!entries.contains(e)||e.state.equals("uploading")||e.state.equals("ready"))return;e.state="uploading";e.error="";changed();Tasks.call(worker,()->{if(e.candidate==null)e.candidate=preparer.prepare(ticket,getApplication().getContentResolver().openInputStream(e.uri));return Files.readAllBytes(e.candidate.file.toPath());}).continueWithTask(worker,t->{byte[] bytes=t.getResult();if(!current()||!entries.contains(e))return Tasks.forException(new IllegalStateException("session_changed"));return ManagedMediaClient.call("reserve",ManagedMediaClient.map("assetId",e.assetId,"operationId",e.operationId,"purpose",purpose,"inputHash",e.candidate.sha256)).continueWithTask(worker,r->{r.getResult();return ManagedMediaClient.upload(e.assetId,bytes);});}).addOnCompleteListener(t->{uploading--;if(!current()||!entries.contains(e)){drain();return;}e.state=t.isSuccessful()?"ready":"failed";e.error=t.isSuccessful()?"":String.valueOf(t.getException());if(t.isSuccessful()&&e.candidate!=null){preparer.release(e.candidate);e.candidate=null;}changed();drain();});}
 public String purpose="post_image";public int limit=100;
 public boolean ready(){return entries.stream().allMatch(e->e.state.equals("ready"));}
 public List<String> ids(){List<String> ids=new ArrayList<>();for(Entry e:entries)ids.add(e.assetId);return ids;}
 public void reset(){for(Entry e:entries)if(e.candidate!=null)preparer.release(e.candidate);entries.clear();changed();}
 public void remove(Entry e){if(e.state.equals("uploading"))return;entries.remove(e);if(e.candidate!=null){preparer.release(e.candidate);e.candidate=null;}changed();if(e.uri!=null)ManagedMediaClient.call("remove",ManagedMediaClient.map("assetId",e.assetId));}
 public void move(Entry e,int delta){int from=entries.indexOf(e),to=from+delta;if(from<0||to<0||to>=entries.size())return;entries.remove(from);entries.add(to,e);changed();}
 @Override protected void onCleared(){if(sessions!=null)sessions.removeInvalidation(invalidation);if(preparer!=null)preparer.close();worker.shutdownNow();entries.clear();}
}
