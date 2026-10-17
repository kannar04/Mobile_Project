package com.gmail.manhle240406.project.preferences;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import static com.gmail.manhle240406.project.preferences.AppearanceRepository.*;
public class AppearanceRepositoryTest {
    static class Memory implements Store {
        State value=new State(Language.SYSTEM,Theme.SYSTEM,0); boolean writable=true;
        public State read() { return value; } public boolean write(State s) { if(!writable)return false; value=s;return true; }
    }
    @Test public void systemLanguageUsesFirstSupportedLocaleThenVietnameseFallback() {
        State s=decode(null,"unknown",0);
        assertEquals("en",s.displayLanguage(Arrays.asList(Locale.FRANCE,Locale.UK)));
        assertEquals("vi",s.displayLanguage(Collections.singletonList(Locale.JAPAN)));
        assertEquals(Theme.SYSTEM,s.theme);
    }
    @Test public void explicitLanguageNeverTranslatesOrDependsOnAccount() {
        Memory m=new Memory(); AppearanceRepository r=new AppearanceRepository(m,Runnable::run);
        State s=r.set(Language.EN,Theme.DARK).toCompletableFuture().join();
        assertEquals("en",s.displayLanguage(Collections.singletonList(new Locale("vi"))));
        assertEquals(1,s.version); assertEquals(Theme.DARK,r.read().toCompletableFuture().join().theme);
        assertEquals(Theme.DARK,new AppearanceRepository(m,Runnable::run).read().toCompletableFuture().join().theme);
    }
    @Test public void persistenceFailureKeepsPreviousAcknowledgedSettings() {
        Memory m=new Memory(); m.writable=false;
        try { new AppearanceRepository(m,Runnable::run).set(Language.EN,Theme.DARK).toCompletableFuture().join(); fail(); }
        catch(CompletionException expected) { assertEquals("preference_save_failed",expected.getCause().getMessage()); }
        assertEquals(0,m.value.version); assertEquals(Theme.SYSTEM,m.value.theme);
    }
    @Test public void corruptEnumsAndNegativeVersionResetOnlyPreferenceValues() {
        State s=decode("evil","theme",-5); assertEquals(Language.VI,s.language); assertEquals(Theme.SYSTEM,s.theme); assertEquals(0,s.version);
    }
    @Test public void rapidDesiredStateWritesAreSerializedEvenOnParallelExecutor() throws Exception {
        Memory m=new Memory(); ExecutorService workers=Executors.newFixedThreadPool(3);
        try {
            AppearanceRepository r=new AppearanceRepository(m,workers);
            CompletionStage<State> first=r.set(Language.VI,Theme.LIGHT);
            CompletionStage<State> second=r.set(Language.EN,Theme.DARK);
            CompletionStage<State> last=r.set(Language.SYSTEM,Theme.SYSTEM);
            assertEquals(1,first.toCompletableFuture().get(2,TimeUnit.SECONDS).version);
            assertEquals(2,second.toCompletableFuture().get(2,TimeUnit.SECONDS).version);
            State saved=last.toCompletableFuture().get(2,TimeUnit.SECONDS);
            assertEquals(3,saved.version);assertEquals(Theme.SYSTEM,saved.theme);
        } finally { workers.shutdownNow(); }
    }
}
