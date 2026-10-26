package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public final class AuthorProfileTest {
    @Test
    public void ownSavedNameIsAvailableWithoutPublishingProfile() {
        Map<String, Object> draft = new HashMap<>();
        draft.put("displayName", "dff");
        draft.put("avatarKey", "cat");
        draft.put("bio", "private biography");
        draft.put("phoneE164", "private contact");
        Map<String, Object> own = AuthorProfile.own("owner", "owner", draft);
        assertEquals("dff", own.get("displayName"));
        assertEquals("cat", own.get("avatarKey"));
        assertEquals("", own.get("bio"));
        assertFalse(own.containsKey("phoneE164"));
    }

    @Test
    public void privateNameIsNeverUsedForOtherReadersOrGuests() {
        Map<String, Object> draft = Collections.singletonMap("displayName", "dff");
        assertNull(AuthorProfile.own("other", "owner", draft));
        assertNull(AuthorProfile.own(null, "owner", draft));
        assertNull(AuthorProfile.own("owner", "owner", null));
    }
}
