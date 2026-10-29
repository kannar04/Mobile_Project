package com.gmail.manhle240406.project.notifications;

import static org.junit.Assert.assertEquals;

import com.gmail.manhle240406.project.R;

import org.junit.Test;

public class InboxPresentationTest {
    @Test
    public void supportedKindsUseAssociatedIcons() {
        assertEquals(R.drawable.ic_inbox_adoption, InboxPresentation.icon("adoption"));
        assertEquals(R.drawable.ic_nav_chat, InboxPresentation.icon("message"));
        assertEquals(R.drawable.ic_nav_favorite, InboxPresentation.icon("like"));
        assertEquals(R.drawable.ic_inbox_comment, InboxPresentation.icon("comment"));
        assertEquals(R.drawable.ic_inbox_review, InboxPresentation.icon("pet_review"));
        assertEquals(R.drawable.ic_inbox_report, InboxPresentation.icon("own_intake"));
        assertEquals(R.drawable.ic_flow_notifications, InboxPresentation.icon("summary"));
    }
}
