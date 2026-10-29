package com.gmail.manhle240406.project.adoption;

import android.os.Bundle;
import android.widget.*;

import androidx.fragment.app.*;

import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.wiring.MainPageFragment;
import com.google.android.material.tabs.TabLayout;

/** Two private libraries with independent state and the shared bottom bar. */
public final class FavoritesLibraryFragment extends MainPageFragment {
    private String selected = "liked_posts";

    @Override
    protected void createPage(Bundle state) {
        if (state != null) selected = state.getString("favorite_tab", selected);
        LinearLayout root = SocialViews.column(requireContext());
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundResource(R.color.anilove_bg);
        setContentView(root);
        SocialSurface.header(requireActivity(), root, R.string.favorites_title, this::finish);
        TabLayout tabs = new TabLayout(requireContext());
        root.addView(tabs);
        tabs.addTab(tabs.newTab().setText(R.string.feed_liked_posts).setTag("liked_posts"));
        tabs.addTab(tabs.newTab().setText(R.string.feed_saved_pets).setTag("saved_pets"));
        FrameLayout body = new FrameLayout(requireContext());
        body.setId(R.id.favorite_library);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        tabs.addOnTabSelectedListener(
                new TabLayout.OnTabSelectedListener() {
                    public void onTabSelected(TabLayout.Tab t) {
                        show((String) t.getTag());
                    }

                    public void onTabUnselected(TabLayout.Tab t) {}

                    public void onTabReselected(TabLayout.Tab t) {}
                });
        tabs.selectTab(tabs.getTabAt("saved_pets".equals(selected) ? 1 : 0));
        show(selected);
        SocialViews.privateView(root);
    }

    private void show(String key) {
        if (!isAdded() || getChildFragmentManager().isStateSaved()) return;
        selected = key;
        FragmentManager fm = getChildFragmentManager();
        Fragment next = fm.findFragmentByTag(key);
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (Fragment f : fm.getFragments())
            tx.hide(f).setMaxLifecycle(f, androidx.lifecycle.Lifecycle.State.STARTED);
        if (next == null) {
            next = "saved_pets".equals(key) ? new SavedPetsFragment() : new LikedPostsFragment();
            tx.add(R.id.favorite_library, next, key);
        } else tx.show(next);
        tx.setMaxLifecycle(next, androidx.lifecycle.Lifecycle.State.RESUMED)
                .setPrimaryNavigationFragment(next)
                .commitNow();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        state.putString("favorite_tab", selected);
        super.onSaveInstanceState(state);
    }
}
