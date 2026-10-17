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

public class ListingReviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_listing_review);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.listing_review_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.listing_review_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.listing_review_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        List<ReviewItem> items = new ArrayList<>();
        items.add(new ReviewItem("Bông - Chó Poodle", "Đăng bởi Nguyễn Văn A · 2 giờ trước", "Đang chờ", R.drawable.profile_avatar_dog));
        items.add(new ReviewItem("Miu - Mèo Anh", "Đăng bởi Trần Thị B · 5 giờ trước", "Đã duyệt", R.drawable.profile_avatar_cat));
        items.add(new ReviewItem("Choco - Chó Cỏ", "Đăng bởi Lê Văn C · 1 ngày trước", "Từ chối", R.drawable.profile_avatar_dog));

        recycler.setAdapter(new ReviewAdapter(items, this));
    }

    public static class ReviewItem {
        public final String title, subtitle, status;
        public final int imageRes;
        public ReviewItem(String t, String s, String st, int img) {
            title = t; subtitle = s; status = st; imageRes = img;
        }
    }

    private static class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {
        private final List<ReviewItem> data;
        private final android.content.Context ctx;
        public ReviewAdapter(List<ReviewItem> d, android.content.Context c) { data = d; ctx = c; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_listing_review, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReviewItem item = data.get(position);
            holder.title.setText(item.title);
            holder.subtitle.setText(item.subtitle);
            holder.statusBadge.setText(item.status);
            holder.image.setImageResource(item.imageRes);

            holder.btnApprove.setOnClickListener(v -> 
                Toast.makeText(ctx, "Đã duyệt tin đăng: " + item.title, Toast.LENGTH_SHORT).show()
            );

            holder.btnReject.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ctx)
                    .setTitle("Lý do từ chối (F10)")
                    .setMultiChoiceItems(new String[]{"Thông tin chưa chính xác", "Ảnh không hợp lệ", "Nội dung không phù hợp"}, null, null)
                    .setPositiveButton("Gửi từ chối", (d, w) -> Toast.makeText(ctx, "Đã từ chối tin đăng: " + item.title, Toast.LENGTH_SHORT).show())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            });
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle, statusBadge;
            ImageView image;
            MaterialButton btnApprove, btnReject;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.review_title);
                subtitle = itemView.findViewById(R.id.review_subtitle);
                statusBadge = itemView.findViewById(R.id.review_status_badge);
                image = itemView.findViewById(R.id.review_pet_image);
                btnApprove = itemView.findViewById(R.id.btn_approve);
                btnReject = itemView.findViewById(R.id.btn_reject);
            }
        }
    }
}
