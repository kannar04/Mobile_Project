package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.UnavailableScreen;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.*;

import java.util.*;

/** Read-only current catalog selectors. No mobile Admin toggle or synthetic entries. */
public final class CatalogActivity extends AppCompatActivity {
    private CatalogRepository repository;
    private SessionGateway sessions;
    private SessionEpoch.Ticket ticket;
    private final List<DocumentSnapshot> rows = new ArrayList<>();
    private RecyclerView list;
    private Adapter adapter;
    private String kind = "species", species;
    private DocumentSnapshot cursor;
    private boolean busy, end;
    private long generation;
    private final Runnable invalidation =
            () -> {
                generation++;
                rows.clear();
                if (adapter != null) adapter.notifyDataSetChanged();
            };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_catalog);
        SocialViews.insets(findViewById(R.id.catalog_root));
        SocialViews.privateView(findViewById(R.id.catalog_root));
        findViewById(R.id.catalog_back).setOnClickListener(v -> finish());
        FirebaseEnvironment env = ((AniloveApplication) getApplication()).foundation();
        sessions = env.sessions;
        ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            UnavailableScreen.show(this, R.string.wiring_auth_required);
            return;
        }
        repository = new CatalogRepository(FirebaseFirestore.getInstance(), sessions);
        sessions.onInvalidated(invalidation);
        list = findViewById(R.id.catalog_recycler);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);
        load();
    }

    private boolean current() {
        return !isDestroyed() && !isFinishing() && sessions.current(ticket);
    }

    private void load() {
        if (busy || end || !current()) return;
        busy = true;
        long g = ++generation;
        repository
                .page(ticket, kind, species, cursor)
                .addOnCompleteListener(
                        done -> {
                            if (!current() || g != generation) return;
                            busy = false;
                            if (!done.isSuccessful()) {
                                Snackbar.make(list, R.string.wiring_failed, Snackbar.LENGTH_LONG)
                                        .show();
                                adapter.notifyDataSetChanged();
                                return;
                            }
                            for (DocumentSnapshot d : done.getResult())
                                if (d.getLong("schemaVersion") != null
                                        && d.getLong("schemaVersion") == 1
                                        && kind.equals(d.getString("kind"))) rows.add(d);
                            List<DocumentSnapshot> raw = done.getResult().getDocuments();
                            if (!raw.isEmpty()) cursor = raw.get(raw.size() - 1);
                            end = raw.size() < 50;
                            adapter.notifyDataSetChanged();
                        });
    }

    private void choose() {
        new MaterialAlertDialogBuilder(this)
                .setItems(
                        new String[] {
                            getString(R.string.wiring_species), getString(R.string.wiring_regions)
                        },
                        (d, w) -> {
                            kind = w == 0 ? "species" : "region";
                            species = null;
                            cursor = null;
                            end = false;
                            rows.clear();
                            load();
                        })
                .show();
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (sessions != null) sessions.removeInvalidation(invalidation);
        super.onDestroy();
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        public int getItemCount() {
            return rows.size() + 2;
        }

        public int getItemViewType(int p) {
            return p == 0 || p == rows.size() + 1 ? 1 : 0;
        }

        public Holder onCreateViewHolder(ViewGroup p, int t) {
            return new Holder(
                    t == 1
                            ? SocialViews.button(
                                    CatalogActivity.this, getString(R.string.wiring_more), () -> {})
                            : getLayoutInflater().inflate(R.layout.item_catalog, p, false));
        }

        public void onBindViewHolder(Holder h, int p) {
            if (p == 0) {
                ((TextView) h.itemView).setText(R.string.catalog_title);
                h.itemView.setOnClickListener(v -> choose());
                return;
            }
            if (p == rows.size() + 1) {
                ((TextView) h.itemView)
                        .setText(
                                busy
                                        ? R.string.wiring_loading
                                        : rows.isEmpty()
                                                ? R.string.wiring_empty
                                                : end ? R.string.social_end : R.string.wiring_more);
                h.itemView.setOnClickListener(v -> load());
                return;
            }
            DocumentSnapshot d = rows.get(p - 1);
            Map<String, Object> labels = (Map<String, Object>) d.get("labels");
            String locale = getResources().getConfiguration().getLocales().get(0).getLanguage();
            Object label = labels == null ? null : labels.get("en".equals(locale) ? "en" : "vi");
            ((TextView) h.itemView.findViewById(R.id.catalog_name))
                    .setText(label instanceof String ? (String) label : d.getId());
            h.itemView.findViewById(R.id.catalog_switch).setVisibility(View.GONE);
            h.itemView.setOnClickListener(
                    v -> {
                        if ("species".equals(kind)) {
                            species = d.getId();
                            kind = "breed";
                            cursor = null;
                            rows.clear();
                            end = false;
                            load();
                        }
                    });
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
