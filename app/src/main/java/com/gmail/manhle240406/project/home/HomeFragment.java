package com.gmail.manhle240406.project.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialComposerActivity;
import com.gmail.manhle240406.project.community.SocialDetailActivity;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View.OnClickListener openComposer = v -> startActivity(new Intent(requireContext(), SocialComposerActivity.class));
        
        View postCard = view.findViewById(R.id.home_create_post_card);
        if (postCard != null) postCard.setOnClickListener(openComposer);
        View photoBtn = view.findViewById(R.id.home_post_photo_btn);
        if (photoBtn != null) photoBtn.setOnClickListener(openComposer);
        View statusBtn = view.findViewById(R.id.home_post_status_btn);
        if (statusBtn != null) statusBtn.setOnClickListener(openComposer);

        // Featured Pets Grid
        RecyclerView petRecycler = view.findViewById(R.id.home_pet_recycler);
        if (petRecycler != null) {
            petRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            List<PetItem> pets = new ArrayList<>();
            pets.add(new PetItem("Bông", "Poodle · 3 tháng", "Quận 7, TP.HCM", R.drawable.profile_avatar_dog));
            pets.add(new PetItem("Miu Miu", "Anh ngắn · 5 tháng", "Quận 1, TP.HCM", R.drawable.profile_avatar_cat));
            pets.add(new PetItem("Choco", "Chó Cỏ · 1 tuổi", "Cầu Giấy, Hà Nội", R.drawable.profile_avatar_dog));
            pets.add(new PetItem("Luna", "Husky · 2 tuổi", "Sơn Trà, Đà Nẵng", R.drawable.profile_avatar_dog));
            petRecycler.setAdapter(new PetAdapter(pets));
        }

        // Seeded Community Posts List
        RecyclerView postRecycler = view.findViewById(R.id.home_posts_recycler);
        if (postRecycler != null) {
            postRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
            List<PostItem> posts = new ArrayList<>();
            posts.add(new PostItem("Nguyễn Lan Anh", "2 giờ trước · Cứu hộ thú cưng", 
                "Hôm nay dẫn bé Bông đi công viên chơi siêu ngoan nè mọi người! 🐶❤️ Bé rất hòa đồng và thích chơi với các bạn chó khác.", 
                R.drawable.profile_avatar_dog, "124 Yêu thích", "18 Bình luận"));
            posts.add(new PostItem("Trần Hoàng Nam", "5 giờ trước · Nhận nuôi mèo", 
                "Bé Miu Miu đã được chích ngừa đầy đủ và tiêm phòng 2 mũi. Cần tìm gia đình mới thực sự yêu thương bé ở TP.HCM! 🐱✨", 
                R.drawable.profile_avatar_cat, "98 Yêu thích", "24 Bình luận"));
            posts.add(new PostItem("Trạm Cứu Hộ AniLove", "1 ngày trước · Tin từ Admin", 
                "Chiến dịch nhận nuôi thú cưng tháng 10/2026 đã sẵn sàng! Hãy ghé thăm trạm và đón bé về nhà nhé. 🏡🐾", 
                R.drawable.profile_avatar_dog, "256 Yêu thích", "45 Bình luận"));
            postRecycler.setAdapter(new PostAdapter(posts));
        }
    }

    private static class PetItem {
        public final String name, breedAge, region;
        public final int imageRes;
        public PetItem(String n, String ba, String r, int img) { name = n; breedAge = ba; region = r; imageRes = img; }
    }

    private class PetAdapter extends RecyclerView.Adapter<PetAdapter.ViewHolder> {
        private final List<PetItem> data;
        public PetAdapter(List<PetItem> d) { data = d; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pet_discovery, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PetItem item = data.get(position);
            holder.name.setText(item.name);
            holder.breedAge.setText(item.breedAge);
            holder.region.setText(item.region);
            holder.image.setImageResource(item.imageRes);
            holder.favoriteBtn.setOnClickListener(v -> Toast.makeText(requireContext(), "Đã lưu " + item.name + " vào Yêu thích", Toast.LENGTH_SHORT).show());
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
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

    private static class PostItem {
        public final String author, time, body, likes, comments;
        public final int imageRes;
        public PostItem(String a, String t, String b, int img, String l, String c) {
            author = a; time = t; body = b; imageRes = img; likes = l; comments = c;
        }
    }

    private class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {
        private final List<PostItem> data;
        public PostAdapter(List<PostItem> d) { data = d; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PostItem item = data.get(position);
            holder.author.setText(item.author);
            holder.time.setText(item.time);
            holder.body.setText(item.body);
            holder.image.setImageResource(item.imageRes);
            holder.likes.setText(item.likes);
            holder.comments.setText(item.comments);

            holder.comments.setOnClickListener(v -> startActivity(new Intent(requireContext(), SocialDetailActivity.class)));
            holder.itemView.setOnClickListener(v -> startActivity(new Intent(requireContext(), SocialDetailActivity.class)));
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView author, time, body, likes, comments;
            ImageView image;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                author = itemView.findViewById(R.id.post_author_name);
                time = itemView.findViewById(R.id.post_time);
                body = itemView.findViewById(R.id.post_body);
                image = itemView.findViewById(R.id.post_image);
                likes = itemView.findViewById(R.id.post_like_btn);
                comments = itemView.findViewById(R.id.post_comment_btn);
            }
        }
    }
}
