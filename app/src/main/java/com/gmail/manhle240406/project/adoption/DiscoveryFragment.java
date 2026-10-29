package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialSurface;
import com.gmail.manhle240406.project.community.SocialViews;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.*;

import java.util.*;

/** Existing grid uses current authorized projection; saved interests never imply reservation. */
public final class DiscoveryFragment extends MainPageFragment {
    private ListingDiscoveryRepository reader;
    private FavoritesRepository favorites;
    private SessionGateway sessions;
    private SessionEpoch.Ticket ticket;
    private List<ListingRow> rows = new ArrayList<>();
    private Map<String, Object> cursor;
    private boolean busy, end, mutation;
    private final Map<String, Boolean> savedStates = new HashMap<>();
    private final Set<String> savedReads = new HashSet<>();
    private View searchHeader;
    private com.google.android.material.chip.ChipGroup filterChips;
    private RecyclerView list;
    private Adapter adapter;
    private DiscoveryQuery query;
    private int message = R.string.wiring_loading;
    private long generation;

    public static final class Window extends androidx.lifecycle.ViewModel {
        final List<ListingRow> rows = new ArrayList<>();
        Map<String, Object> cursor;
        DiscoveryQuery query;
        boolean end;
        String uid, draft;
        android.os.Parcelable anchor;
    }

    private final android.os.Handler searchHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;
    private Window window;
    private android.widget.TextView order;
    private androidx.appcompat.app.AlertDialog dialog;
    private final Runnable invalidation =
            () -> {
                generation++;
                busy = false;
                end = true;
                message = R.string.wiring_auth_required;
                rows.clear();
                savedStates.clear();
                savedReads.clear();
                if (adapter != null) adapter.notifyDataSetChanged();
                if (dialog != null) dialog.dismiss();
            };

    @Override
    protected void createPage(Bundle state) {
        setContentView(R.layout.activity_discovery);
        View root = findViewById(R.id.discovery_root);
        SocialViews.privateView(root);
        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.discovery_back))
                .setNavigationOnClickListener(v -> finish());
        FirebaseEnvironment env = ((AniloveApplication) getApplication()).foundation();
        sessions = env.sessions;
        ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            ((android.view.ViewGroup) root).removeAllViews();
            ((android.view.ViewGroup) root)
                    .addView(
                            SocialViews.text(
                                    requireActivity(),
                                    getString(R.string.wiring_auth_required),
                                    false));
            return;
        }
        window = new androidx.lifecycle.ViewModelProvider(this).get(Window.class);
        if (!java.util.Objects.equals(window.uid, ticket.uid)) {
            window.rows.clear();
            window.cursor = null;
            window.query = null;
            window.end = false;
            window.anchor = null;
            window.draft = null;
            window.uid = ticket.uid;
        }
        rows = window.rows;
        cursor = window.cursor;
        end = window.end;
        sessions.onInvalidated(invalidation);
        reader = new ListingDiscoveryRepository(FirebaseFirestore.getInstance(), sessions);
        favorites = new FavoritesRepository(FirebaseFirestore.getInstance(), sessions);
        list = findViewById(R.id.discovery_recycler);
        float width =
                getResources().getDisplayMetrics().widthPixels
                        / getResources().getDisplayMetrics().density;
        int columns =
                Math.max(
                        1,
                        Math.min(
                                4,
                                (int)
                                        (width
                                                / (180
                                                        * Math.max(
                                                                1,
                                                                getResources()
                                                                        .getConfiguration()
                                                                        .fontScale)))));
        GridLayoutManager layout = new GridLayoutManager(requireActivity(), columns);
        list.setLayoutManager(layout);
        layout.setSpanSizeLookup(
                new GridLayoutManager.SpanSizeLookup() {
                    public int getSpanSize(int p) {
                        return p == 0 || p == rows.size() + 1 ? columns : 1;
                    }
                });
        adapter = new Adapter();
        list.setAdapter(adapter);
        String raw = state == null ? args().getString("search") : state.getString("prefix", "");
        java.util.Map<String, String> restoredFilters = new java.util.TreeMap<>();
        if (state != null) {
            Bundle stored = state.getBundle("filters");
            if (stored != null)
                for (String key : stored.keySet())
                    if (DiscoveryQuery.FILTERS.contains(key))
                        restoredFilters.put(key, stored.getString(key));
        }
        try {
            query =
                    window.query != null
                            ? window.query
                            : new DiscoveryQuery(
                                    raw == null ? "" : raw,
                                    state == null
                                            ? "available"
                                            : state.getString("availability", "available"),
                                    restoredFilters);
        } catch (IllegalArgumentException e) {
            query = new DiscoveryQuery("", "available", Collections.emptyMap());
        }
        EditText input = findViewById(R.id.discovery_search);
        input.setSingleLine();
        input.setText(
                window.draft != null
                        ? window.draft
                        : state != null ? state.getString("draft", query.prefix) : query.prefix);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.addTextChangedListener(
                new android.text.TextWatcher() {
                    public void beforeTextChanged(
                            CharSequence text, int start, int count, int after) {}

                    public void afterTextChanged(android.text.Editable e) {}

                    public void onTextChanged(CharSequence text, int start, int before, int count) {
                        window.draft = text.toString();
                        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
                        pendingSearch =
                                () -> {
                                    if (!current()) return;
                                    try {
                                        DiscoveryQuery selected =
                                                new DiscoveryQuery(
                                                        input.getText().toString(),
                                                        query.availability,
                                                        query.filters);
                                        if (!selected.signature.equals(query.signature))
                                            apply(selected);
                                        input.setError(null);
                                    } catch (IllegalArgumentException e) {
                                        input.setError(getString(R.string.wiring_search_invalid));
                                    }
                                };
                        searchHandler.postDelayed(pendingSearch, 300);
                    }
                });
        input.setOnEditorActionListener(
                (v, action, event) -> {
                    try {
                        apply(
                                new DiscoveryQuery(
                                        input.getText().toString(),
                                        query.availability,
                                        query.filters));
                        input.setError(null);
                    } catch (IllegalArgumentException e) {
                        input.setError(getString(R.string.wiring_search_invalid));
                    }
                    return true;
                });
        android.widget.LinearLayout controls = findViewById(R.id.discovery_controls);
        android.view.View filter =
                SocialViews.button(
                        requireActivity(), getString(R.string.flow_filters), () -> filters());
        filter.setId(R.id.flow_filters);
        controls.addView(filter);
        controls.addView(
                SocialViews.button(
                        requireActivity(),
                        getString(R.string.flow_clear_filters),
                        () -> {
                            apply(
                                    new DiscoveryQuery(
                                            query.prefix, "available", Collections.emptyMap()));
                        }));
        controls.addView(
                SocialSurface.quiet(
                        requireActivity(),
                        getString(R.string.flow_recommendations),
                        () -> {
                            dialog =
                                    new MaterialAlertDialogBuilder(requireActivity())
                                            .setTitle(R.string.flow_recommendations)
                                            .setMessage(R.string.flow_recommendation_help)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                        }));
        for (int i = 0; i < controls.getChildCount(); i++) {
            View child = controls.getChildAt(i);
            child.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, -2, 1));
            if (child instanceof android.widget.TextView)
                ((android.widget.TextView) child).setMaxLines(2);
        }
        order = findViewById(R.id.flow_filter_summary);
        filterChips = findViewById(R.id.discovery_chips);
        summary();
        LinearLayout container = (LinearLayout) root;
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.VERTICAL);
        java.util.List<View> headerViews = new ArrayList<>();
        for (int index = 1; index < container.getChildCount(); index++) {
            View child = container.getChildAt(index);
            if (child != list) headerViews.add(child);
        }
        for (View child : headerViews) {
            container.removeView(child);
            header.addView(child, new LinearLayout.LayoutParams(-1, -2));
        }
        searchHeader = header;
        list.setAdapter(new ConcatAdapter(new Header(), adapter));
        list.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView v, int dx, int dy) {
                        if (dy > 0
                                && ((GridLayoutManager) list.getLayoutManager())
                                                .findLastVisibleItemPosition()
                                        >= rows.size() - 5) load();
                    }
                });
        if (rows.isEmpty() && !end) load();
        if (window.anchor != null) list.getLayoutManager().onRestoreInstanceState(window.anchor);
        if (state == null && args().getBoolean("filters", false)) filters();
        String id = args().getString("listing");
        if (id != null && com.gmail.manhle240406.project.pet.PetSchema.id(id))
            reader.detail(ticket, id)
                    .addOnSuccessListener(
                            row -> {
                                if (current() && row.exists()) detail(row);
                            });
    }

    private Bundle args() {
        return getArguments() == null ? Bundle.EMPTY : getArguments();
    }

    private boolean current() {
        return !isDestroyed() && !isFinishing() && sessions != null && sessions.current(ticket);
    }

    private final Runnable revalidate =
            () -> {
                if (reader == null || list == null || !current() || busy || isHidden()) return;
                GridLayoutManager manager = (GridLayoutManager) list.getLayoutManager();
                int first = Math.max(0, manager.findFirstVisibleItemPosition() - 2),
                        last = Math.min(rows.size() - 1, manager.findLastVisibleItemPosition() - 1);
                List<String> ids = new ArrayList<>();
                for (int n = first; n <= last && ids.size() < 20; n++) ids.add(rows.get(n).getId());
                if (ids.isEmpty()) return;
                long g = generation;
                reader.clear();
                reader.window(ticket, ids)
                        .addOnCompleteListener(
                                done -> {
                                    if (!current() || g != generation) return;
                                    if (!done.isSuccessful()) return;
                                    Map<String, ListingRow> fresh = new HashMap<>();
                                    for (ListingRow row : done.getResult().getDocuments())
                                        fresh.put(row.getId(), row);
                                    for (int n = rows.size() - 1; n >= 0; n--)
                                        if (ids.contains(rows.get(n).getId())) {
                                            ListingRow value = fresh.get(rows.get(n).getId());
                                            if (value == null) rows.remove(n);
                                            else rows.set(n, value);
                                        }
                                    adapter.notifyDataSetChanged();
                                });
            };

    @Override
    public void onStart() {
        super.onStart();
        BrowsePulse.add(revalidate);
        if (reader != null) revalidate.run();
    }

    @Override
    public void onStop() {
        BrowsePulse.remove(revalidate);
        super.onStop();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) BrowsePulse.remove(revalidate);
        else {
            BrowsePulse.add(revalidate);
            revalidate.run();
        }
    }

    private void load() {
        if (busy || end || !current()) return;
        busy = true;
        long g = ++generation;
        reader.page(ticket, query, cursor)
                .addOnCompleteListener(
                        done -> {
                            if (!current() || g != generation) return;
                            busy = false;
                            message =
                                    done.isSuccessful()
                                            ? R.string.wiring_empty
                                            : R.string.wiring_failed;
                            if (done.isSuccessful()) {
                                List<ListingRow> page = done.getResult().getDocuments();
                                for (ListingRow d : page)
                                    if (rows.stream().noneMatch(x -> x.getId().equals(d.getId())))
                                        rows.add(d);
                                cursor = done.getResult().cursor;
                                end = cursor == null;
                                while (rows.size() > 100) rows.remove(0);
                            }
                            adapter.notifyDataSetChanged();
                        });
    }

    private void detail(ListingRow row) {
        startActivity(
                new android.content.Intent(requireActivity(), ListingDetailActivity.class)
                        .putExtra("listing", row.getId()));
    }

    private void apply(DiscoveryQuery selected) {
        reader.clear();
        generation++;
        busy = false;
        query = selected;
        cursor = null;
        end = false;
        rows.clear();
        window.anchor = null;
        summary();
        adapter.notifyDataSetChanged();
        list.scrollToPosition(0);
        load();
    }

    private void summary() {
        order.setText(query.prefix.isEmpty() ? R.string.flow_newest : R.string.flow_name_order);
        com.google.android.material.chip.ChipGroup chips = filterChips;
        chips.removeAllViews();
        com.google.android.material.chip.Chip availability =
                new com.google.android.material.chip.Chip(requireActivity());
        availability.setText(
                "reserved".equals(query.availability)
                        ? R.string.flow_reserved
                        : R.string.flow_available);
        chips.addView(availability);
        for (String key : query.filters.keySet()) {
            com.google.android.material.chip.Chip chip =
                    new com.google.android.material.chip.Chip(requireActivity());
            chip.setText(
                    key.equals("speciesId") || key.equals("breedId")
                            ? R.string.wiring_species
                            : key.equals("regionId")
                                    ? R.string.wiring_regions
                                    : key.equals("sex") ? R.string.flow_sex : R.string.flow_age);
            chips.addView(chip);
        }
    }

    private void filters() {
        dialog =
                DiscoveryFilterDialog.show(
                        (AppCompatActivity) requireActivity(),
                        sessions,
                        ticket,
                        query,
                        this::apply);
    }

    @Override
    public void onResume() {
        super.onResume();
        savedStates.clear();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        if (query != null) {
            state.putString("prefix", query.prefix);
            state.putString("availability", query.availability);
            state.putString("draft", window.draft);
            Bundle filters = new Bundle();
            for (Map.Entry<String, String> e : query.filters.entrySet())
                filters.putString(e.getKey(), e.getValue());
            state.putBundle("filters", filters);
        }
        super.onSaveInstanceState(state);
    }

    private void save(String id) {
        if (mutation || !current()) return;
        mutation = true;
        FavoriteActions.set(FirebaseFirestore.getInstance(), sessions, ticket, favorites, id, true)
                .addOnCompleteListener(
                        done -> {
                            if (!current()) return;
                            mutation = false;
                            Snackbar.make(
                                            list,
                                            done.isSuccessful()
                                                    ? R.string.wiring_saved
                                                    : R.string.wiring_unknown,
                                            Snackbar.LENGTH_LONG)
                                    .show();
                        });
    }

    @Override
    public void onDestroyView() {
        searchHandler.removeCallbacksAndMessages(null);
        if (window != null && query != null) {
            window.cursor = cursor;
            window.query = query;
            window.end = end;
            if (list != null) window.anchor = list.getLayoutManager().onSaveInstanceState();
        }
        generation++;
        if (dialog != null) dialog.dismiss();
        if (reader != null) reader.close();
        if (favorites != null) favorites.close();
        if (sessions != null) sessions.removeInvalidation(invalidation);
        super.onDestroyView();
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        public int getItemCount() {
            return rows.size() + 1;
        }

        public int getItemViewType(int p) {
            return p == rows.size() ? 1 : 0;
        }

        public Holder onCreateViewHolder(ViewGroup p, int type) {
            return new Holder(
                    type == 1
                            ? SocialViews.button(
                                    requireActivity(),
                                    getString(R.string.wiring_more),
                                    () -> {
                                        end = false;
                                        load();
                                    })
                            : getLayoutInflater().inflate(R.layout.item_pet_discovery, p, false));
        }

        public void onBindViewHolder(Holder h, int p) {
            if (p == rows.size()) {
                ((TextView) h.itemView)
                        .setText(
                                busy
                                        ? R.string.wiring_loading
                                        : rows.isEmpty() && !end && !busy
                                                ? R.string.feed_listing_window_empty
                                                : rows.isEmpty()
                                                        ? message
                                                        : end
                                                                ? R.string.feed_listing_end
                                                                : R.string.wiring_more);
                h.itemView.setEnabled(!busy && (!end || rows.isEmpty()));
                return;
            }
            ListingRow row = rows.get(p);
            ((TextView) h.itemView.findViewById(R.id.pet_name)).setText(row.getString("petName"));
            ((TextView) h.itemView.findViewById(R.id.pet_breed_age))
                    .setText(row.getString("title"));
            ((TextView) h.itemView.findViewById(R.id.pet_region))
                    .setText(row.getString("regionLabel"));
            ((ImageView) h.itemView.findViewById(R.id.pet_image))
                    .setImageResource(
                            "cat".equals(row.getString("bundledIllustrationKey"))
                                    ? R.drawable.profile_avatar_cat
                                    : R.drawable.profile_avatar_dog);
            h.itemView.setOnClickListener(v -> detail(row));
            View save = h.itemView.findViewById(R.id.pet_favorite_btn);
            save.setTag(row.getId());
            Boolean selected = savedStates.get(row.getId());
            save.setSelected(Boolean.TRUE.equals(selected));
            if (save instanceof ImageView)
                ((ImageView) save)
                        .setImageResource(
                                Boolean.TRUE.equals(selected)
                                        ? R.drawable.ic_nav_favorite
                                        : R.drawable.ic_flow_favorite_outline);
            save.setContentDescription(
                    getString(
                            Boolean.TRUE.equals(selected)
                                    ? R.string.wiring_remove_saved
                                    : R.string.wiring_save));
            save.setEnabled(selected != null && !mutation);
            if (selected == null && savedReads.add(row.getId()))
                FirebaseFirestore.getInstance()
                        .document("users/" + ticket.uid + "/favorites/" + row.getId())
                        .get(Source.SERVER)
                        .addOnCompleteListener(
                                done -> {
                                    if (!current()) return;
                                    savedReads.remove(row.getId());
                                    if (done.isSuccessful()) {
                                        savedStates.put(row.getId(), done.getResult().exists());
                                        adapter.notifyDataSetChanged();
                                    }
                                });
            save.setOnClickListener(
                    v -> {
                        Boolean oldSaved = savedStates.get(row.getId());
                        if (oldSaved == null || mutation || !current()) return;
                        boolean desired = !oldSaved;
                        mutation = true;
                        adapter.notifyDataSetChanged();
                        FavoriteActions.set(
                                        FirebaseFirestore.getInstance(),
                                        sessions,
                                        ticket,
                                        favorites,
                                        row.getId(),
                                        desired)
                                .addOnCompleteListener(
                                        done -> {
                                            if (!current()) return;
                                            mutation = false;
                                            if (done.isSuccessful())
                                                savedStates.put(row.getId(), desired);
                                            adapter.notifyDataSetChanged();
                                            Snackbar.make(
                                                            list,
                                                            done.isSuccessful()
                                                                    ? R.string.wiring_saved
                                                                    : R.string.wiring_unknown,
                                                            Snackbar.LENGTH_LONG)
                                                    .show();
                                        });
                    });
        }
    }

    private final class Header extends RecyclerView.Adapter<Holder> {
        public int getItemCount() {
            return 1;
        }

        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            return new Holder(searchHeader);
        }

        public void onBindViewHolder(Holder holder, int position) {}
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
