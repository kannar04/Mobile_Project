package com.gmail.manhle240406.project.extensions;

import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;
import com.google.firebase.firestore.*;

import java.util.*;

/** Manual coarse-region discovery works without location permission or a map service. */
public final class NearbyRepository implements AutoCloseable {
    public interface PlacesProvider {
        Task<List<Map<String, Object>>> search(String category, String regionId);
    }

    public static final PlacesProvider NO_PROVIDER =
            (category, region) ->
                    Tasks.forException(
                            new IllegalStateException("maps_places_service_not_authorized"));
    private final ListingDiscoveryRepository listings;
    private final SessionGateway sessions;
    private final PlacesProvider places;
    private final DiscoveryTools.RequestGeneration generation =
            new DiscoveryTools.RequestGeneration();
    private final Runnable invalidate = generation::clear;

    public NearbyRepository(
            ListingDiscoveryRepository listings, SessionGateway sessions, PlacesProvider provider) {
        this.listings = listings;
        this.sessions = sessions;
        places = Objects.requireNonNull(provider);
        sessions.onInvalidated(invalidate);
    }

    private void current(SessionEpoch.Ticket t) {
        if (t.uid == null || !sessions.current(t))
            throw new IllegalStateException("session_changed");
    }

    public void newArea() {
        generation.reset();
    }

    public Task<List<ListingRow>> regionPages(
            SessionEpoch.Ticket ticket, List<String> regionIds, String speciesId) {
        current(ticket);
        if (regionIds.isEmpty()
                || regionIds.size() > 3
                || new HashSet<>(regionIds).size() != regionIds.size())
            throw new IllegalArgumentException("three_explicit_regions_max");
        long captured = generation.begin();
        List<Task<ListingDiscoveryRepository.Page>> pages = new ArrayList<>();
        try {
            for (String region : regionIds) {
                AdoptionValues.catalogId(region);
                Map<String, String> filters = new HashMap<>();
                filters.put("regionId", region);
                if (speciesId != null) filters.put("speciesId", speciesId);
                pages.add(
                        listings.page(ticket, new DiscoveryQuery("", "available", filters), null));
            }
        } catch (RuntimeException e) {
            generation.complete(captured);
            throw e;
        }
        return Tasks.whenAll(pages)
                .continueWith(
                        done -> {
                            current(ticket);
                            if (!generation.complete(captured))
                                throw new IllegalStateException("query_generation_changed");
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            Map<String, ListingRow> unique = new LinkedHashMap<>();
                            for (Task<ListingDiscoveryRepository.Page> page : pages)
                                for (ListingRow row : page.getResult().getDocuments())
                                    unique.putIfAbsent(row.getId(), row);
                            return Collections.unmodifiableList(new ArrayList<>(unique.values()));
                        });
    }

    public Task<List<Map<String, Object>>> places(
            SessionEpoch.Ticket ticket, String category, String region) {
        current(ticket);
        if (!Arrays.asList("vets", "pet_shops").contains(category))
            throw new IllegalArgumentException("place_category");
        AdoptionValues.catalogId(region);
        long captured = generation.begin();
        return com.google.android.gms.tasks.Tasks.withTimeout(
                        places.search(category, region), 15, java.util.concurrent.TimeUnit.SECONDS)
                .continueWith(
                        done -> {
                            current(ticket);
                            if (!generation.complete(captured))
                                throw new IllegalStateException("query_generation_changed");
                            if (!done.isSuccessful())
                                throw Objects.requireNonNull(done.getException());
                            List<Map<String, Object>> rows = done.getResult();
                            if (rows.size() > 20)
                                throw new IllegalStateException("provider_page_bound");
                            return Collections.unmodifiableList(rows);
                        });
    }

    @Override
    public void close() {
        sessions.removeInvalidation(invalidate);
        generation.clear();
    }
}
