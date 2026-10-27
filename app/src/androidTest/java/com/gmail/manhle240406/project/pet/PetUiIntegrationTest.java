package com.gmail.manhle240406.project.pet;
import android.view.*;
import androidx.test.core.app.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthState;
import com.google.android.material.textfield.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Requires the configured Android emulator and synthetic Auth/Firestore services. */
@RunWith(AndroidJUnit4.class)
public class PetUiIntegrationTest {
    @Test public void reusablePrivateFieldDoesNotSaveItsText(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{TextInputLayout l=(TextInputLayout)LayoutInflater.from(new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),R.style.Theme_Project_Auth)).inflate(R.layout.pet_field,null,false);assertFalse(l.getEditText().isSaveEnabled());assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO,l.getEditText().getImportantForAutofill());});}
    @Test public void rotationRetainsPrivateMemoryAndOperationIdentityButLogoutClearsBoth() throws Exception {
        AniloveApplication app=ApplicationProvider.getApplicationContext();assertEquals("local_emulator",app.foundation().alias);CountDownLatch ready=new CountDownLatch(1);Observer<AuthState> observer=s->{if(s.uid!=null&&s.setupReady&&!s.pending)ready.countDown();};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{app.auth().logout();app.auth().state().observeForever(observer);app.auth().email("pet-"+UUID.randomUUID()+"@example.test","synthetic-password-123",true);});
        try{assertTrue(ready.await(25,TimeUnit.SECONDS));}finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().state().removeObserver(observer));}
        try(ActivityScenario<PetActivity> scenario=ActivityScenario.launch(PetActivity.class)){
            AtomicReference<PetViewModel> model=new AtomicReference<>();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<end){scenario.onActivity(a->model.set(new ViewModelProvider(a).get(PetViewModel.class)));if(model.get().page!=null)break;Thread.sleep(50);}assertNotNull(model.get().page);
            String operation=UUID.randomUUID().toString();scenario.onActivity(a->{model.get().newPet();model.get().text.put("explanation","Synthetic private declaration");model.get().text.put("chip","Synthetic private chip");model.get().remember(new PetCommand("register_pet",model.get().petId,operation,0,0,0,0,Collections.emptyMap()));});
            scenario.recreate();scenario.onActivity(a->{PetViewModel kept=new ViewModelProvider(a).get(PetViewModel.class);assertSame(model.get(),kept);assertEquals("Synthetic private declaration",kept.text.get("explanation"));assertEquals("Synthetic private chip",kept.text.get("chip"));assertNull(kept.pending);assertFalse(a.findViewById(R.id.pet_root).isSaveEnabled());});
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue(model.get().lost);assertTrue(model.get().text.isEmpty());assertNull(model.get().pending);assertNull(model.get().detail);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}
    }
}
