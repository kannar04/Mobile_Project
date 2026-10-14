package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.Arrays;
import java.util.List;

public class FavoritesActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_favorites);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.favorites_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.favorites_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.favorites_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        List<String> favorites = Arrays.asList("Bông (Chó Poodle)", "Miu (Mèo Anh)");
        recycler.setAdapter(new FavoritesAdapter(favorites, this));
    }

    private static class FavoritesAdapter extends RecyclerView.Adapter<FavoritesAdapter.ViewHolder> {
        private final List<String> data;
        private final android.content.Context ctx;
        public FavoritesAdapter(List<String> d, android.content.Context c) { data = d; ctx = c; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(24, 24, 24, 24);
            tv.setBackgroundResource(R.drawable.anilove_card_bg);
            tv.setTextSize(15f);
            tv.setTextColor(0xFF2D2D2D);
            return new ViewHolder(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String item = data.get(position);
            holder.text.setText(item);
            holder.itemView.setOnClickListener(v -> Toast.makeText(ctx, "Mở yêu thích: " + item, Toast.LENGTH_SHORT).show());
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            public ViewHolder(@NonNull TextView itemView) {
                super(itemView);
                text = itemView;
            }
        }
    }
}
