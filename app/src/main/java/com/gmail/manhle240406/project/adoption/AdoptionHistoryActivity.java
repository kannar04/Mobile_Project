package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class AdoptionHistoryActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_adoption_history);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.adoption_history_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.adoption_history_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.adoption_history_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        List<HistoryItem> items = new ArrayList<>();
        items.add(new HistoryItem("Bé Bông (Poodle)", "Hoàn tất nhận nuôi: 10/10/2026", R.drawable.profile_avatar_dog));
        items.add(new HistoryItem("Miu Miu (Mèo Anh)", "Hoàn tất nhận nuôi: 01/08/2026", R.drawable.profile_avatar_cat));

        recycler.setAdapter(new HistoryAdapter(items));
    }

    public static class HistoryItem {
        public final String title, date;
        public final int imageRes;
        public HistoryItem(String t, String d, int img) { title = t; date = d; imageRes = img; }
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private final List<HistoryItem> data;
        public HistoryAdapter(List<HistoryItem> d) { data = d; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_adoption_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = data.get(position);
            holder.title.setText(item.title);
            holder.date.setText(item.date);
            holder.image.setImageResource(item.imageRes);
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, date;
            ImageView image;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.history_title);
                date = itemView.findViewById(R.id.history_date);
                image = itemView.findViewById(R.id.history_pet_image);
            }
        }
    }
}
