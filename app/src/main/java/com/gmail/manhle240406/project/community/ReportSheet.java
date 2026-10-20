package com.gmail.manhle240406.project.community;

import static com.gmail.manhle240406.project.community.SocialViews.*;

import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.gmail.manhle240406.project.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import java.util.*;

/** A contextual private intake; parent feed activity/anchor stays untouched. */
public final class ReportSheet extends BottomSheetDialogFragment {
  public static ReportSheet target(
      String kind, String target, String parent, long version, String reportId) {
    ReportSheet s = new ReportSheet();
    Bundle b = new Bundle();
    b.putString("kind", kind);
    b.putString("target", target);
    b.putString("parent", parent);
    b.putLong("version", version);
    b.putString("report", reportId);
    s.setArguments(b);
    return s;
  }

  private boolean intakeReady;
  private String expectedOperation;
  private DailyViewModel model;
  private TextInputEditText details;
  private TextView status;
  private MaterialButton send;
  private boolean actuallyClosing;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
    model = new ViewModelProvider(requireActivity()).get(DailyViewModel.class);
    expectedOperation =
        state == null
            ? (model.pendingReport == null ? null : model.pendingReport.operationId)
            : state.getString("expected_operation");
    LinearLayout outer = column(requireContext());
    ScrollView scroll = new ScrollView(requireContext());
    outer.addView(scroll);
    LinearLayout col = column(requireContext());
    scroll.addView(col);
    col.addView(text(requireContext(), getString(R.string.report_title), true));
    col.addView(text(requireContext(), getString(R.string.report_private), false));
    TextView reason = text(requireContext(), getString(R.string.report_reason), false);
    col.addView(reason);
    TextView welfare = text(requireContext(), getString(R.string.report_welfare_help), false);
    welfare.setVisibility(View.GONE);
    col.addView(welfare);
    Spinner category = new Spinner(requireContext());
    category.setId(View.generateViewId());
    reason.setLabelFor(category.getId());
    category.setMinimumHeight(dp(requireContext(), 48));
    category.setAdapter(
        new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            getResources().getStringArray(R.array.report_reasons)));
    category.setSelection(model.category);
    if (requireArguments().getString("report") != null && model.report != null) {
      category.setSelection(ReportCommand.CATEGORIES.indexOf(model.report.get("category")));
      category.setEnabled(false);
    }
    col.addView(category);
    category.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> p, View v, int position, long row) {
            model.category = position;
            welfare.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
          }

          public void onNothingSelected(AdapterView<?> p) {}
        });
    details = field(requireContext(), col, getString(R.string.report_details), 4);
    details.setText(model.reportText);
    details.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void afterTextChanged(android.text.Editable e) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            model.reportText = s.toString();
          }
        });
    status = text(requireContext(), "", false);
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    col.addView(status);
    send = primaryButton(requireContext(), getString(R.string.report_submit), this::submit);
    col.addView(send);
    col.addView(button(requireContext(), getString(R.string.social_retry), model::retry));
    col.addView(button(requireContext(), getString(R.string.social_reconcile), model::reconcile));
    col.addView(button(requireContext(), getString(R.string.community_cancel), this::cancel));
    insets(outer);
    privateView(outer);
    return outer;
  }

  @Override
  public void onViewCreated(View view, Bundle b) {
    super.onViewCreated(view, b);
    model.changes.observe(
        getViewLifecycleOwner(),
        x -> {
          if (model.lost) {
            actuallyClosing = true;
            dismissAllowingStateLoss();
            return;
          }
          send.setEnabled(
              intakeReady
                  && !model.busy
                  && model.pending == null
                  && model.pendingReport == null
                  && !model.restored);
          details.setEnabled(!model.busy && model.pendingReport == null);
          status.setText(
              !intakeReady && model.pendingReport == null && !model.busy && !model.restored
                  ? getString(R.string.flow_report_paused)
                  : model.notice.isEmpty() ? "" : DailyCopy.notice(requireContext(), model.notice));
          SocialSurface.hideEmpty(status);
          if (expectedOperation != null && expectedOperation.equals(model.lastCommittedReport)) {
            model.committed = false;
            model.reportText = "";
            actuallyClosing = true;
            dismiss();
          }
        });
    model.read(
        "system/reportPolicy",
        policy -> {
          intakeReady =
              policy != null
                  && Long.valueOf(1).equals(policy.get("schemaVersion"))
                  && Boolean.TRUE.equals(policy.get("intakeEnabled"));
          if (send != null) {
            send.setEnabled(
                intakeReady && !model.busy && model.pendingReport == null && !model.restored);
            if (!intakeReady && model.pendingReport == null && !model.busy && !model.restored)
              status.setText(R.string.flow_report_paused);
          }
        });
    if (model.restored) model.reconcile();
  }

  private void submit() {
    Bundle b = requireArguments();
    String existing = b.getString("report");
    Map<String, Object> prior = model.report;
    String category =
        existing == null
            ? ReportCommand.CATEGORIES.get(model.category)
            : (String) prior.get("category");
    try {
      expectedOperation = UUID.randomUUID().toString();
      model.sendReport(
          new ReportCommand(
              expectedOperation,
              existing == null ? UUID.randomUUID().toString() : existing,
              existing == null ? "submit" : "append",
              b.getString("kind"),
              b.getString("target"),
              b.getString("parent"),
              b.getLong("version"),
              existing == null ? 0 : SocialRepository.number(prior, "lastReporterVersion"),
              category,
              model.reportText.trim(),
              null));
    } catch (IllegalArgumentException invalid) {
      details.setError(getString(R.string.report_invalid));
      details.requestFocus();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    state.putString("expected_operation", expectedOperation);
  }

  private void cancel() {
    if (model.reportText.isEmpty() && !model.busy) {
      actuallyClosing = true;
      dismiss();
      return;
    }
    new MaterialAlertDialogBuilder(requireContext())
        .setMessage(
            model.busy || model.pendingReport != null
                ? R.string.report_cancel_pending
                : R.string.report_discard)
        .setPositiveButton(
            R.string.community_discard,
            (d, w) -> {
              model.reportText = "";
              actuallyClosing = true;
              dismiss();
            })
        .setNegativeButton(R.string.community_cancel, null)
        .show();
  }

  @Override
  public void onStart() {
    super.onStart();
    setCancelable(false);
    if (getDialog() != null) {
      getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      android.view.Window window = getDialog().getWindow();
      boolean dark =
          (getResources().getConfiguration().uiMode
                  & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
              == android.content.res.Configuration.UI_MODE_NIGHT_YES;
      androidx.core.view.WindowCompat.getInsetsController(window, window.getDecorView())
          .setAppearanceLightNavigationBars(!dark);
      if (getDialog() instanceof androidx.activity.ComponentDialog)
        ((androidx.activity.ComponentDialog) getDialog())
            .getOnBackPressedDispatcher()
            .addCallback(
                getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                  public void handleOnBackPressed() {
                    cancel();
                  }
                });
      if (getDialog() instanceof com.google.android.material.bottomsheet.BottomSheetDialog) {
        com.google.android.material.bottomsheet.BottomSheetDialog d =
            (com.google.android.material.bottomsheet.BottomSheetDialog) getDialog();
        d.getBehavior()
            .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setDraggable(false);
      }
    }
  }
}
