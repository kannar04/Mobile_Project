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

public class LostAndFoundActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_lost_and_found);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lost_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.lost_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.lost_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        List<String> items = Arrays.asList(
            "Thú cưng Thất lạc: Bé Choco (Quận 7, TP.HCM)",
            "Thú cưng Tìm thấy: Mèo Vàng lạc chủ (Hà Nội)"
        );
        recycler.setAdapter(new LostAdapter(items, this));
    }

    private static class LostAdapter extends RecyclerView.Adapter<LostAdapter.ViewHolder> {
        private final List<String> data;
        private final android.content.Context ctx;
        public LostAdapter(List<String> d, android.content.Context c) { data = d; ctx = c; }

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
                    .setTitle("Thông báo thú cưng thất lạc")
                    .setMessage("Chi tiết thông báo: " + item + "\n\n- Gửi bằng chứng sở hữu để xác nhận.")
                    .setPositiveButton("Gửi bằng chứng", (d, w) -> Toast.makeText(ctx, "Đã gửi bằng chứng sở hữu", Toast.LENGTH_SHORT).show())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                text = (TextView) itemView;
            }
        }
    }
}
