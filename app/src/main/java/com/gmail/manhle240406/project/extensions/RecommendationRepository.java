package com.gmail.manhle240406.project.extensions;

import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;

/** Three newest authorized pages; ranking is confined to this explicit refresh. */
public final class RecommendationRepository implements AutoCloseable {
    private final ListingDiscoveryRepository listings;
    private final SessionGateway sessions;
    private final DiscoveryTools.RequestGeneration generation =
            new DiscoveryTools.RequestGeneration();
    private final Runnable clear = generation::clear;

    public RecommendationRepository(ListingDiscoveryRepository listings, SessionGateway sessions) {
        this.listings = listings;
        this.sessions = sessions;
        sessions.onInvalidated(clear);
    }

    public void reset() {
        generation.reset();
    }

    public Task<List<ListingRow>> refresh(SessionEpoch.Ticket ticket) {
        if (ticket.uid == null || !sessions.current(ticket))
            throw new IllegalStateException("session_changed");
        long captured = generation.begin();
        return page(ticket, captured, null, 0, new LinkedHashMap<>())
                .continueWith(
                        done -> {
                            if (!sessions.current(ticket) || !generation.complete(captured))
                                throw new IllegalStateException("session_or_generation_changed");
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            return done.getResult();
                        });
    }

    private Task<List<ListingRow>> page(
            SessionEpoch.Ticket ticket,
            long captured,
            Map<String, Object> cursor,
            int count,
            LinkedHashMap<String, ListingRow> rows) {
        return listings.page(
                        ticket, new DiscoveryQuery("", "available", Collections.emptyMap()), cursor)
                .continueWithTask(
                        done -> {
                            if (!sessions.current(ticket) || !generation.current(captured))
                                return Tasks.forException(
                                        new IllegalStateException("session_changed"));
                            if (!done.isSuccessful())
                                return Tasks.forException(
                                        Objects.requireNonNull(done.getException()));
                            List<ListingRow> page = done.getResult().getDocuments();
                            for (ListingRow row : page) rows.putIfAbsent(row.getId(), row);
                            if (count == 2 || done.getResult().cursor == null)
                                return Tasks.forResult(
                                        Collections.unmodifiableList(
                                                new ArrayList<>(rows.values())));
                            return page(ticket, captured, done.getResult().cursor, count + 1, rows);
                        });
    }

    /**
     * Existing discovery schema has no approved care-interest tag field: unknown contributes zero.
     */
    public List<DiscoveryTools.Ranked> rank(
            List<ListingRow> snapshot,
            boolean enabled,
            Set<String> species,
            Set<String> regions,
            Set<String> interests) {
        List<DiscoveryTools.Candidate> input = new ArrayList<>();
        for (ListingRow row : snapshot) {
            DiscoveryProjection.validate(row.getData(), row.getId());
            input.add(
                    new DiscoveryTools.Candidate(
                            row.getId(),
                            row.getString("speciesId"),
                            row.getString("regionId"),
                            Collections.emptySet(),
                            Objects.requireNonNull(row.getTimestamp("firstPublishedAt"))
                                    .toDate()
                                    .getTime(),
                            "available".equals(row.getString("availability"))));
        }
        return DiscoveryTools.rank(input, enabled, species, regions, interests);
    }

    @Override
    public void close() {
        sessions.removeInvalidation(clear);
        generation.clear();
    }
}
