package com.gmail.manhle240406.project.community;

import android.os.Bundle;
import android.view.*;
import android.widget.*;

import androidx.recyclerview.widget.*;

import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.AdoptionApi;
import com.gmail.manhle240406.project.adoption.AdoptionFlowModel;
import com.gmail.manhle240406.project.adoption.ParticipantCommunicationRepository;
import com.gmail.manhle240406.project.foundation.*;
import com.gmail.manhle240406.project.wiring.MainPageFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.firestore.*;

import java.util.*;

/** Own bounded participant index. Never invents strangers, memberships or message previews. */
public final class ConversationsFragment extends MainPageFragment {
    private DirectInboxAdapter direct;
    private ParticipantCommunicationRepository repository;
    private SessionGateway sessions;
    private SessionEpoch.Ticket ticket;
    private final List<DocumentSnapshot> rows = new ArrayList<>();
    private final List<Map<String, Object>> flowRows = new ArrayList<>();
    private DocumentSnapshot cursor;
    private boolean busy, more = true;
    private long generation;
    private TextView status;
    private MaterialButton retry, newMessage;
    private RecyclerView list;
    private TabLayout tabs;
    private boolean adoptionSelected;
    private long flowLoadGeneration;
    private final Runnable invalidation =
            () -> {
                generation++;
                rows.clear();
                flowRows.clear();
                if (list != null) list.getAdapter().notifyDataSetChanged();
            };

    @Override
    protected void createPage(Bundle saved) {
        adoptionSelected = saved != null && saved.getBoolean("adoption_chat_tab", false);
        LinearLayout root = SocialViews.column(requireContext());
        root.setPadding(0, 0, 0, 0);
        setContentView(root);
        root.setBackgroundResource(R.color.anilove_bg);
        SocialViews.privateView(root);
        com.google.android.material.appbar.MaterialToolbar header =
                SocialSurface.header(requireContext(), root, R.string.nav_chat, this::finish);
        header.setNavigationIcon(null);
        LinearLayout body = SocialViews.column(requireContext());
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        tabs = new TabLayout(requireContext());
        tabs.setId(R.id.chat_type_tabs);
        tabs.setTabMode(TabLayout.MODE_FIXED);
        tabs.addTab(tabs.newTab().setText(R.string.oc3_direct_messages));
        tabs.addTab(tabs.newTab().setText(R.string.oc3_adoption_messages));
        body.addView(tabs);
        newMessage =
                SocialSurface.quiet(
                        requireContext(),
                        getString(R.string.oc3_new_message),
                        () ->
                                DirectChats.people(
                                        (androidx.appcompat.app.AppCompatActivity)
                                                requireActivity()));
        newMessage.setId(R.id.oc3_chat_new);
        newMessage.setIconResource(R.drawable.ic_nav_chat);
        body.addView(newMessage);
        body.addView(SocialSurface.muted(requireContext(), getString(R.string.oc3_chat_help)));
        status = SocialSurface.muted(requireContext(), getString(R.string.wiring_loading));
        body.addView(status);
        retry =
                SocialSurface.quiet(
                        requireContext(),
                        getString(R.string.wiring_retry),
                        () -> {
                            retry.setVisibility(View.GONE);
                            loadFlow();
                            load();
                        });
        retry.setVisibility(View.GONE);
        body.addView(retry);
        list = new RecyclerView(requireContext());
        list.setId(R.id.conversations_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new Rows());
        body.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        FirebaseEnvironment environment = ((AniloveApplication) getApplication()).foundation();
        sessions = environment.sessions;
        ticket = sessions == null ? null : sessions.capture();
        if (ticket == null || ticket.uid == null) {
            status.setText(R.string.wiring_auth_required);
            return;
        }
        direct =
                new DirectInboxAdapter(
                        (androidx.appcompat.app.AppCompatActivity) requireActivity(), sessions);
        sessions.onInvalidated(invalidation);
        repository =
                new ParticipantCommunicationRepository(FirebaseFirestore.getInstance(), sessions);
        tabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    public void onTabSelected(TabLayout.Tab tab) {
                        showTab(tab.getPosition() == 1);
                    }

                    public void onTabUnselected(TabLayout.Tab tab) {}

                    public void onTabReselected(TabLayout.Tab tab) {
                        if (tab.getPosition() == 1) {
                            loadFlow();
                            load();
                        }
                    }
                });
        tabs.selectTab(tabs.getTabAt(adoptionSelected ? 1 : 0));
        showTab(adoptionSelected);
        list.addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView v, int x, int y) {
                        if (adoptionSelected
                                && y > 0
                                && ((LinearLayoutManager) list.getLayoutManager())
                                                .findLastVisibleItemPosition()
                                        >= rows.size() + flowRows.size() - 5) load();
                    }
                });
    }

    private void showTab(boolean adoption) {
        adoptionSelected = adoption;
        if (list == null || direct == null) return;
        list.setAdapter(adoption ? new Rows() : direct);
        newMessage.setVisibility(adoption ? View.GONE : View.VISIBLE);
        status.setText(
                adoption
                        ? (flowRows.isEmpty() && rows.isEmpty()
                                ? R.string.chat_no_messages
                                : R.string.oc3_adoption_messages)
                        : R.string.oc3_direct_messages);
        retry.setVisibility(View.GONE);
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putBoolean("adoption_chat_tab", adoptionSelected);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (direct != null) direct.start();
        loadFlow();
        if (repository == null || !current()) return;
        repository.listenInbox(
                ticket,
                (snapshot, error) -> {
                    if (!current() || list == null) return;
                    if (error != null) {
                        if (adoptionSelected && rows.isEmpty() && flowRows.isEmpty()) {
                            status.setText(R.string.chat_load_failed);
                            retry.setVisibility(View.VISIBLE);
                        }
                        return;
                    }
                    if (snapshot == null) return;
                    for (DocumentSnapshot row : snapshot.getDocuments()) {
                        rows.removeIf(old -> old.getId().equals(row.getId()));
                        rows.add(row);
                    }
                    rows.sort(
                            (a, b) -> {
                                com.google.firebase.Timestamp x = a.getTimestamp("updatedAt"),
                                        y = b.getTimestamp("updatedAt");
                                return x == null ? 1 : y == null ? -1 : y.compareTo(x);
                            });
                    while (rows.size() > 100) rows.remove(rows.size() - 1);
                    if (cursor == null && !snapshot.isEmpty()) {
                        cursor = snapshot.getDocuments().get(snapshot.size() - 1);
                        more = snapshot.size() == 20;
                    }
                    if (adoptionSelected) {
                        loadFlow();
                        list.getAdapter().notifyDataSetChanged();
                        status.setText(R.string.oc3_adoption_messages);
                        retry.setVisibility(View.GONE);
                    } else direct.refresh();
                });
    }

    @Override
    public void onPause() {
        if (direct != null) direct.stop();
        if (repository != null) repository.stop();
        super.onPause();
    }

    private boolean current() {
        return !isDestroyed() && !isFinishing() && sessions != null && sessions.current(ticket);
    }

    @SuppressWarnings("unchecked")
    private void loadFlow() {
        if (ticket == null || ticket.uid == null) return;
        long epoch = generation, request = ++flowLoadGeneration;
        AdoptionApi.call("flowRead", AdoptionFlowModel.map("kind", "chatInbox", "after", null))
                .addOnCompleteListener(
                        t -> {
                            if (!current()
                                    || epoch != generation
                                    || request != flowLoadGeneration
                                    || list == null) return;
                            if (!t.isSuccessful()) {
                                if (adoptionSelected) {
                                    status.setText(R.string.chat_load_failed);
                                    retry.setVisibility(View.VISIBLE);
                                }
                                return;
                            }
                            flowRows.clear();
                            Object raw = t.getResult().get("rows");
                            if (raw instanceof List)
                                for (Object row : (List<?>) raw)
                                    if (row instanceof Map) flowRows.add((Map<String, Object>) row);
                            if (adoptionSelected) {
                                list.getAdapter().notifyDataSetChanged();
                                status.setText(
                                        flowRows.isEmpty() && rows.isEmpty()
                                                ? R.string.chat_no_messages
                                                : R.string.oc3_adoption_messages);
                            }
                        });
    }

    private void load() {
        if (busy || !more || repository == null || !current()) return;
        busy = true;
        long epoch = ++generation;
        retry.setVisibility(View.GONE);
        repository
                .inbox(ticket, cursor)
                .addOnCompleteListener(
                        result -> {
                            if (!current() || epoch != generation) return;
                            busy = false;
                            if (!result.isSuccessful()) {
                                if (adoptionSelected) {
                                    status.setText(R.string.chat_load_failed);
                                    retry.setVisibility(View.VISIBLE);
                                }
                                return;
                            }
                            for (DocumentSnapshot row : result.getResult())
                                if (rows.stream().noneMatch(old -> old.getId().equals(row.getId())))
                                    rows.add(row);
                            if (!result.getResult().isEmpty())
                                cursor =
                                        result.getResult()
                                                .getDocuments()
                                                .get(result.getResult().size() - 1);
                            more = result.getResult().size() == 20;
                            while (rows.size() > 100) rows.remove(0);
                            if (adoptionSelected) {
                                list.getAdapter().notifyDataSetChanged();
                                status.setText(R.string.oc3_adoption_messages);
                            }
                        });
    }

    private final class Rows extends RecyclerView.Adapter<Holder> {
        @Override
        public int getItemCount() {
            return Math.max(1, flowRows.size() + rows.size());
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int type) {
            MaterialButton button = SocialSurface.quiet(requireContext(), "", () -> {});
            button.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new Holder(button);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            MaterialButton button = (MaterialButton) holder.itemView;
            button.setOnClickListener(null);
            button.setIconResource(0);
            button.setEnabled(false);
            if (flowRows.isEmpty() && rows.isEmpty()) {
                button.setText(R.string.chat_no_messages);
                return;
            }
            button.setEnabled(true);
            if (position < flowRows.size()) {
                Map<String, Object> row = flowRows.get(position);
                button.setText(
                        String.valueOf(
                                        row.getOrDefault(
                                                "title",
                                                getString(R.string.chat_participant_conversation)))
                                + "\n"
                                + getString(R.string.oc3_adoption_messages));
                button.setIconResource(R.drawable.ic_nav_chat);
                button.setOnClickListener(
                        v ->
                                ((MainActivity) requireActivity())
                                        .navigate("adoption-chat:" + row.get("adoptionId")));
                return;
            }
            DocumentSnapshot row = rows.get(position - flowRows.size());
            String activity =
                    row.getTimestamp("updatedAt") == null
                            ? ""
                            : java.text.DateFormat.getDateTimeInstance(
                                            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                    .format(row.getTimestamp("updatedAt").toDate());
            button.setText(
                    getString(R.string.chat_participant_conversation)
                            + (activity.isEmpty() ? "" : "\n" + activity));
            button.setIconResource(R.drawable.ic_nav_chat);
            button.setOnClickListener(
                    v -> ((MainActivity) requireActivity()).conversation(row.getId()));
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View view) {
            super(view);
        }
    }

    @Override
    public void onDestroyView() {
        generation++;
        if (direct != null) direct.close();
        if (repository != null) repository.close();
        if (sessions != null) sessions.removeInvalidation(invalidation);
        list = null;
        super.onDestroyView();
    }
}
