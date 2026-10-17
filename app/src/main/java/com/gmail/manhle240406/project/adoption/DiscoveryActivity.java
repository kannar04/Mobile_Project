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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class DiscoveryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_discovery);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.discovery_root), (v, insets) - > {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        findViewById(R.id.discovery_back).setOnClickListener(v - > finish());
        findViewById(R.id.discovery_search).setOnClickListener(v - > showFilterDialog());

        RecyclerView recycler = findViewById(R.id.discovery_recycler);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));

        List < PetDiscoveryItem > pets = new ArrayList < > ();
        pets.add(new PetDiscoveryItem("Bông", "Poodle · 3 tháng", "Quận 7, TP.HCM", R.drawable.profile_avatar_dog));
        pets.add(new PetDiscoveryItem("Miu", "Anh ngắn · 5 tháng", "Quận 1, TP.HCM", R.drawable.profile_avatar_cat));
        pets.add(new PetDiscoveryItem("Choco", "Chó Cỏ · 1 tuổi", "Cầu Giấy, Hà Nội", R.drawable.profile_avatar_dog));
        pets.add(new PetDiscoveryItem("Luna", "Husky · 2 tuổi", "Sơn Trà, Đà Nẵng", R.drawable.profile_avatar_dog));

        recycler.setAdapter(new DiscoveryAdapter(pets, this));
    }

    private void showFilterDialog() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Bộ lọc nâng cao (F13)")
            .setMessage("Lọc theo loài (Chó/Mèo), độ tuổi (2 tháng - 15 năm), giới tính, tiêm phòng, khoảng cách...")
            .setPositiveButton("Áp dụng bộ lọc", (d, w) - > Toast.makeText(this, "Đã áp dụng bộ lọc", Toast.LENGTH_SHORT).show())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    public static class PetDiscoveryItem {
        public final String name, breedAge, region;
        public final int imageRes;
        public PetDiscoveryItem(String n, String ba, String r, int img) {
            name = n;
            breedAge = ba;
            region = r;
            imageRes = img;
        }
    }

    private static class DiscoveryAdapter extends RecyclerView.Adapter < DiscoveryAdapter.ViewHolder > {
        private final List < PetDiscoveryItem > data;
        private final android.content.Context ctx;
        public DiscoveryAdapter(List < PetDiscoveryItem > d, android.content.Context c) {
            data = d;
            ctx = c;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pet_discovery, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PetDiscoveryItem item = data.get(position);
            holder.name.setText(item.name);
            holder.breedAge.setText(item.breedAge);
            holder.region.setText(item.region);
            holder.image.setImageResource(item.imageRes);
            holder.itemView.setOnClickListener(v - > Toast.makeText(ctx, "Xem chi tiết thú cưng: " + item.name, Toast.LENGTH_SHORT).show());
            holder.favoriteBtn.setOnClickListener(v - > Toast.makeText(ctx, "Đã lưu vào Yêu thích: " + item.name, Toast.LENGTH_SHORT).show());
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, breedAge, region;
            ImageView image, favoriteBtn;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.pet_name);
                breedAge = itemView.findViewById(R.id.pet_breed_age);
                region = itemView.findViewById(R.id.pet_region);
                image = itemView.findViewById(R.id.pet_image);
                favoriteBtn = itemView.findViewById(R.id.pet_favorite_btn);
            }
        }
    }
}