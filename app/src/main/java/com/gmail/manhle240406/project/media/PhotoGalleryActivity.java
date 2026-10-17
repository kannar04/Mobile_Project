package com.gmail.manhle240406.project.media;

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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class PhotoGalleryActivity extends AppCompatActivity {
    private final List<PhotoItem> photos = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!com.gmail.manhle240406.project.wiring.SparkCapabilities.uploadsEnabled()) { com.gmail.manhle240406.project.wiring.UnavailableScreen.show(this, com.gmail.manhle240406.project.R.string.wiring_uploads_paused); return; }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_photo_gallery);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.photo_gallery_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.photo_gallery_back).setOnClickListener(v -> finish());

        photos.add(new PhotoItem("Ảnh 1 (Poodle Bông)", true, 100));
        photos.add(new PhotoItem("Ảnh 2 (Góc nghiêng)", false, 60));

        RecyclerView recycler = findViewById(R.id.photo_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setAdapter(new PhotoAdapter(photos, this::showPhotoDetail));

        findViewById(R.id.photo_add_btn).setOnClickListener(v -> {
            if (photos.size() >= 10) {
                Toast.makeText(this, "Tối đa 10 ảnh cho phép", Toast.LENGTH_SHORT).show();
                return;
            }
            photos.add(new PhotoItem("Ảnh " + (photos.size() + 1), false, 100));
            RecyclerView.Adapter adapter = recycler.getAdapter();
            if (adapter != null) adapter.notifyDataSetChanged();
            Toast.makeText(this, "Đã chọn ảnh từ thư viện (Chọn " + photos.size() + "/10)", Toast.LENGTH_SHORT).show();
        });
    }

    private void showPhotoDetail(PhotoItem item) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.photo_detail_title)
            .setMessage("Chi tiết ảnh: " + item.title + "\n- Đặt làm ảnh bìa: " + (item.isCover ? "Có" : "Không") + "\n- Tiến độ tải: " + item.progress + "%")
            .setPositiveButton("Lưu thay đổi", (d, w) -> Toast.makeText(this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public static class PhotoItem {
        public final String title;
        public boolean isCover;
        public final int progress;
        public PhotoItem(String t, boolean c, int p) {
            title = t; isCover = c; progress = p;
        }
    }

    private static class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.ViewHolder> {
        private final List<PhotoItem> data;
        private final java.util.function.Consumer<PhotoItem> onClick;
        public PhotoAdapter(List<PhotoItem> d, java.util.function.Consumer<PhotoItem> cb) { data = d; onClick = cb; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_photo, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PhotoItem item = data.get(position);
            holder.coverBadge.setVisibility(item.isCover ? View.VISIBLE : View.GONE);
            holder.progressOverlay.setVisibility(item.progress < 100 ? View.VISIBLE : View.GONE);
            holder.itemView.setOnClickListener(v -> onClick.accept(item));
        }

        @Override
        public int getItemCount() { return data.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView image;
            View coverBadge, progressOverlay;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.photo_image);
                coverBadge = itemView.findViewById(R.id.photo_cover_badge);
                progressOverlay = itemView.findViewById(R.id.photo_progress_overlay);
            }
        }
    }
}
