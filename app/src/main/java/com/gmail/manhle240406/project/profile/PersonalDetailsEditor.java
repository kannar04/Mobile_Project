package com.gmail.manhle240406.project.profile;

import android.app.DatePickerDialog;
import android.view.View;
import android.widget.*;

import androidx.lifecycle.LifecycleOwner;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.media.ManagedMediaClient;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.util.*;

/**
 * Optional private details never enter ProfileSchema.publicContent or a public author projection.
 */
final class PersonalDetailsEditor {
    private String birthdate, initialDate, initialGender;
    private Runnable saveAction;
    private Spinner genderField;
    private java.util.function.Consumer<Boolean> completion;
    private long version;
    private boolean loading = true, saving;
    private Map<String, Object> pending;

    PersonalDetailsEditor(
            android.content.Context context, LinearLayout parent, LifecycleOwner owner) {
        SessionGateway sessions =
                ((AniloveApplication) context.getApplicationContext()).foundation().sessions;
        SessionEpoch.Ticket ticket = sessions.capture();
        TextView title =
                SocialViews.text(context, context.getString(R.string.feedback_gender), false);
        parent.addView(title);
        Spinner gender = new Spinner(context);
        gender.setId(View.generateViewId());
        title.setLabelFor(gender.getId());
        gender.setMinimumHeight(SocialViews.dp(context, 48));
        gender.setAdapter(
                new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        context.getResources().getStringArray(R.array.feedback_genders)));
        parent.addView(gender);
        genderField = gender;
        com.google.android.material.textfield.TextInputLayout dateOutline =
                new com.google.android.material.textfield.TextInputLayout(
                        context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        dateOutline.setHint(R.string.feedback_birthdate);
        com.google.android.material.textfield.TextInputEditText date =
                new com.google.android.material.textfield.TextInputEditText(
                        dateOutline.getContext());
        date.setFocusable(false);
        date.setMinHeight(SocialViews.dp(context, 56));
        dateOutline.addView(date);
        parent.addView(dateOutline);
        dateOutline.setEndIconMode(
                com.google.android.material.textfield.TextInputLayout.END_ICON_CUSTOM);
        dateOutline.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar);
        dateOutline.setEndIconContentDescription(R.string.feedback_birthdate);
        dateOutline.setEndIconOnClickListener(v -> date.performClick());
        TextView status = SocialSurface.muted(context, context.getString(R.string.profile_loading));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        parent.addView(status);
        MaterialButton save =
                SocialSurface.quiet(context, context.getString(R.string.profile_save), () -> {});
        save.setVisibility(View.GONE);
        parent.addView(save);
        save.setEnabled(false);
        gender.setEnabled(false);
        date.setEnabled(false);
        Runnable enable =
                () -> {
                    boolean active = !loading && !saving && pending == null;
                    gender.setEnabled(active);
                    date.setEnabled(active);
                    save.setEnabled(!loading && !saving);
                };
        date.setOnClickListener(
                v -> {
                    LocalDate initial =
                            birthdate == null
                                    ? LocalDate.of(2000, 1, 1)
                                    : LocalDate.parse(birthdate);
                    DatePickerDialog picker =
                            new DatePickerDialog(
                                    context,
                                    (view, y, m, d) -> {
                                        birthdate = LocalDate.of(y, m + 1, d).toString();
                                        date.setText(birthdate);
                                    },
                                    initial.getYear(),
                                    initial.getMonthValue() - 1,
                                    initial.getDayOfMonth());
                    picker.getDatePicker().setMaxDate(System.currentTimeMillis());
                    picker.getDatePicker()
                            .setMinDate(java.sql.Date.valueOf("1900-01-01").getTime());
                    picker.setButton(
                            -3,
                            context.getString(R.string.media_remove),
                            (d, w) -> {
                                birthdate = null;
                                date.setText("");
                            });
                    picker.show();
                });
        saveAction =
                () -> {
                    if (pending == null)
                        pending =
                                ManagedMediaClient.map(
                                        "operationId",
                                        UUID.randomUUID().toString(),
                                        "expectedVersion",
                                        version,
                                        "gender",
                                        gender.getSelectedItemPosition() == 0
                                                ? null
                                                : gender.getSelectedItemPosition() == 1
                                                        ? "male"
                                                        : "female",
                                        "birthdate",
                                        birthdate);
                    saving = true;
                    enable.run();
                    ManagedMediaClient.call("personal", pending)
                            .addOnCompleteListener(
                                    t -> {
                                        if (!sessions.current(ticket)
                                                || owner.getLifecycle().getCurrentState()
                                                        == androidx.lifecycle.Lifecycle.State
                                                                .DESTROYED) return;
                                        saving = false;
                                        if (t.isSuccessful()) {
                                            version =
                                                    ((Number) t.getResult().get("version"))
                                                            .longValue();
                                            pending = null;
                                            initialDate = birthdate;
                                            initialGender =
                                                    gender.getSelectedItemPosition() == 0
                                                            ? null
                                                            : gender.getSelectedItemPosition() == 1
                                                                    ? "male"
                                                                    : "female";
                                            status.setText(R.string.feedback_personal_saved);
                                            if (completion != null) completion.accept(true);
                                        } else {
                                            status.setText(R.string.profile_unavailable);
                                            if (completion != null) completion.accept(false);
                                        }
                                        enable.run();
                                    });
                };
        ManagedMediaClient.call("personalRead", Collections.emptyMap())
                .addOnCompleteListener(
                        t -> {
                            if (!sessions.current(ticket)
                                    || owner.getLifecycle().getCurrentState()
                                            == androidx.lifecycle.Lifecycle.State.DESTROYED) return;
                            loading = false;
                            if (t.isSuccessful()) {
                                Map<String, Object> p = t.getResult();
                                version = ((Number) p.get("version")).longValue();
                                birthdate = (String) p.get("birthdate");
                                initialDate = birthdate;
                                initialGender = (String) p.get("gender");
                                gender.setSelection(
                                        "male".equals(p.get("gender"))
                                                ? 1
                                                : "female".equals(p.get("gender")) ? 2 : 0);
                                if (birthdate != null) date.setText(birthdate);
                                status.setText("");
                                enable.run();
                            } else {
                                status.setText(R.string.profile_unavailable);
                                save.setVisibility(View.GONE);
                            }
                        });
    }

    boolean dirty() {
        return !loading
                && (!Objects.equals(initialDate, birthdate)
                        || !Objects.equals(
                                initialGender,
                                genderField.getSelectedItemPosition() == 0
                                        ? null
                                        : genderField.getSelectedItemPosition() == 1
                                                ? "male"
                                                : "female"));
    }

    void save(java.util.function.Consumer<Boolean> done) {
        if (loading || saving) {
            done.accept(false);
            return;
        }
        if (!dirty() && pending == null) {
            done.accept(true);
            return;
        }
        completion = done;
        saveAction.run();
    }
}
