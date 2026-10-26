package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.pet.PetSchema.*;

import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.activity.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.lifecycle.ViewModelProvider;

import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthState;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.*;
import com.google.firebase.Timestamp;

import java.util.*;

public final class CommunityActivity extends AppCompatActivity {
    private CommunityViewModel model;
    private LinearLayout content, editor, results;
    private TextView status, detail;
    private TextInputLayout field;
    private TextInputEditText body;
    private Spinner illustration;
    private final Map<String, MaterialButton> buttons = new HashMap<>();
    private boolean rendering;
    private Object shownConflict;
    private androidx.appcompat.app.AlertDialog dialog;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_community);
        content = findViewById(R.id.community_content);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.community_root),
                (v, i) -> {
                    Insets
                            b =
                                    i.getInsets(
                                            WindowInsetsCompat.Type.systemBars()
                                                    | WindowInsetsCompat.Type.displayCutout()),
                            k = i.getInsets(WindowInsetsCompat.Type.ime());
                    v.setPadding(b.left, b.top, b.right, Math.max(b.bottom, k.bottom));
                    return i;
                });
        model = new ViewModelProvider(this).get(CommunityViewModel.class);
        heading(R.string.community_title);
        label(R.string.community_help);
        label(R.string.community_unavailable);
        status = label(0);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        action("retry", R.string.community_retry, model::retry, false);
        action("reconcile", R.string.community_reconcile, model::reconcile, false);
        action(
                "compare",
                R.string.community_compare,
                () -> {
                    shownConflict = null;
                    if (model.conflict != null) render();
                    else model.compare();
                },
                false);
        action(
                "release",
                R.string.community_release_recovery,
                () -> confirm(R.string.community_release_help, model::releaseRecovery),
                false);
        action("new", R.string.community_new, model::newPost, true);
        action("posts", R.string.community_refresh, () -> model.list("posts", false), false);
        action(
                "recoveryHistory",
                R.string.community_operations,
                () -> model.list("operations", false),
                false);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        action("next", R.string.community_next, () -> model.list(model.kind, true), false);
        detail = label(0);
        LinearLayout outer = content;
        editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        content.addView(editor);
        content = editor;
        field = (TextInputLayout) getLayoutInflater().inflate(R.layout.pet_field, content, false);
        field.setHint(getString(R.string.community_body));
        body = (TextInputEditText) field.getEditText();
        body.setId(View.generateViewId());
        body.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(5);
        body.setGravity(Gravity.TOP);
        content.addView(field);
        TextView label = label(R.string.community_illustration);
        illustration = new Spinner(this);
        illustration.setId(View.generateViewId());
        illustration.setMinimumHeight(dp(48));
        illustration.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_spinner_dropdown_item,
                        getResources().getStringArray(R.array.community_illustrations)));
        label.setLabelFor(illustration.getId());
        content.addView(illustration, new LinearLayout.LayoutParams(-1, -2));
        illustration.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                        capture();
                    }

                    public void onNothingSelected(AdapterView<?> p) {}
                });
        body.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int c, int after) {}

                    public void onTextChanged(CharSequence s, int a, int before, int c) {
                        capture();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        label(R.string.community_illustration_help);
        label(R.string.community_memory_help);
        action("save", R.string.community_save, () -> save(null), true);
        action("preview", R.string.community_preview, this::preview, false);
        action("submit", R.string.community_submit_unavailable, () -> {}, false);
        action("report", R.string.community_report_unavailable, () -> {}, false);
        action("hide", R.string.community_hide, () -> model.command("hide"), false);
        action(
                "archive",
                R.string.community_archive,
                () -> confirm(R.string.community_archive_help, () -> model.command("archive")),
                false);
        action(
                "history",
                R.string.community_history,
                () -> leaveDraft(() -> model.list("revisions", false)),
                false);
        action(
                "operations",
                R.string.community_operations,
                () -> leaveDraft(() -> model.list("operations", false)),
                false);
        label(R.string.community_social_limits);
        content = outer;
        action("back", R.string.auth_back, this::back, false);
        disableState(findViewById(R.id.community_root));
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            public void handleOnBackPressed() {
                                back();
                            }
                        });
        model.changes.observe(this, x -> render());
        ((AniloveApplication) getApplication())
                .auth()
                .state()
                .observe(
                        this,
                        s -> {
                            if (s.uid == null) {
                                if (dialog != null) dialog.dismiss();
                                finish();
                            } else render();
                        });
    }

    private int dp(int x) {
        return Math.round(x * getResources().getDisplayMetrics().density);
    }

    private void disableState(View v) {
        v.setSaveEnabled(false);
        v.setSaveFromParentEnabled(false);
        if (v instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++)
                disableState(((ViewGroup) v).getChildAt(i));
    }

    private TextView label(int id) {
        TextView t = new TextView(this);
        t.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        t.setTextColor(
                MaterialColors.getColor(t, com.google.android.material.R.attr.colorOnSurface));
        if (id != 0) t.setText(id);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(12);
        content.addView(t, p);
        t.setSaveEnabled(false);
        return t;
    }

    private void heading(int id) {
        TextView t = label(id);
        t.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        ViewCompat.setAccessibilityHeading(t, true);
    }

    private MaterialButton action(String key, int text, Runnable run, boolean primary) {
        MaterialButton b =
                (MaterialButton) getLayoutInflater().inflate(R.layout.pet_button, content, false);
        b.setText(text);
        if (!primary) {
            b.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            MaterialColors.getColor(
                                    b,
                                    com.google.android.material.R.attr.colorSecondaryContainer)));
            b.setTextColor(
                    MaterialColors.getColor(
                            b, com.google.android.material.R.attr.colorOnSecondaryContainer));
        }
        b.setOnClickListener(v -> run.run());
        content.addView(b);
        buttons.put(key, b);
        return b;
    }

    private void capture() {
        if (!rendering && model != null && body != null && illustration != null)
            model.changed(
                    body.getText() == null ? "" : body.getText().toString(),
                    Math.max(0, illustration.getSelectedItemPosition()));
    }

    private boolean validate() {
        field.setError(null);
        if (!CommunitySchema.fields(
                CommunitySchema.fields(
                        model.body.trim(),
                        CommunitySchema.ILLUSTRATIONS.get(model.illustration)))) {
            field.setError(getString(R.string.community_invalid));
            body.requestFocus();
            return false;
        }
        return true;
    }

    private Runnable afterSave;
    private String afterSaveOperation;

    private void save(Runnable after) {
        if (!validate()) return;
        afterSave = after;
        model.command(model.contentVersion == 0 ? "create" : "edit");
        afterSaveOperation = model.pending == null ? null : model.pending.operationId;
    }

    private void confirm(int id, Runnable run) {
        dialog =
                new MaterialAlertDialogBuilder(this)
                        .setMessage(id)
                        .setNegativeButton(R.string.community_cancel, null)
                        .setPositiveButton(R.string.community_confirm, (d, w) -> run.run())
                        .show();
    }

    private void preview() {
        if (!validate()) return;
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(24), 0, dp(24), 0);
        TextView text = new TextView(this);
        text.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        text.setText(
                getString(
                        R.string.community_audience_preview,
                        model.body.trim(),
                        getString(R.string.community_illustration_help)));
        view.addView(text);
        if (model.illustration > 0) {
            ImageView image = new ImageView(this);
            image.setImageResource(
                    model.illustration == 2
                            ? R.drawable.profile_avatar_cat
                            : model.illustration == 3
                                    ? R.drawable.profile_avatar_dog
                                    : R.drawable.profile_avatar_default);
            image.setContentDescription(getString(R.string.community_illustration_help));
            view.addView(image, new LinearLayout.LayoutParams(dp(96), dp(96)));
        }
        androidx.core.widget.NestedScrollView scroll =
                new androidx.core.widget.NestedScrollView(this);
        scroll.addView(view);
        disableState(scroll);
        dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.community_preview)
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
    }

    private void leaveDraft(Runnable after) {
        if (!model.dirty) {
            after.run();
            return;
        }
        dialog =
                new MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.community_leave)
                        .setPositiveButton(R.string.community_save, (d, w) -> save(after))
                        .setNegativeButton(
                                R.string.community_discard,
                                (d, w) -> {
                                    model.dirty = false;
                                    after.run();
                                })
                        .setNeutralButton(R.string.community_cancel, null)
                        .show();
    }

    private void back() {
        if (model.busy || model.writePending || model.pending != null || model.restored) {
            finish();
            return;
        }
        if ("revisions".equals(model.kind) || "operations".equals(model.kind)) {
            model.kind = "posts";
            model.page = null;
            model.notifyChange();
            return;
        }
        if (model.editing) leaveDraft(model::returnToList);
        else finish();
    }

    private int message() {
        switch (model.message) {
            case "committed":
                return R.string.community_saved;
            case "unknown":
                return R.string.community_unknown;
            case "conflict":
                return R.string.community_conflict;
            case "network":
                return R.string.community_network;
            case "denied":
                return R.string.community_denied;
            case "invalid":
                return R.string.community_invalid;
            case "schema":
                return R.string.community_schema;
            case "recovery":
                return R.string.community_recovery;
            case "no_receipt":
                return R.string.community_no_receipt;
            default:
                return R.string.community_help;
        }
    }

    private void render() {
        if (body == null) return;
        if (model.lost) {
            if (dialog != null) dialog.dismiss();
            afterSave = null;
            afterSaveOperation = null;
            finish();
            return;
        }
        boolean locked = model.locked(),
                has = model.editing,
                history = "revisions".equals(model.kind) || "operations".equals(model.kind),
                canonical = model.contentVersion > 0,
                archived =
                        model.detail != null
                                && "archived".equals(model.detail.root.get("lifecycle"));
        AuthState auth = ((AniloveApplication) getApplication()).auth().state().getValue();
        boolean ready = auth != null && auth.communityReady();
        for (MaterialButton b : buttons.values()) b.setEnabled(!locked);
        buttons.get("back").setEnabled(true);
        buttons.get("submit").setEnabled(false);
        buttons.get("report").setEnabled(false);
        buttons.get("save").setEnabled(!locked && ready && !archived);
        body.setEnabled(!locked && !archived);
        illustration.setEnabled(!locked && !archived);
        editor.setVisibility(has && !history ? View.VISIBLE : View.GONE);
        results.setVisibility(!has || history ? View.VISIBLE : View.GONE);
        for (String k : new String[] {"new", "posts"})
            buttons.get(k).setVisibility(!has ? View.VISIBLE : View.GONE);
        buttons.get("posts")
                .setEnabled(!model.busy && !model.writePending && model.pending == null);
        buttons.get("recoveryHistory").setVisibility(model.restored ? View.VISIBLE : View.GONE);
        buttons.get("recoveryHistory").setEnabled(!model.busy && !model.writePending);
        buttons.get("release").setVisibility(model.restored ? View.VISIBLE : View.GONE);
        buttons.get("release")
                .setEnabled(
                        !model.busy
                                && !model.writePending
                                && model.postsChecked
                                && model.operationsChecked
                                && "no_receipt".equals(model.message));
        for (String k : new String[] {"hide", "archive", "history", "operations"})
            buttons.get(k).setVisibility(canonical ? View.VISIBLE : View.GONE);
        buttons.get("hide").setEnabled(!locked && !archived);
        buttons.get("archive").setEnabled(!locked && !archived);
        boolean recovery = model.pending != null || model.restored;
        buttons.get("retry").setVisibility(model.pending != null ? View.VISIBLE : View.GONE);
        buttons.get("retry").setEnabled(!model.busy && !model.writePending);
        buttons.get("reconcile").setVisibility(recovery ? View.VISIBLE : View.GONE);
        buttons.get("reconcile").setEnabled(!model.busy && !model.writePending);
        buttons.get("compare").setVisibility(canonical ? View.VISIBLE : View.GONE);
        buttons.get("compare").setEnabled(!model.busy && !model.writePending && !model.restored);
        buttons.get("next")
                .setVisibility(
                        (!has || history) && model.page != null && model.page.cursor != null
                                ? View.VISIBLE
                                : View.GONE);
        status.setText(
                (model.busy || model.writePending) && !"unknown".equals(model.message)
                        ? R.string.community_busy
                        : message());
        detail.setText(
                canonical
                        ? getString(
                                R.string.community_status,
                                getString(
                                        archived
                                                ? R.string.community_archived
                                                : R.string.community_active),
                                model.contentVersion,
                                model.controlVersion,
                                model.postId)
                        : "");
        rendering = true;
        if (!Objects.equals(body.getText() == null ? "" : body.getText().toString(), model.body))
            body.setText(model.body);
        illustration.setSelection(model.illustration);
        rendering = false;
        results.removeAllViews();
        if (model.page != null) {
            if (model.page.rows.isEmpty()) rowText(getString(R.string.community_empty));
            for (Map<String, Object> row : model.page.rows) {
                if ("posts".equals(model.kind)) {
                    MaterialButton b =
                            (MaterialButton)
                                    getLayoutInflater()
                                            .inflate(R.layout.pet_button, results, false);
                    b.setText(
                            getString(
                                    R.string.community_status,
                                    getString(
                                            "archived".equals(row.get("lifecycle"))
                                                    ? R.string.community_archived
                                                    : R.string.community_active),
                                    version(row, "contentVersion"),
                                    version(row, "controlVersion"),
                                    row.get("postId")));
                    b.setEnabled(!locked);
                    b.setOnClickListener(v -> model.load((String) row.get("postId")));
                    results.addView(b);
                    disableState(b);
                } else if ("revisions".equals(model.kind))
                    rowText(
                            getString(
                                    R.string.community_revision_row,
                                    version(row, "contentVersion"),
                                    row.get("body"),
                                    date((Timestamp) row.get("createdAt"))));
                else
                    rowText(
                            getString(
                                    R.string.community_operation_row,
                                    commandLabel((String) row.get("command")),
                                    version(nested(row, "result"), "contentVersion"),
                                    version(nested(row, "result"), "controlVersion"),
                                    date((Timestamp) row.get("committedAt"))));
            }
        }
        if (model.conflict != null
                && shownConflict != model.conflict
                && (dialog == null || !dialog.isShowing())) {
            shownConflict = model.conflict;
            String text =
                    getString(R.string.community_conflict)
                            + "\n\n"
                            + getString(R.string.community_server_label)
                            + "\n"
                            + model.conflict.revision.get("body")
                            + "\n\n"
                            + getString(R.string.community_candidate_label)
                            + "\n"
                            + model.body;
            dialog =
                    new MaterialAlertDialogBuilder(this)
                            .setMessage(text)
                            .setPositiveButton(
                                    R.string.community_keep, (d, w) -> model.resolve(true))
                            .setNegativeButton(
                                    R.string.community_reload, (d, w) -> model.resolve(false))
                            .setNeutralButton(R.string.community_cancel, null)
                            .show();
        }
        if (afterSave != null
                && afterSaveOperation != null
                && afterSaveOperation.equals(model.lastCommittedOperation)
                && !locked
                && "committed".equals(model.message)) {
            Runnable next = afterSave;
            afterSave = null;
            next.run();
        }
    }

    private void rowText(String s) {
        TextView t = new TextView(this);
        t.setTextAppearance(
                com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        t.setText(s);
        t.setPadding(0, dp(12), 0, dp(12));
        results.addView(t);
        disableState(t);
    }

    private String date(Timestamp t) {
        return java.text.DateFormat.getDateTimeInstance().format(t.toDate())
                + " ("
                + t.toDate().toInstant()
                + ")";
    }

    private String commandLabel(String c) {
        return getString(
                "hide".equals(c)
                        ? R.string.community_hide
                        : "archive".equals(c)
                                ? R.string.community_archive
                                : R.string.community_save);
    }

    @Override
    protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
