package com.gmail.manhle240406.project.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.R;
import java.util.ArrayList;
import java.util.List;

public class InboxActivity extends AppCompatActivity {
    private ViewSwitcher switcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inbox);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.inbox_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.inbox_back).setOnClickListener(v -> finish());
        findViewById(R.id.inbox_settings_btn).setOnClickListener(v -> 
            startActivity(new Intent(this, NotificationSettingsActivity.class))
        );

        switcher = findViewById(R.id.inbox_switcher);
        findViewById(R.id.inbox_toggle_empty).setOnClickListener(v -> switcher.showNext());

        RecyclerView recyclerView = findViewById(R.id.inbox_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<InboxItem> items = new ArrayList<>();
        items.add(new InboxItem("Yêu cầu nhận nuôi mới", "Sarah muốn gặp Bông!", "14 ngày", true));
        items.add(new InboxItem("Message from Host", "'Is Bông good with cats?'", "15 ngày", true));
        items.add(new InboxItem("Message from Host", "'Is Bông good with dogs?'", "16 ngày", false));

        recyclerView.setAdapter(new InboxAdapter(items));
    }

    public static class InboxItem {
        public final String title, message, time;
        public final boolean unread;
        public InboxItem(String t, String m, String tm, boolean u) {
            title = t; message = m; time = tm; unread = u;
        }
    }

    private static class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.ViewHolder> {
        private final List<InboxItem> data;
        public InboxAdapter(List<InboxItem> d) { data = d; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inbox, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            InboxItem item = data.get(position);
            holder.title.setText(item.title);
            holder.message.setText(item.message);
            holder.time.setText(item.time);
            holder.unreadDot.setVisibility(item.unread ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, message, time;
            View unreadDot;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.inbox_title);
                message = itemView.findViewById(R.id.inbox_message);
                time = itemView.findViewById(R.id.inbox_time);
                unreadDot = itemView.findViewById(R.id.inbox_unread_dot);
            }
        }
    }
}
