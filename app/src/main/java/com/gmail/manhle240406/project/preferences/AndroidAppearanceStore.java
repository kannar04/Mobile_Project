package com.gmail.manhle240406.project.preferences;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Construct/use through AppearanceRepository's serial worker, never commit on the UI thread. */
public final class AndroidAppearanceStore implements AppearanceRepository.Store {
    private final SharedPreferences prefs;

    public AndroidAppearanceStore(Context context) {
        prefs =
                context.getApplicationContext()
                        .getSharedPreferences("presentation_preferences_v1", Context.MODE_PRIVATE);
    }

    @Override
    public AppearanceRepository.State read() {
        try {
            return AppearanceRepository.decode(
                    prefs.getString("languageMode", "system"),
                    prefs.getString("themeMode", "system"),
                    prefs.getLong("preferenceVersion", 0));
        } catch (ClassCastException e) {
            return AppearanceRepository.decode(null, null, 0);
        }
    }

    @Override
    public boolean write(AppearanceRepository.State s) {
        return prefs.edit()
                .clear()
                .putString("languageMode", s.language.name().toLowerCase(Locale.ROOT))
                .putString("themeMode", s.theme.name().toLowerCase(Locale.ROOT))
                .putLong("preferenceVersion", s.version)
                .commit();
    }
}
