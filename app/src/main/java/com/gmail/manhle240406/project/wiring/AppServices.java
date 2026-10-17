package com.gmail.manhle240406.project.wiring;

import android.os.*;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.foundation.FirebaseEnvironment;
import com.gmail.manhle240406.project.preferences.*;
import com.gmail.manhle240406.project.security.*;
import com.google.firebase.auth.FirebaseAuth;
import java.util.concurrent.*;

/** App-owned bindings; no Storage SDK, privileged client or paid-service fallback. */
public final class AppServices {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    public final AppearanceRepository appearance;
    public final AccountSecurityRepository security;
    public AppServices(AniloveApplication app,FirebaseEnvironment env) {
        appearance=new AppearanceRepository(new AndroidAppearanceStore(app),worker);
        appearance.read().thenAccept(s->main.post(()->applyAppearance(s)));
        security=env.sessions==null?null:new AccountSecurityRepository(
            new FirebaseAccountSecurityProvider(FirebaseAuth.getInstance()),
            FirebaseAccountSecurityProvider.session(env.sessions),
            app.listingDrafts()::clearAccountAfterSignOut,SystemClock::elapsedRealtime,
            BuildConfig.PASSWORD_RESET_CONFIGURATION_VERIFIED);
    }
    public void applyAppearance(AppearanceRepository.State s) {
        AppCompatDelegate.setDefaultNightMode(s.theme==AppearanceRepository.Theme.DARK?AppCompatDelegate.MODE_NIGHT_YES:
            s.theme==AppearanceRepository.Theme.LIGHT?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        String tag=s.language==AppearanceRepository.Language.SYSTEM?"":s.language==AppearanceRepository.Language.EN?"en":"vi";
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag));
    }
}
