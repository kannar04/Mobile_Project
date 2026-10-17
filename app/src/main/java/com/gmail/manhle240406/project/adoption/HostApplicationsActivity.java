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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class HostApplicationsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this)) return;
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_host_applications);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.host_applications_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.host_applications_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.host_applications_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        List<HostAppItem> items = new ArrayList<>();
        items.add(new HostAppItem("Lan Anh", "Xin nhận nuôi Bé Bông (Poodle)", "Chờ duyệt", R.drawable.profile_avatar_default));
        items.add(new HostAppItem("Hoàng Nam", "Xin nhận nuôi Bé Miu (Mèo Anh)", "Chờ duyệt", R.drawable.profile_avatar_default));

        recycler.setAdapter(new HostAppsAdapter(items, this));
    }

    public static class HostAppItem {
        public final String name, info, status;
        public final int avatarRes;
        public HostAppItem(String n, String i, String s, int av) { name = n; info = i; status = s; avatarRes = av; }
    }

    private static class HostAppsAdapter extends RecyclerView.Adapter<HostAppsAdapter.ViewHolder> {
        private final List<HostAppItem> data;
        private final android.content.Context ctx;
        public HostAppsAdapter(List<HostAppItem> d, android.content.Context c) { data = d; ctx = c; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_host_application, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HostAppItem item = data.get(position);
            holder.name.setText(item.name);
            holder.info.setText(item.info);
            holder.status.setText(item.status);
            holder.avatar.setImageResource(item.avatarRes);

            holder.btnAccept.setOnClickListener(v -> 
                Toast.makeText(ctx, "Đã chấp nhận yêu cầu của " + item.name + "! Đã giữ chỗ.", Toast.LENGTH_SHORT).show()
            );

            holder.btnReject.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle("Xác nhận từ chối (F18)")
                    .setMessage("Từ chối yêu cầu của " + item.name + "?")
                    .setPositiveButton("Từ chối", (d, w) -> Toast.makeText(ctx, "Đã từ chối yêu cầu.", Toast.LENGTH_SHORT).show())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, info, status;
            ImageView avatar;
            MaterialButton btnAccept, btnReject;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.host_applicant_name);
                info = itemView.findViewById(R.id.host_app_info);
                status = itemView.findViewById(R.id.host_app_status);
                avatar = itemView.findViewById(R.id.host_applicant_avatar);
                btnAccept = itemView.findViewById(R.id.btn_host_accept);
                btnReject = itemView.findViewById(R.id.btn_host_reject);
            }
        }
    }
}
