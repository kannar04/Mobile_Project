package com.gmail.manhle240406.project.pet;

import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.*;

import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.activity.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.community.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;

import java.util.*;

/** Registration and owner status cards. Offering and participant review have separate routes. */
public final class PetActivity extends AppCompatActivity {
    private String lastNotice = "";
    private TextView uploadHelp;
    private String photoKey;
    private LinearLayout photoContainer;
    private com.gmail.manhle240406.project.media.PostPhotoEditor photos;
    private PetRegistrationModel model;
    private SpeciesCatalogStore speciesCatalog;
    private LinearLayout body;
    private TextView status;
    private boolean showingForm, adoptingTab, loadingAdoptions;
    private androidx.appcompat.app.AlertDialog dialog;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        speciesCatalog = ((AniloveApplication) getApplication()).speciesCatalog();
        model = new ViewModelProvider(this).get(PetRegistrationModel.class);
        if (saved == null && getIntent().getBooleanExtra("startAdd", false) && !model.editing)
            model.start();
        photoContainer = SocialViews.column(this);
        photos =
                new com.gmail.manhle240406.project.media.PostPhotoEditor(
                        this,
                        photoContainer,
                        () -> {
                            if (model.editing && photos != null && photos.model.ready())
                                model.change("photoIds", photos.model.ids());
                        },
                        6,
                        "pet_photo");
        photos.model.changes.observe(this, x -> updateState());
        model.changes.observe(this, x -> render());
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            public void handleOnBackPressed() {
                                leave();
                            }
                        });
    }

    private void render() {
        if (model.lost) {
            finish();
            return;
        }
        if (showingForm && model.editing) {
            updateState();
            return;
        }
        showingForm = model.editing;
        LinearLayout root = SocialSurface.page(this);
        SocialViews.privateView(root);
        SocialSurface.header(this, root, R.string.pet_title, this::leave);
        body = SocialSurface.content(this, root);
        status = SocialSurface.muted(this, "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(status);
        if (model.editing) {
            if (photoContainer.getParent() instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) photoContainer.getParent()).removeView(photoContainer);
            String key = String.valueOf(model.form.get("observedAt"));
            if (photoKey != null && !photoKey.equals(key)) photos.model.reset();
            photoKey = key;
            photos.model.restore(model.form.get("photoIds"));
            body.addView(photoContainer);
            form();
        } else {
            com.google.android.material.tabs.TabLayout tabs =
                    new com.google.android.material.tabs.TabLayout(this);
            tabs.addTab(tabs.newTab().setText(R.string.pet_tab_owned), !adoptingTab);
            tabs.addTab(tabs.newTab().setText(R.string.pet_tab_adopting), adoptingTab);
            tabs.addOnTabSelectedListener(
                    new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                        public void onTabSelected(
                                com.google.android.material.tabs.TabLayout.Tab tab) {
                            boolean next = tab.getPosition() == 1;
                            if (next != adoptingTab) {
                                adoptingTab = next;
                                render();
                            }
                        }

                        public void onTabUnselected(
                                com.google.android.material.tabs.TabLayout.Tab tab) {}

                        public void onTabReselected(
                                com.google.android.material.tabs.TabLayout.Tab tab) {}
                    });
            body.addView(tabs);
            if (adoptingTab) {
                loadAdopting();
                updateState();
                return;
            }
            if (rows(model.workspace.get("pets")).isEmpty())
                body.addView(SocialSurface.muted(this, getString(R.string.feedback_pet_empty)));
            for (Map<String, Object> pet : rows(model.workspace.get("pets"))) {
                Map<String, Object> identity = object(pet.get("identity")),
                        claim = object(pet.get("claim"));
                LinearLayout card = SocialViews.column(this);
                card.setBackgroundResource(R.drawable.anilove_card_bg);
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView photo = new ImageView(this);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                String species = String.valueOf(identity.get("species"));
                photo.setImageResource(
                        "dog".equals(species)
                                ? R.drawable.profile_avatar_dog
                                : "cat".equals(species)
                                        ? R.drawable.profile_avatar_cat
                                        : R.drawable.ic_pet_paw_vector);
                photo.setContentDescription(null);
                java.util.List<String> photoIds =
                        com.gmail.manhle240406.project.media.MediaGrid.ids(
                                pet.get("photoAssetIds"));
                if (!photoIds.isEmpty())
                    com.gmail.manhle240406.project.media.MediaGrid.load(
                            photo, photoIds.get(0), "thumbnail");
                row.addView(
                        photo,
                        new LinearLayout.LayoutParams(
                                SocialViews.dp(this, 76), SocialViews.dp(this, 76)));
                LinearLayout info = SocialViews.column(this);
                info.addView(
                        SocialViews.text(
                                this,
                                String.valueOf(identity.getOrDefault("displayName", "")),
                                true));
                String state = String.valueOf(claim.get("reviewState"));
                info.addView(
                        SocialSurface.muted(
                                this,
                                getString(
                                        "self_declared_for_demo".equals(state)
                                                ? R.string.permissions_self_declared
                                                : "accepted_for_demo".equals(state)
                                                        ? R.string.feedback_pet_approved
                                                        : "submitted".equals(state)
                                                                ? R.string.feedback_pet_pending
                                                                : "rejected".equals(state)
                                                                        ? R.string
                                                                                .feedback_pet_rejected
                                                                        : R.string
                                                                                .feedback_pet_draft)));
                row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(row);
                card.addView(
                        SocialSurface.quiet(
                                this,
                                getString(R.string.feedback_pet_details),
                                () -> details(pet)));
                body.addView(card, new LinearLayout.LayoutParams(-1, -2));
                SocialSurface.gap(body, 12);
            }
            if (model.workspace.get("nextPets") != null)
                body.addView(
                        SocialSurface.quiet(
                                this,
                                getString(R.string.ad_next),
                                () ->
                                        com.gmail.manhle240406.project.adoption.OwnedPets.read(
                                                        ((AniloveApplication) getApplication())
                                                                .foundation()
                                                                .sessions,
                                                        20,
                                                        String.valueOf(
                                                                model.workspace.get("nextPets")))
                                                .addOnSuccessListener(
                                                        r -> {
                                                            model.workspace = r;
                                                            model.changed();
                                                        })));
            if (model.workspace.get("reviewer") instanceof List
                    && ((List<?>) model.workspace.get("reviewer")).contains("pet_offer_review"))
                body.addView(
                        SocialSurface.quiet(
                                this,
                                getString(R.string.feedback_pet_review),
                                () ->
                                        startActivity(
                                                new Intent(this, AdoptionFlowActivity.class)
                                                        .putExtra("mode", "review"))));
            LinearLayout actionBar = SocialViews.column(this);
            actionBar.setPadding(
                    SocialViews.dp(this, 20),
                    SocialViews.dp(this, 10),
                    SocialViews.dp(this, 20),
                    SocialViews.dp(this, 12));
            actionBar.setBackgroundColor(
                    com.google.android.material.color.MaterialColors.getColor(
                            actionBar, com.google.android.material.R.attr.colorSurface));
            actionBar.setElevation(SocialViews.dp(this, 4));
            com.google.android.material.button.MaterialButton add =
                    SocialViews.primaryButton(
                            this,
                            getString(model.restored ? R.string.ad_resume : R.string.ad_add_pet),
                            model::start);
            add.setTag("pet_add_action");
            actionBar.addView(add);
            root.addView(actionBar, new LinearLayout.LayoutParams(-1, -2));
        }
        updateState();
    }

    private void loadAdopting() {
        if (loadingAdoptions) return;
        loadingAdoptions = true;
        TextView loading = SocialSurface.muted(this, getString(R.string.wiring_loading));
        body.addView(loading);
        AdoptionApi.call("flowRead", map("kind", "index", "role", "adopter", "after", null))
                .addOnCompleteListener(
                        done -> {
                            loadingAdoptions = false;
                            if (isDestroyed() || isFinishing() || !adoptingTab) return;
                            body.removeView(loading);
                            if (!done.isSuccessful()) {
                                body.addView(
                                        SocialSurface.muted(
                                                this, getString(R.string.lc_error_network)));
                                body.addView(
                                        SocialSurface.quiet(
                                                this,
                                                getString(R.string.wiring_retry),
                                                this::loadAdopting));
                                return;
                            }
                            int count = 0;
                            for (Map<String, Object> row : rows(done.getResult().get("rows"))) {
                                String state = String.valueOf(row.get("status"));
                                if (Arrays.asList(
                                                "REJECTED",
                                                "WITHDRAWN",
                                                "CANCELLED",
                                                "CANCELLED_BEFORE_HANDOVER",
                                                "FINALIZED",
                                                "RETURNED")
                                        .contains(state)) continue;
                                count++;
                                body.addView(
                                        SocialSurface.quiet(
                                                this,
                                                String.valueOf(
                                                                row.getOrDefault(
                                                                        "title",
                                                                        getString(
                                                                                R.string
                                                                                        .lc_title_detail)))
                                                        + "\n"
                                                        + state,
                                                () -> {
                                                    Intent intent =
                                                            new Intent(
                                                                    this, LifecycleActivity.class);
                                                    if (row.get("adoptionId") != null)
                                                        intent.putExtra(
                                                                "adoption",
                                                                String.valueOf(
                                                                        row.get("adoptionId")));
                                                    else
                                                        intent.putExtra(
                                                                "request",
                                                                String.valueOf(
                                                                        row.get("requestId")));
                                                    startActivity(intent);
                                                }));
                            }
                            if (count == 0)
                                body.addView(
                                        SocialSurface.muted(this, getString(R.string.lc_empty)));
                        });
    }

    private void updateState() {
        if (status == null) return;
        if (photos != null) photos.setLocked(model.busy || model.unknown);
        int validation =
                "invalid_name".equals(model.error)
                        ? R.string.oc3_pet_name_error
                        : "invalid_age".equals(model.error)
                                ? R.string.oc3_pet_age_error
                                : "invalid_claim".equals(model.error)
                                        ? R.string.oc3_pet_claim_error
                                        : 0;
        int copy =
                model.busy
                        ? R.string.ad_loading
                        : model.submitted
                                ? (Boolean.TRUE.equals(model.workspace.get("simplified"))
                                        ? R.string.permissions_pet_saved
                                        : R.string.feedback_pet_registered)
                                : !model.error.isEmpty()
                                        ? model.unknown
                                                ? R.string.ad_unknown_outcome
                                                : validation != 0 ? validation : R.string.ad_failed
                                        : model.localSaved ? R.string.feedback_pet_local_draft : 0;
        status.setText(copy == 0 ? "" : getString(copy));
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
        String notice = model.submitted ? "submitted" : model.error;
        if (!notice.isEmpty() && !notice.equals(lastNotice) && !model.busy) {
            lastNotice = notice;
            com.google.android.material.snackbar.Snackbar.make(
                            body,
                            copy == 0 ? R.string.ad_failed : copy,
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .show();
            if (validation != 0) {
                View input =
                        body.findViewWithTag(
                                "invalid_name".equals(model.error)
                                        ? "pet_name"
                                        : "invalid_age".equals(model.error)
                                                ? "pet_ageMonths"
                                                : "pet_explanation");
                if (input instanceof com.google.android.material.textfield.TextInputEditText) {
                    ((com.google.android.material.textfield.TextInputEditText) input)
                            .setError(getString(validation));
                    input.requestFocus();
                }
            }
        }
        if (notice.isEmpty()) lastNotice = "";
        if (showingForm) {
            if (uploadHelp != null) {
                uploadHelp.setText(
                        photos.model.ready() ? "" : getString(R.string.oc3_pet_photo_wait));
                uploadHelp.setVisibility(photos.model.ready() ? View.GONE : View.VISIBLE);
            }
            for (View v : editable) v.setEnabled(!model.busy && !model.unknown);
            if (submit != null) {
                submit.setEnabled(!model.busy && photos.model.ready());
                submit.setText(model.unknown ? R.string.ad_retry_same : R.string.feedback_register);
            }
            if (check != null) check.setVisibility(model.unknown ? View.VISIBLE : View.GONE);
        }
    }

    private final List<View> editable = new ArrayList<>();
    private com.google.android.material.button.MaterialButton submit, check;

    private void form() {
        editable.clear();
        body.addView(SocialSurface.muted(this, getString(R.string.oc3_pet_form_help)));
        field("name", R.string.feedback_pet_name, 80, false);
        speciesChoices();
        field("breed", R.string.feedback_breed, 80, false);
        choices(
                "sex",
                R.string.pet_sex,
                getResources().getStringArray(R.array.pet_sexes),
                new String[] {"unknown", "female", "male"});
        field("ageMonths", R.string.feedback_age, 4, false)
                .setInputType(InputType.TYPE_CLASS_NUMBER);
        choices(
                "basis",
                R.string.ad_claim_basis,
                getResources().getStringArray(R.array.ad_bases),
                new String[] {"claimed_owner", "authorized_caregiver", "other_permission"});
        CheckBox care = new CheckBox(this);
        care.setText(R.string.ad_reported_care);
        care.setMinHeight(SocialViews.dp(this, 48));
        care.setChecked(Boolean.TRUE.equals(model.form.get("reportedCare")));
        care.setOnCheckedChangeListener((b, on) -> model.change("reportedCare", on));
        body.addView(care);
        editable.add(care);
        field("explanation", R.string.ad_claim_explanation, 1000, true);
        body.addView(SocialSurface.muted(this, getString(R.string.ad_claim_notice)));
        uploadHelp = SocialSurface.muted(this, "");
        body.addView(uploadHelp);
        submit =
                SocialViews.primaryButton(
                        this,
                        getString(R.string.feedback_register),
                        () -> {
                            if (photos.model.ready()) {
                                model.change("photoIds", photos.model.ids());
                                model.submit();
                            }
                        });
        submit.setTag("pet_register_submit");
        body.addView(submit);
        check = SocialSurface.quiet(this, getString(R.string.ad_check_result), model::reconcile);
        body.addView(check);
        body.addView(SocialSurface.quiet(this, getString(R.string.feedback_cancel), this::leave));
    }

    private void speciesChoices() {
        TextView text = SocialSurface.muted(this, getString(R.string.ad_species));
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        text.setLabelFor(spinner.getId());
        body.addView(text);
        spinner.setMinimumHeight(SocialViews.dp(this, 48));
        bindSpecies(spinner, speciesCatalog.snapshot());
        body.addView(spinner);
        editable.add(spinner);
        speciesCatalog.refresh(
                false,
                values -> {
                    if (!isFinishing() && showingForm && spinner.getParent() != null)
                        bindSpecies(spinner, values);
                });
    }

    private void bindSpecies(Spinner spinner, List<SpeciesCatalogStore.Entry> entries) {
        String selected = String.valueOf(model.form.get("species"));
        List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
        for (SpeciesCatalogStore.Entry entry : entries) {
            ids.add(entry.id);
            labels.add(entry.label(this));
        }
        if (!ids.contains(selected)) {
            ids.add(selected);
            labels.add(speciesCatalog.label(selected));
        }
        spinner.setOnItemSelectedListener(null);
        spinner.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(Math.max(0, ids.indexOf(selected)));
        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (!Objects.equals(model.form.get("species"), ids.get(pos)))
                            model.change("species", ids.get(pos));
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
    }

    private TextInputEditText field(String key, int label, int max, boolean multi) {
        TextInputLayout box = new TextInputLayout(this);
        box.setHint(label);
        TextInputEditText input = new TextInputEditText(box.getContext());
        input.setTag("pet_" + key);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(max)});
        input.setText(String.valueOf(model.form.getOrDefault(key, "")));
        input.setMinHeight(SocialViews.dp(this, 52));
        input.setSingleLine(!multi);
        if (multi) input.setMinLines(3);
        box.addView(input);
        body.addView(box);
        SocialSurface.gap(body, 12);
        editable.add(input);
        input.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int st, int n, int a) {}

                    public void afterTextChanged(Editable e) {}

                    public void onTextChanged(CharSequence s, int st, int b, int n) {
                        model.change(key, s.toString());
                    }
                });
        return input;
    }

    private void choices(String key, int label, String[] labels, String[] values) {
        TextView text = SocialSurface.muted(this, getString(label));
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        text.setLabelFor(spinner.getId());
        body.addView(text);
        spinner.setMinimumHeight(SocialViews.dp(this, 48));
        spinner.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setSelection(Math.max(0, Arrays.asList(values).indexOf(model.form.get(key))));
        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (!Objects.equals(model.form.get(key), values[pos]))
                            model.change(key, values[pos]);
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
        body.addView(spinner);
        editable.add(spinner);
    }

    private void details(Map<String, Object> pet) {
        Map<String, Object> i = object(pet.get("identity")), claim = object(pet.get("claim"));
        LinearLayout info = SocialViews.column(this);
        com.gmail.manhle240406.project.media.MediaGrid gallery =
                new com.gmail.manhle240406.project.media.MediaGrid(this);
        gallery.bind(pet.get("photoAssetIds"));
        info.addView(gallery);
        info.addView(
                SocialViews.text(
                        this,
                        getString(R.string.ad_species)
                                + ": "
                                + speciesCatalog.label(String.valueOf(i.get("species"))),
                        false));
        info.addView(
                SocialViews.text(
                        this,
                        getString(R.string.feedback_breed)
                                + ": "
                                + String.valueOf(i.getOrDefault("breed", "")),
                        false));
        info.addView(
                SocialViews.text(
                        this,
                        getString(R.string.pet_sex)
                                + ": "
                                + getResources()
                                        .getStringArray(R.array.pet_sexes)[
                                        "female".equals(i.get("sex"))
                                                ? 1
                                                : "male".equals(i.get("sex")) ? 2 : 0],
                        false));
        info.addView(
                SocialViews.text(
                        this,
                        getString(R.string.feedback_age)
                                + ": "
                                + (i.get("ageMonths") == null
                                        ? getString(R.string.ad_unknown)
                                        : Long.toString(number(i.get("ageMonths")))),
                        false));
        info.addView(
                SocialViews.text(
                        this,
                        getString(R.string.ad_claim_explanation)
                                + "\n"
                                + String.valueOf(claim.getOrDefault("explanation", "")),
                        false));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(info);
        dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(String.valueOf(i.getOrDefault("displayName", "")))
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.social_edit, (d, w) -> model.editPet(pet))
                        .setNegativeButton(
                                R.string.permissions_edit_care,
                                (d, w) ->
                                        startActivity(
                                                new Intent(this, AdoptionFlowActivity.class)
                                                        .putExtra("mode", "care")
                                                        .putExtra(
                                                                "petId",
                                                                String.valueOf(pet.get("petId")))))
                        .show();
    }

    private void leave() {
        if (!model.editing) {
            finish();
            return;
        }
        if (model.busy || model.unknown) {
            model.flush();
            model.editing = false;
            showingForm = false;
            render();
            return;
        }
        dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.feedback_discard_pet)
                        .setMessage(R.string.feedback_discard_pet_help)
                        .setPositiveButton(R.string.feedback_discard, (d, w) -> model.discard())
                        .setNegativeButton(R.string.feedback_continue_editing, null)
                        .show();
    }

    @Override
    protected void onStop() {
        model.flush();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
