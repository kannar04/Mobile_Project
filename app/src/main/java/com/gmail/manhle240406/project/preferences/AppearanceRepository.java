package com.gmail.manhle240406.project.preferences;

import java.util.*;
import java.util.concurrent.*;

/**
 * Installation preferences only. Applying these to AppCompat belongs to the separate wiring scope.
 */
public final class AppearanceRepository {
    public enum Language {
        SYSTEM,
        VI,
        EN
    }

    public enum Theme {
        SYSTEM,
        LIGHT,
        DARK
    }

    public static final class State {
        public final Language language;
        public final Theme theme;
        public final long version;

        public State(Language l, Theme t, long v) {
            if (l == null || t == null || v < 0) throw new IllegalArgumentException("preferences");
            language = l;
            theme = t;
            version = v;
        }

        public String displayLanguage(List<Locale> system) {
            if (language != Language.SYSTEM) return language.name().toLowerCase(Locale.ROOT);
            for (Locale l : system)
                if (l != null && Arrays.asList("vi", "en").contains(l.getLanguage()))
                    return l.getLanguage();
            return "vi";
        }
    }

    public interface Store {
        State read();

        boolean write(State state);
    }

    private final Store store;
    private final Executor writer;
    private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);

    public AppearanceRepository(Store store, Executor serialWriter) {
        this.store = store;
        writer = serialWriter;
    }

    private synchronized CompletionStage<State> enqueue(
            java.util.function.Supplier<State> operation) {
        CompletableFuture<State> next =
                tail.handle((v, e) -> null).thenApplyAsync(v -> operation.get(), writer);
        tail = next.handle((v, e) -> null);
        return next;
    }

    public CompletionStage<State> read() {
        return enqueue(store::read);
    }

    public CompletionStage<State> set(Language language, Theme theme) {
        Objects.requireNonNull(language);
        Objects.requireNonNull(theme);
        return enqueue(
                () -> {
                    State before = store.read();
                    State next = new State(language, theme, Math.addExact(before.version, 1));
                    if (!store.write(next))
                        throw new IllegalStateException("preference_save_failed");
                    return next;
                });
    }

    public static State decode(String language, String theme, long version) {
        Language l;
        Theme t;
        try {
            l = Language.valueOf(language.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            l = language == null ? Language.SYSTEM : Language.VI;
        }
        try {
            t = Theme.valueOf(theme.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            t = Theme.SYSTEM;
        }
        return new State(l, t, Math.max(0, version));
    }
}
