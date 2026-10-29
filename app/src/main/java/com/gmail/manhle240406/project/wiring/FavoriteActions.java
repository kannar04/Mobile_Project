package com.gmail.manhle240406.project.wiring;

import com.gmail.manhle240406.project.adoption.FavoritesRepository;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;

/** Fresh expected versions and desired state; no optimistic fake success or automatic retry. */
public final class FavoriteActions {
    private FavoriteActions() {}

    public static Task<Map<String, Object>> set(
            FirebaseFirestore db,
            SessionGateway sessions,
            SessionEpoch.Ticket t,
            FavoritesRepository repository,
            String id,
            boolean desired) {
        Task<DocumentSnapshot> item =
                db.document("users/" + t.uid + "/favorites/" + id).get(Source.SERVER);
        Task<DocumentSnapshot> meta =
                db.document("users/" + t.uid + "/favoriteMeta/main").get(Source.SERVER);
        return Tasks.whenAll(item, meta)
                .continueWithTask(
                        done -> {
                            if (!sessions.current(t))
                                return Tasks.forException(
                                        new IllegalStateException("session_changed"));
                            if (!done.isSuccessful())
                                return Tasks.forException(
                                        Objects.requireNonNull(done.getException()));
                            Long f = item.getResult().getLong("favoriteVersion"),
                                    m = meta.getResult().getLong("metaVersion");
                            return repository.setSaved(
                                    t,
                                    id,
                                    desired,
                                    UUID.randomUUID().toString(),
                                    f == null ? 0 : f,
                                    m == null ? 0 : m);
                        });
    }
}
