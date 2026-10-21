package com.gmail.manhle240406.project.community;
import android.app.job.*;
import com.gmail.manhle240406.project.AniloveApplication;
public final class CommunitySyncJob extends JobService {
 @Override public boolean onStartJob(JobParameters p){((AniloveApplication)getApplication()).communityOutbox().drain(()->jobFinished(p,false));return true;}
 @Override public boolean onStopJob(JobParameters p){((AniloveApplication)getApplication()).communityOutbox().stop();return true;}
}
