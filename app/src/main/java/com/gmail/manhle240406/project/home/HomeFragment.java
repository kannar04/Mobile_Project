package com.gmail.manhle240406.project.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialComposerActivity;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View.OnClickListener openComposer = v -> {
            startActivity(new Intent(requireContext(), SocialComposerActivity.class));
        };
        View postCard = view.findViewById(R.id.home_create_post_card);
        if (postCard != null) postCard.setOnClickListener(openComposer);
        View photoBtn = view.findViewById(R.id.home_post_photo_btn);
        if (photoBtn != null) photoBtn.setOnClickListener(openComposer);
        View statusBtn = view.findViewById(R.id.home_post_status_btn);
        if (statusBtn != null) statusBtn.setOnClickListener(openComposer);
    }
}
