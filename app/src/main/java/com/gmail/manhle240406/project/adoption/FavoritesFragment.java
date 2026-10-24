package com.gmail.manhle240406.project.adoption;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.*;
import com.google.firebase.firestore.*;
import com.google.android.material.snackbar.Snackbar;

/**
 * Existing saved-list surface; only current own references and authorized live projections.
 */
public final class FavoritesFragment extends com.gmail.manhle240406.project.wiring.MainPageFragment {
    private FavoritesRepository repo;
    private ListingDiscoveryRepository listings;
    private SessionGateway sessions;
    private SessionEpoch.Ticket ticket;
    private final java.util.ArrayDeque<Runnable> resolutions = new java.util.ArrayDeque<>();
    private int resolving;
    private LinearLayout content;
    private TextView status;
    private FavoriteRows adapter;
    private DocumentSnapshot cursor;
    private boolean busy, end, mutation;
    private long generation;
    private final Runnable invalidation = () -> {
        generation++;
        resolutions.clear();
        if (content != null) content.removeAllViews();
    };

    @Override
    protected void createPage(Bundle state) {
        LinearLayout root = SocialViews.column(requireContext());
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundResource(R.color.anilove_bg);
        setContentView(root);
        SocialSurface.header(requireActivity(), root, R.string.favorites_title, this::finish);
        content = SocialViews.column(requireActivity());
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        status = SocialSurface.muted(requireActivity(), getString(R.string.wiring_loading));
        content.addView(status);
        SocialViews.privateView(root);
        FirebaseEnvironment env = ((AniloveApplication) getApplication()).foundation();
        sessions = env.sessions;
        ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            status.setText(R.string.wiring_auth_required);
            return;
        }
        androidx.recyclerview.widget.RecyclerView list = new androidx.recyclerview.widget.RecyclerView(requireActivity());
        list.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(requireActivity()));
        adapter = new FavoriteRows(requireActivity(), row -> startActivity(new Intent(requireActivity(), ListingDetailActivity.class).putExtra("listing", row.id)), row -> {
            if (mutation || !current()) return;
            mutation = true;
            adapter.mutation = true;
            adapter.notifyDataSetChanged();
            FavoriteActions.set(FirebaseFirestore.getInstance(), sessions, ticket, repo, row.id, false).addOnCompleteListener(result -> {
                if (!current()) return;
                mutation = false;
                adapter.mutation = false;
                if (result.isSuccessful()) adapter.rows.remove(row);
                adapter.notifyDataSetChanged();
                Snackbar.make(content, result.isSuccessful() ? R.string.wiring_saved : R.string.wiring_unknown, Snackbar.LENGTH_LONG).show();
            });
        }, this::page);
        list.setAdapter(adapter);
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        sessions.onInvalidated(invalidation);
        repo = new FavoritesRepository(FirebaseFirestore.getInstance(), sessions);
        listings = new ListingDiscoveryRepository(FirebaseFirestore.getInstance(), sessions);
        page();
    }

    private final Runnable refreshSources = () -> {
        if (listings == null || adapter == null || !current()) return;
        for (FavoriteRows.Row row : adapter.rows) {
            row.source = null;
            row.resolved = false;
        }
        adapter.notifyDataSetChanged();
        listings.clear();
        cursor = null;
        end = false;
        adapter.rows.clear();
        page();
    };

    @Override
    public void onStart() {
        super.onStart();
        BrowsePulse.add(refreshSources);
        refreshSources.run();
    }

    @Override
    public void onStop() {
        BrowsePulse.remove(refreshSources);
        super.onStop();
    }

    private boolean current() {
        return !isDestroyed() && !isFinishing() && sessions != null && sessions.current(ticket);
    }

    private void page() {
        if (busy || end || !current()) return;
        busy = true;
        adapter.busy = true;
        adapter.failed = false;
        adapter.notifyDataSetChanged();
        long g = ++generation;
        status.setText(R.string.wiring_loading);
        repo.page(ticket, cursor).addOnCompleteListener(done -> {
            if (!current() || g != generation) return;
            busy = false;
            adapter.busy = false;
            if (!done.isSuccessful()) {
                status.setText(R.string.wiring_failed);
                adapter.failed = true;
                adapter.notifyDataSetChanged();
                return;
            }
            status.setText(done.getResult().isEmpty() && cursor == null ? R.string.community_saved_empty : R.string.ui_saved_private);
            if (!done.getResult().isEmpty())
                cursor = done.getResult().getDocuments().get(done.getResult().size() - 1);
            end = done.getResult().size() < 20;
            adapter.end = end;
            for (DocumentSnapshot ref : done.getResult()) row(ref, g);
            adapter.notifyDataSetChanged();
        });
    }

    private void row(DocumentSnapshot ref, long g) {
        if (adapter.rows.stream().anyMatch(x -> x.id.equals(ref.getId()))) return;
        FavoriteRows.Row row = new FavoriteRows.Row(ref.getId());
        adapter.rows.add(row);
        resolutions.add(() -> repo.resolve(ticket, listings, row.id).addOnCompleteListener(result -> {
            resolving--;
            if (!current()) return;
            row.source = result.isSuccessful() ? result.getResult() : null;
            row.resolved = true;
            adapter.notifyDataSetChanged();
            drain();
        }));
        drain();
    }

    private void drain() {
        while (current() && resolving < 3 && !resolutions.isEmpty()) {
            resolving++;
            resolutions.remove().run();
        }
    }

    @Override
    public void onDestroyView() {
        generation++;
        resolutions.clear();
        if (repo != null) repo.close();
        if (listings != null) listings.close();
        if (sessions != null) sessions.removeInvalidation(invalidation);
        super.onDestroyView();
    }
}
