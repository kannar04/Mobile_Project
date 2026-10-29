package com.gmail.manhle240406.project.notifications;

import androidx.annotation.DrawableRes;

import com.gmail.manhle240406.project.R;

/** Stable visual category mapping for validated inbox kinds. */
final class InboxPresentation {
    private InboxPresentation() {}

    @DrawableRes
    static int icon(String kind) {
        if ("adoption".equals(kind)) return R.drawable.ic_inbox_adoption;
        if ("message".equals(kind)) return R.drawable.ic_nav_chat;
        if ("like".equals(kind)) return R.drawable.ic_nav_favorite;
        if ("comment".equals(kind)) return R.drawable.ic_inbox_comment;
        if ("pet_review".equals(kind)) return R.drawable.ic_inbox_review;
        if ("own_intake".equals(kind)) return R.drawable.ic_inbox_report;
        return R.drawable.ic_flow_notifications;
    }
}
