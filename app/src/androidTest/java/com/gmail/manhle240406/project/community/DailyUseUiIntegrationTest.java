package com.gmail.manhle240406.project.community;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import android.view.*;
import com.google.android.material.textfield.TextInputEditText;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.auth.AuthState;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real native recreation/session assertions. Requires an Android device + isolated emulators. */
@RunWith(AndroidJUnit4.class)
public final class DailyUseUiIntegrationTest {
    private AniloveApplication account()throws Exception {
        AniloveApplication app=ApplicationProvider.getApplicationContext();
        assertEquals("local_emulator",app.foundation().alias);
        CountDownLatch ready=new CountDownLatch(1);
        Observer<AuthState> observer=s->{if(s.uid!=null&&s.setupReady&&!s.pending)ready.countDown();};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            app.auth().logout();app.auth().state().observeForever(observer);
            app.auth().email("daily-"+UUID.randomUUID()+"@example.test","synthetic-password-123",true);
        });
        try{assertTrue(ready.await(30,TimeUnit.SECONDS));}
        finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().state().removeObserver(observer));}
        return app;
    }
    private TextInputEditText field(View v){if(v instanceof TextInputEditText)return (TextInputEditText)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextInputEditText e=field(g.getChildAt(i));if(e!=null)return e;}}return null;}
    @Test public void composerRotationKeepsMemoryButNoPrivateHierarchyOrSavedState()throws Exception {
        AniloveApplication app=account();AtomicReference<DailyViewModel> model=new AtomicReference<>();
        try(ActivityScenario<SocialComposerActivity> scenario=ActivityScenario.launch(SocialComposerActivity.class)){
            scenario.onActivity(a->{DailyViewModel m=new ViewModelProvider(a).get(DailyViewModel.class);model.set(m);TextInputEditText input=field(a.findViewById(android.R.id.content));assertNotNull(input);input.setText("Synthetic unsaved private moment");assertFalse(input.isSaveEnabled());assertEquals("Synthetic unsaved private moment",m.body);assertFalse(m.saved.contains("body"));assertFalse(m.saved.contains("reportText"));});
            scenario.recreate();scenario.onActivity(a->{assertSame(model.get(),new ViewModelProvider(a).get(DailyViewModel.class));assertEquals("Synthetic unsaved private moment",field(a.findViewById(android.R.id.content)).getText().toString());});
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue(model.get().lost);assertEquals("",model.get().body);assertNull(model.get().pending);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}
    }
    @Test public void contextualReportRotationKeepsTransientStatementAndLogoutClearsIt()throws Exception {
        AniloveApplication app=account();AtomicReference<DailyViewModel> model=new AtomicReference<>();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{model.set(new ViewModelProvider(a).get(DailyViewModel.class));ReportSheet.target("post",UUID.randomUUID().toString(),null,1,null).showNow(a.getSupportFragmentManager(),"report");ReportSheet sheet=(ReportSheet)a.getSupportFragmentManager().findFragmentByTag("report");TextInputEditText details=field(sheet.requireView());assertNotNull(details);details.setText("Synthetic private concern");assertFalse(details.isSaveEnabled());});
            scenario.recreate();scenario.onActivity(a->{ReportSheet sheet=(ReportSheet)a.getSupportFragmentManager().findFragmentByTag("report");assertNotNull(sheet);assertEquals("Synthetic private concern",field(sheet.requireView()).getText().toString());assertFalse(model.get().saved.contains("reportText"));});
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals("",model.get().reportText);assertTrue(model.get().lost);
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}
    }
    @Test public void coldPendingStateContainsIdsAndNeverRestoresOrReplaysStatement()throws Exception {
        AniloveApplication app=account();InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Map<String,Object> values=new HashMap<>();values.put("owner_uid",app.foundation().sessions.capture().uid);values.put("pending_op",UUID.randomUUID().toString());values.put("pending_kind","report");values.put("report_id",UUID.randomUUID().toString());DailyViewModel m=new DailyViewModel(app,new SavedStateHandle(values));assertTrue(m.restored);assertEquals("",m.reportText);assertNull(m.pendingReport);assertNull(m.pending);m.retry();assertNull(m.pendingReport);assertNull(m.pending);
        });InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());
    }
    private android.widget.Spinner spinner(View view){
        if(view instanceof android.widget.Spinner)return (android.widget.Spinner)view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.widget.Spinner found=spinner(group.getChildAt(i));if(found!=null)return found;}}
        return null;
    }
    @Test public void illustrationLoadAndImageOnlyEditSurviveRotation()throws Exception {
        AniloveApplication app=account();
        try(ActivityScenario<SocialComposerActivity> scenario=ActivityScenario.launch(SocialComposerActivity.class)){
            scenario.onActivity(a->a.showRevision(CommunitySchema.fields("Existing story","cat")));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{
                DailyViewModel m=new ViewModelProvider(a).get(DailyViewModel.class);
                android.widget.Spinner choice=spinner(a.findViewById(android.R.id.content));
                assertEquals(2,choice.getSelectedItemPosition());assertFalse(m.dirty);
                choice.setSelection(3);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a->{DailyViewModel m=new ViewModelProvider(a).get(DailyViewModel.class);assertTrue(m.dirty);assertEquals(3,m.illustration);});
            scenario.recreate();
            scenario.onActivity(a->{assertEquals(3,spinner(a.findViewById(android.R.id.content)).getSelectedItemPosition());assertTrue(new ViewModelProvider(a).get(DailyViewModel.class).dirty);});
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}
    }
    @Test public void socialReceiptsClearOnlyTheSubmittedCommentOnce()throws Exception {
        AniloveApplication app=account();
        android.content.Intent intent=new android.content.Intent(app,SocialDetailActivity.class).putExtra("post",UUID.randomUUID().toString());
        try(ActivityScenario<SocialDetailActivity> scenario=ActivityScenario.launch(intent)){
            scenario.onActivity(a->{
                DailyViewModel m=new ViewModelProvider(a).get(DailyViewModel.class);
                field(a.findViewById(android.R.id.content)).setText("Unsaved reply");
                for(String command:Arrays.asList("set_like","edit_comment","withdraw_comment","hide")){
                    m.socialCommitted(UUID.randomUUID().toString(),SocialRepository.map("command",command,"input",SocialRepository.map("body","Unsaved reply")));
                    m.changed();assertEquals("Unsaved reply",m.body);assertEquals("Unsaved reply",field(a.findViewById(android.R.id.content)).getText().toString());
                }
                m.socialCommitted(UUID.randomUUID().toString(),SocialRepository.map("command","add_comment","input",SocialRepository.map("body","Previous draft")));
                m.changed();assertEquals("Unsaved reply",m.body);assertEquals("Unsaved reply",field(a.findViewById(android.R.id.content)).getText().toString());
                String op=UUID.randomUUID().toString();
                Map<String,Object> receipt=SocialRepository.map("command","add_comment","input",SocialRepository.map("body","Unsaved reply"));
                m.socialCommitted(op,receipt);m.changed();assertEquals("",m.body);assertEquals("",field(a.findViewById(android.R.id.content)).getText().toString());
                field(a.findViewById(android.R.id.content)).setText("Unsaved reply");m.socialCommitted(op,receipt);m.changed();assertEquals("Unsaved reply",m.body);assertEquals("Unsaved reply",field(a.findViewById(android.R.id.content)).getText().toString());
            });
            scenario.recreate();
            scenario.onActivity(a->assertEquals("Unsaved reply",field(a.findViewById(android.R.id.content)).getText().toString()));
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync(()->app.auth().logout());}
    }
    @Test public void signingInReplacesTheLostFeedSession()throws Exception {
        AniloveApplication app=ApplicationProvider.getApplicationContext();
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->app.auth().logout());
        android.app.Instrumentation.ActivityMonitor monitor=null;
        android.app.Activity replacement=null;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->assertTrue(new ViewModelProvider(a).get(FeedViewModel.class).lost));
            monitor=instrumentation.addMonitor(MainActivity.class.getName(),null,false);
            account();
            replacement=instrumentation.waitForMonitorWithTimeout(monitor,10000);
            assertNotNull("Login must launch a fresh feed Activity",replacement);
            MainActivity fresh=(MainActivity)replacement;
            instrumentation.runOnMainSync(()->{
                assertFalse(new ViewModelProvider(fresh).get(FeedViewModel.class).lost);
                assertTrue(new ViewModelProvider(fresh).get(DailyViewModel.class).current());
            });
            instrumentation.waitForIdleSync();assertEquals(1,monitor.getHits());
        }finally{
            if(monitor!=null)instrumentation.removeMonitor(monitor);
            android.app.Activity cleanup=replacement;
            instrumentation.runOnMainSync(()->{if(cleanup!=null)cleanup.finish();app.auth().logout();});
        }
    }

}
