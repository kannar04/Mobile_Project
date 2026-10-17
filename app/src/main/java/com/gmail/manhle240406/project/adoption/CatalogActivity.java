package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.TextView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.R;
import java.util.Arrays;
import java.util.List;

public class CatalogActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_catalog);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.catalog_root), (v, insets) - > {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.catalog_back).setOnClickListener(v - > finish());

        RecyclerView recycler = findViewById(R.id.catalog_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        List < String > catalogs = Arrays.asList("Loài: Chó (Dog)", "Loài: Mèo (Cat)", "Giống: Poodle", "Giống: Golden Retriever", "Khu vực: TP. Hồ Chí Minh", "Khu vực: Hà Nội", "Khu vực: Đà Nẵng");
        recycler.setAdapter(new CatalogAdapter(catalogs));
    }

    private static class CatalogAdapter extends RecyclerView.Adapter < CatalogAdapter.ViewHolder > {
        private final List < String > data;
        public CatalogAdapter(List < String > d) {
            data = d;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_catalog, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.name.setText(data.get(position));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name;
            SwitchCompat switchCompat;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.catalog_name);
                switchCompat = itemView.findViewById(R.id.catalog_switch);
            }
        }
    }
}