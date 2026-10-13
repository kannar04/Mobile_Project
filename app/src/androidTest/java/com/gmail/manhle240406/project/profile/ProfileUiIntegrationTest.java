package com.gmail.manhle240406.project.profile;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;
import android.view.LayoutInflater;
import android.view.ContextThemeWrapper;
import android.view.View;
import com.gmail.manhle240406.project.R;
import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.auth.AuthState;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Runtime requires a connected emulator/device; source compilation is not a passed runtime test. */
@RunWith(AndroidJUnit4.class)
public class ProfileUiIntegrationTest {
    private AniloveApplication account() throws Exception {
        AniloveApplication app=ApplicationProvider.getApplicationContext();
        assertEquals("local_emulator",app.foundation().alias);
        CountDownLatch ready=new CountDownLatch(1);
        androidx.lifecycle.Observer<AuthState> observer=s->{if(s.uid!=null && s.setupReady && !s.pending)ready.countDown();};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            app.auth().logout();app.auth().state().observeForever(observer);
            app.auth().email("profile-"+UUID.randomUUID()+"@example.test","synthetic-password-123",true);
        });
        try{assertTrue("Account bootstrap must finish",ready.await(25,TimeUnit.SECONDS));}
        finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().state().removeObserver(observer));}
        return app;
    }
    private void awaitLoaded(ActivityScenario<ProfileActivity> scenario) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        AtomicReference<Boolean> loaded=new AtomicReference<>(false);
        while(System.nanoTime()<end){scenario.onActivity(a->loaded.set(new ViewModelProvider(a).get(ProfileViewModel.class).loaded));if(loaded.get())return;Thread.sleep(50);}
        fail("Profile server load did not finish");
    }
    @Test public void privateFieldsOptOutOfHierarchyPersistenceAndHaveSeparateActions(){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            View view=LayoutInflater.from(new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),R.style.Theme_Project_Auth)).inflate(R.layout.activity_profile,null,false);
            for(int id:new int[]{R.id.profile_name,R.id.profile_bio,R.id.profile_phone,R.id.profile_avatar,R.id.profile_region})assertFalse(view.findViewById(id).isSaveEnabled());
            assertNotNull(view.findViewById(R.id.profile_save_contact));assertNotNull(view.findViewById(R.id.profile_hide));assertNotNull(view.findViewById(R.id.profile_preview));
        });
    }
    @Test public void rotationRetainsMemoryDraftButDoesNotWriteOrSerializePrivateFields() throws Exception {
        AniloveApplication app=account();String uid=com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        try(ActivityScenario<ProfileActivity> activity=ActivityScenario.launch(ProfileActivity.class)){
            awaitLoaded(activity);AtomicReference<ProfileViewModel> original=new AtomicReference<>();
            activity.onActivity(a->{original.set(new ViewModelProvider(a).get(ProfileViewModel.class));((TextInputEditText)a.findViewById(R.id.profile_name)).setText("Synthetic alias");((TextInputEditText)a.findViewById(R.id.profile_phone)).setText("+84912345678");});
            activity.recreate();activity.onActivity(a->{assertSame(original.get(),new ViewModelProvider(a).get(ProfileViewModel.class));assertEquals("+84912345678",((TextInputEditText)a.findViewById(R.id.profile_phone)).getText().toString());assertFalse(a.findViewById(R.id.profile_phone).isSaveEnabled());assertNull(original.get().retry);});
            assertFalse(Tasks.await(FirebaseFirestore.getInstance().document("users/"+uid+"/private/contact").get(Source.SERVER),15,TimeUnit.SECONDS).exists());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue(original.get().sessionLost);assertEquals("",original.get().phone);assertNull(original.get().snapshot);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}
    }
}
