package com.gmail.manhle240406.project.community;

import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.lifecycle.ViewModelProvider;

import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.wiring.MainPageFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.*;

import java.util.*;

public final class DirectConversationFragment extends MainPageFragment {
    private AuthorLookup authors;
    private String authorUid, peerName;
    private DirectChatModel model;
    private LinearLayout messages;
    private ScrollView scroll;
    private TextView status;
    private TextInputEditText input;
    private MaterialButton send, older, block;
    private boolean rendering;
    private Object rendered;

    public static DirectConversationFragment create(String id) {
        DirectConversationFragment f = new DirectConversationFragment();
        Bundle b = new Bundle();
        b.putString("id", id);
        f.setArguments(b);
        return f;
    }

    @Override
    protected void createPage(Bundle saved) {
        model = new ViewModelProvider(this).get(DirectChatModel.class);
        authors =
                new AuthorLookup(
                        ((com.gmail.manhle240406.project.AniloveApplication)
                                        requireActivity().getApplication())
                                .foundation()
                                .sessions);
        LinearLayout root = SocialViews.column(requireContext());
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundResource(R.color.anilove_bg);
        setContentView(root);
        SocialViews.privateView(root);
        SocialSurface.header(requireContext(), root, R.string.oc3_direct_messages, this::finish);
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe =
                new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(requireContext());
        swipe.setId(R.id.oc3_chat_refresh);
        root.addView(swipe, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(requireContext());
        swipe.addView(scroll);
        messages = SocialViews.column(requireContext());
        scroll.addView(messages);
        swipe.setOnRefreshListener(
                () -> {
                    model.load(false);
                    swipe.setRefreshing(false);
                });
        older =
                SocialSurface.quiet(
                        requireContext(),
                        getString(R.string.social_more_comments),
                        () -> model.load(true));
        root.addView(older);
        status = SocialSurface.muted(requireContext(), "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        root.addView(status);
        LinearLayout compose = new LinearLayout(requireContext());
        compose.setPadding(16, 0, 16, 0);
        compose.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(compose);
        TextInputLayout box =
                new TextInputLayout(
                        requireContext(),
                        null,
                        com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(R.string.feedback_chat_message);
        input = new TextInputEditText(box.getContext());
        input.setId(R.id.oc3_chat_input);
        input.setMaxLines(4);
        input.setMinHeight(SocialViews.dp(requireContext(), 48));
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4000)});
        box.addView(input);
        compose.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
        send =
                SocialSurface.quiet(
                        requireContext(), getString(R.string.feedback_chat_send), model::send);
        send.setId(R.id.oc3_chat_send);
        compose.addView(send);
        block =
                SocialSurface.quiet(
                        requireContext(),
                        getString(R.string.oc3_block),
                        () ->
                                new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                                requireContext())
                                        .setMessage(
                                                model.blockedByMe
                                                        ? R.string.oc3_unblock
                                                        : R.string.oc3_block)
                                        .setPositiveButton(
                                                android.R.string.ok, (d, w) -> model.block())
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show());
        root.addView(block);
        input.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        if (!rendering) model.body = s.toString();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        model.changes.observe(getViewLifecycleOwner(), x -> render());
        model.open(requireArguments().getString("id"));
        render();
    }

    private void render() {
        if (model.peerUid != null && !model.peerUid.equals(authorUid)) {
            authorUid = model.peerUid;
            String requested = authorUid;
            authors.get(requested)
                    .addOnSuccessListener(
                            p -> {
                                if (isAdded()
                                        && getView() != null
                                        && requested.equals(authorUid)
                                        && p != null) {
                                    peerName = String.valueOf(p.get("displayName"));
                                    render();
                                }
                            });
        }
        if (model.lost) {
            finish();
            return;
        }
        rendering = true;
        if (!input.getText().toString().equals(model.body)) input.setText(model.body);
        rendering = false;
        input.setEnabled(!model.sending && !model.pending() && !model.blocked);
        send.setText(model.pending() ? R.string.oc3_chat_retry : R.string.feedback_chat_send);
        send.setEnabled(!model.sending && !model.blocked);
        block.setEnabled(!model.sending);
        block.setText(model.blockedByMe ? R.string.oc3_unblock : R.string.oc3_block);
        older.setVisibility(model.before == null ? View.GONE : View.VISIBLE);
        older.setEnabled(!model.busy);
        int statusCopy =
                !model.error.isEmpty()
                        ? R.string.oc3_chat_failed
                        : model.blocked
                                ? R.string.oc3_blocked
                                : !model.loaded
                                        ? R.string.wiring_loading
                                        : model.messages.isEmpty() ? R.string.oc3_no_messages : 0;
        status.setText(statusCopy == 0 ? "" : getString(statusCopy));
        status.setVisibility(statusCopy == 0 ? View.GONE : View.VISIBLE);
        Object key = Arrays.asList(new ArrayList<>(model.messages), model.uid(), peerName);
        if (key.equals(rendered)) return;
        boolean follow = rendered == null || scroll != null && !scroll.canScrollVertically(1);
        rendered = key;
        messages.removeAllViews();
        for (Map<String, Object> row : model.messages) {
            LinearLayout bubble = SocialViews.column(requireContext());
            bubble.setBackgroundResource(R.drawable.anilove_card_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = SocialViews.dp(requireContext(), 12);
            messages.addView(bubble, lp);
            bubble.addView(
                    SocialSurface.muted(
                            requireContext(),
                            model.uid().equals(row.get("senderUid"))
                                    ? getString(R.string.oc3_you)
                                    : peerName == null
                                            ? getString(R.string.social_member)
                                            : peerName));
            bubble.addView(
                    SocialViews.text(requireContext(), String.valueOf(row.get("body")), false));
        }
        if (follow && scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onResume() {
        super.onResume();
        model.startRealtime();
        model.load(false);
    }

    @Override
    public void onPause() {
        model.stopRealtime();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        model.stopRealtime();
        rendered = null;
        if (authors != null) authors.close();
        authorUid = null;
        peerName = null;
        super.onDestroyView();
    }
}
