package com.gmail.manhle240406.project.adoption;

import android.view.*;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Explicit filter draft; dismissing cannot mutate the applied query. Catalog IDs remain internal.
 */
final class DiscoveryFilterDialog {
    private final AppCompatActivity activity;
    private final CatalogRepository catalogs;
    private final SpeciesCatalogStore speciesCatalog;
    private final SessionGateway sessions;
    private final SessionEpoch.Ticket ticket;
    private final Map<String, Spinner> controls = new LinkedHashMap<>();
    private final Map<String, List<String>> ids = new LinkedHashMap<>();
    private final Map<String, String> draft;
    private androidx.appcompat.app.AlertDialog dialog;
    private long breedGeneration;

    private DiscoveryFilterDialog(
            AppCompatActivity a, SessionGateway s, SessionEpoch.Ticket t, DiscoveryQuery q) {
        activity = a;
        sessions = s;
        ticket = t;
        catalogs = new CatalogRepository(FirebaseFirestore.getInstance(), s);
        speciesCatalog = ((AniloveApplication) a.getApplication()).speciesCatalog();
        draft = new TreeMap<>(q.filters);
    }

    static androidx.appcompat.app.AlertDialog show(
            AppCompatActivity a,
            SessionGateway s,
            SessionEpoch.Ticket t,
            DiscoveryQuery q,
            Consumer<DiscoveryQuery> apply) {
        return new DiscoveryFilterDialog(a, s, t, q).open(q, apply);
    }

    private androidx.appcompat.app.AlertDialog open(
            DiscoveryQuery query, Consumer<DiscoveryQuery> apply) {
        LinearLayout content = SocialViews.column(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        Spinner availability =
                add(
                        content,
                        R.string.flow_available,
                        new String[] {
                            activity.getString(R.string.flow_available),
                            activity.getString(R.string.flow_reserved)
                        });
        availability.setSelection("reserved".equals(query.availability) ? 1 : 0);
        for (String key : Arrays.asList("speciesId", "breedId", "regionId")) {
            controls.put(
                    key,
                    add(
                            content,
                            key.equals("speciesId")
                                    ? R.string.wiring_species
                                    : key.equals("breedId")
                                            ? R.string.flow_breed
                                            : R.string.wiring_regions,
                            new String[] {activity.getString(R.string.flow_any)}));
            ids.put(key, new ArrayList<>(Collections.singletonList("")));
        }
        Spinner sex =
                add(
                        content,
                        R.string.flow_sex,
                        activity.getResources().getStringArray(R.array.flow_sex_labels));
        String[] sexes = {"", "unknown", "male", "female"};
        sex.setSelection(Math.max(0, Arrays.asList(sexes).indexOf(draft.getOrDefault("sex", ""))));
        Spinner age =
                add(
                        content,
                        R.string.flow_age,
                        activity.getResources().getStringArray(R.array.flow_age_labels));
        String[] ages = {"", "unknown", "baby", "young", "adult", "senior"};
        age.setSelection(
                Math.max(0, Arrays.asList(ages).indexOf(draft.getOrDefault("ageBand", ""))));
        TextView issue = SocialViews.text(activity, "", false);
        issue.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(issue);
        dialog =
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.flow_filters)
                        .setView(scroll)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.flow_apply_filters, null)
                        .create();
        dialog.setOnShowListener(
                ignored -> {
                    dialog.getButton(-1)
                            .setOnClickListener(
                                    v -> {
                                        if (!sessions.current(ticket)) return;
                                        Map<String, String> selected = new TreeMap<>();
                                        for (String key : controls.keySet()) {
                                            List<String> values = ids.get(key);
                                            int p = controls.get(key).getSelectedItemPosition();
                                            String value =
                                                    p >= 0 && p < values.size()
                                                            ? values.get(p)
                                                            : "";
                                            // Do not silently clear a previous applied selection
                                            // while its catalog is unresolved.
                                            if (!controls.get(key).isEnabled()
                                                    && draft.containsKey(key)) {
                                                issue.setText(R.string.wiring_failed);
                                                return;
                                            }
                                            if (!value.isEmpty()) selected.put(key, value);
                                        }
                                        if (sex.getSelectedItemPosition() > 0)
                                            selected.put(
                                                    "sex", sexes[sex.getSelectedItemPosition()]);
                                        if (age.getSelectedItemPosition() > 0)
                                            selected.put(
                                                    "ageBand", ages[age.getSelectedItemPosition()]);
                                        try {
                                            apply.accept(
                                                    new DiscoveryQuery(
                                                            query.prefix,
                                                            availability.getSelectedItemPosition()
                                                                            == 1
                                                                    ? "reserved"
                                                                    : "available",
                                                            selected));
                                            dialog.dismiss();
                                        } catch (IllegalArgumentException e) {
                                            issue.setText(R.string.wiring_search_invalid);
                                        }
                                    });
                });
        controls.get("speciesId")
                .setOnItemSelectedListener(
                        new AdapterView.OnItemSelectedListener() {
                            String last = draft.getOrDefault("speciesId", "");

                            public void onNothingSelected(AdapterView<?> parent) {}

                            public void onItemSelected(
                                    AdapterView<?> p, View v, int position, long id) {
                                List<String> values = ids.get("speciesId");
                                if (!controls.get("speciesId").isEnabled()
                                        || position >= values.size()) return;
                                String selected = values.get(position);
                                if (!selected.equals(last)) {
                                    breedGeneration++;
                                    draft.remove("breedId");
                                    ids.put(
                                            "breedId",
                                            new ArrayList<>(Collections.singletonList("")));
                                    controls.get("breedId")
                                            .setAdapter(
                                                    new ArrayAdapter<>(
                                                            activity,
                                                            android.R.layout
                                                                    .simple_spinner_dropdown_item,
                                                            new String[] {
                                                                activity.getString(
                                                                        R.string.flow_any)
                                                            }));
                                    last = selected;
                                    controls.get("breedId").setEnabled(true);
                                    if (!selected.isEmpty()) load("breedId", selected, issue);
                                }
                            }
                        });
        dialog.show();
        load("speciesId", null, issue);
        load("regionId", null, issue);
        if (draft.containsKey("speciesId")) load("breedId", draft.get("speciesId"), issue);
        return dialog;
    }

    private void load(String key, String parent, TextView issue) {
        if ("speciesId".equals(key)) {
            controls.get(key).setEnabled(false);
            applySpecies(speciesCatalog.snapshot(), issue);
            speciesCatalog.refresh(
                    false,
                    rows -> {
                        if (activity.isDestroyed()
                                || dialog == null
                                || !dialog.isShowing()
                                || !sessions.current(ticket)) return;
                        applySpecies(rows, issue);
                    });
            return;
        }
        long version = breedGeneration;
        controls.get(key).setEnabled(false);
        catalogs.page(
                        ticket,
                        key.equals("speciesId")
                                ? "species"
                                : key.equals("breedId") ? "breed" : "region",
                        parent,
                        null)
                .addOnCompleteListener(
                        done -> {
                            if (activity.isDestroyed()
                                    || dialog == null
                                    || !dialog.isShowing()
                                    || !sessions.current(ticket)
                                    || key.equals("breedId") && version != breedGeneration) return;
                            if (!done.isSuccessful()) {
                                issue.setText(R.string.wiring_failed);
                                return;
                            }
                            List<String> values = new ArrayList<>(Collections.singletonList("")),
                                    labels =
                                            new ArrayList<>(
                                                    Collections.singletonList(
                                                            activity.getString(R.string.flow_any)));
                            String lang =
                                    "en"
                                                    .equals(
                                                            activity.getResources()
                                                                    .getConfiguration()
                                                                    .getLocales()
                                                                    .get(0)
                                                                    .getLanguage())
                                            ? "en"
                                            : "vi";
                            for (DocumentSnapshot row : done.getResult()) {
                                values.add(row.getId());
                                Object raw = row.get("labels");
                                labels.add(
                                        raw instanceof Map
                                                ? String.valueOf(((Map<?, ?>) raw).get(lang))
                                                : activity.getString(R.string.flow_unknown));
                            }
                            ids.put(key, values);
                            controls.get(key)
                                    .setAdapter(
                                            new ArrayAdapter<>(
                                                    activity,
                                                    android.R.layout.simple_spinner_dropdown_item,
                                                    labels));
                            int selected = values.indexOf(draft.getOrDefault(key, ""));
                            controls.get(key).setSelection(Math.max(0, selected));
                            controls.get(key).setEnabled(selected >= 0 || !draft.containsKey(key));
                            if (selected < 0 && draft.containsKey(key))
                                issue.setText(R.string.wiring_unavailable_item);
                        });
    }

    private void applySpecies(List<SpeciesCatalogStore.Entry> rows, TextView issue) {
        List<String> values = new ArrayList<>(Collections.singletonList("")),
                labels =
                        new ArrayList<>(
                                Collections.singletonList(activity.getString(R.string.flow_any)));
        for (SpeciesCatalogStore.Entry row : rows) {
            values.add(row.id);
            labels.add(row.label(activity));
        }
        ids.put("speciesId", values);
        Spinner control = controls.get("speciesId");
        control.setAdapter(
                new ArrayAdapter<>(
                        activity, android.R.layout.simple_spinner_dropdown_item, labels));
        int selected = values.indexOf(draft.getOrDefault("speciesId", ""));
        control.setSelection(Math.max(0, selected));
        control.setEnabled(selected >= 0 || !draft.containsKey("speciesId"));
        if (selected < 0 && draft.containsKey("speciesId"))
            issue.setText(R.string.wiring_unavailable_item);
    }

    private Spinner add(LinearLayout col, int label, String[] labels) {
        col.addView(SocialViews.text(activity, activity.getString(label), false));
        Spinner v = new Spinner(activity);
        v.setContentDescription(activity.getString(label));
        v.setMinimumHeight(SocialViews.dp(activity, 48));
        v.setAdapter(
                new ArrayAdapter<>(
                        activity, android.R.layout.simple_spinner_dropdown_item, labels));
        col.addView(v, new LinearLayout.LayoutParams(-1, -2));
        return v;
    }
}
