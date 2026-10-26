package com.gmail.manhle240406.project.wiring;

import android.app.Application;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

/** Lifecycle-owned native page content. No embedded Activities or secondary windows. */
public abstract class MainPageFragment extends Fragment {
    private FrameLayout page;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle state) {
        page = new FrameLayout(requireContext());
        page.setBackgroundResource(com.gmail.manhle240406.project.R.color.anilove_bg);
        return page;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle state) {
        createPage(state);
    }

    protected abstract void createPage(Bundle state);

    protected void setContentView(int layout) {
        setContentView(getLayoutInflater().inflate(layout, page, false));
    }

    protected void setContentView(View view) {
        page.removeAllViews();
        page.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }

    protected <T extends View> T findViewById(int id) {
        return page.findViewById(id);
    }

    protected Application getApplication() {
        return requireActivity().getApplication();
    }

    protected boolean isDestroyed() {
        return getView() == null || !isAdded();
    }

    protected boolean isFinishing() {
        return getActivity() == null || requireActivity().isFinishing();
    }

    protected void finish() {
        if (getActivity() instanceof com.gmail.manhle240406.project.MainActivity)
            ((com.gmail.manhle240406.project.MainActivity) requireActivity()).up();
    }

    public void leave(Runnable action) {
        action.run();
    }

    @Override
    public void onDestroyView() {
        page = null;
        super.onDestroyView();
    }
}
