package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.TextView;
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

public class SupportCenterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_support_center);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.support_center_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.support_center_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.support_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        List<String> items = Arrays.asList(
            "Hướng dẫn Nhận nuôi: Quy trình từ A-Z",
            "Chăm sóc Thú cưng: Dinh dưỡng & Y tế cơ bản",
            "Báo cáo Sự cố & Hỗ trợ khẩn cấp",
            "Chính sách & Biểu khoản AniLove"
        );
        recycler.setAdapter(new SupportAdapter(items, this));
    }

    private static class SupportAdapter extends RecyclerView.Adapter<SupportAdapter.ViewHolder> {
        private final List<String> data;
        private final android.content.Context ctx;
        public SupportAdapter(List<String> d, android.content.Context c) { data = d; ctx = c; }

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
                    .setTitle("Trợ giúp và hướng dẫn")
                    .setMessage("Đang xem hướng dẫn: " + item + "\n\n(Lưu ý: Phiên bản Thử nghiệm - Một số tính năng có thể chưa hoàn chỉnh. Liên hệ admin để được hỗ trợ trực tiếp.)")
                    .setPositiveButton("Đã hiểu", null)
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
