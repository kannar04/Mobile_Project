package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.Arrays;
import java.util.List;

public class AdminCaseToolsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin_case_tools);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.admin_case_tools_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.admin_case_tools_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.admin_case_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        List<String> items = Arrays.asList(
            "Trung tâm Cả Phúc Lợi: 5 ca triage cần xử lý",
            "Duyệt tin đăng: 3 tin chờ kiểm duyệt",
            "Báo cáo vi phạm: 1 báo cáo cộng đồng",
            "Ca phúc lợi khẩn cấp: 1 trường hợp"
        );
        recycler.setAdapter(new AdminAdapter(items, this));
    }

    private static class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.ViewHolder> {
        private final List<String> data;
        private final android.content.Context ctx;
        public AdminAdapter(List<String> d, android.content.Context c) { data = d; ctx = c; }

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
            holder.itemView.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle("Quản trị Ca phúc lợi & Triage (F39)")
                    .setMessage("Thực hiện công cụ quản trị cho: " + item)
                    .setPositiveButton("Xử lý nhanh", (d, w) -> Toast.makeText(ctx, "Đã xử lý ca triage", Toast.LENGTH_SHORT).show())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
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
