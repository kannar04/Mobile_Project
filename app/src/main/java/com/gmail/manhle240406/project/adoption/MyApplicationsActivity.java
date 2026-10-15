package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class MyApplicationsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_my_applications);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.my_applications_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.my_applications_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.my_applications_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        List<AppItem> items = new ArrayList<>();
        items.add(new AppItem("Bông (Chó Poodle)", "Ngày gửi: 14/10/2026", "Đang chờ", R.drawable.profile_avatar_dog));
        items.add(new AppItem("Miu Miu (Mèo Anh)", "Ngày gửi: 01/10/2026", "Đã chấp nhận", R.drawable.profile_avatar_cat));

        recycler.setAdapter(new AppsAdapter(items, this));
    }

    public static class AppItem {
        public final String name, date, status;
        public final int imageRes;
        public AppItem(String n, String d, String s, int img) { name = n; date = d; status = s; imageRes = img; }
    }

    private static class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {
        private final List<AppItem> data;
        private final android.content.Context ctx;
        public AppsAdapter(List<AppItem> d, android.content.Context c) { data = d; ctx = c; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_application, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppItem item = data.get(position);
            holder.name.setText(item.name);
            holder.date.setText(item.date);
            holder.status.setText(item.status);
            holder.image.setImageResource(item.imageRes);

            holder.itemView.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle("Chi tiết Yêu cầu (F17)")
                    .setMessage("Yêu cầu nhận nuôi: " + item.name + "\n- Trạng thái: " + item.status + "\n- " + item.date)
                    .setPositiveButton("Rút yêu cầu", (d, w) -> Toast.makeText(ctx, "Đã rút yêu cầu nhận nuôi", Toast.LENGTH_SHORT).show())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, date, status;
            ImageView image;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.app_pet_name);
                date = itemView.findViewById(R.id.app_date);
                status = itemView.findViewById(R.id.app_status_badge);
                image = itemView.findViewById(R.id.app_pet_image);
            }
        }
    }
}
