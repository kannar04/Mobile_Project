package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import com.google.firebase.Timestamp;

import org.junit.Test;

import java.util.*;

public class CommunityOfflineContractTest {
    final String id = "00000000-0000-0000-0000-000000000001";

    Map<String, Object> feed() {
        return SocialRepository.map(
                "schemaVersion",
                3L,
                "postId",
                id,
                "authorUid",
                "B",
                "type",
                "community",
                "contentVersion",
                2L,
                "controlVersion",
                5L,
                "body",
                "Hello pets",
                "bundledIllustrationKey",
                "cat",
                "audience",
                "community",
                "state",
                "visible",
                "firstPublishedAt",
                new Timestamp(100, 0),
                "editedAt",
                null);
    }

    @Test
    public void schema3MapperExcludesIdentityAndInteractions() {
        Map<String, Object> safe = CommunityOfflineContract.viewed(feed(), id, false, false);
        assertFalse(safe.containsKey("authorUid"));
        assertFalse(safe.containsKey("likeCount"));
        assertEquals(
                SocialRepository.map("seconds", 100L, "nanoseconds", 0L),
                safe.get("firstPublishedAt"));
        assertEquals(5L, safe.get("controlVersion"));
        assertThrows(UnsupportedOperationException.class, () -> safe.put("body", "modified"));
    }

    @Test
    public void schema2IsOnlineOnlyAndNeverInventsControlVersion() {
        Map<String, Object> m = feed();
        m.put("schemaVersion", 2L);
        m.remove("controlVersion");
        assertTrue(SocialSchema.feed(m, id));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommunityOfflineContract.viewed(m, id, false, false));
    }

    @Test
    public void pendingCachedExtraFieldsAndInvalidVersionsAreNeverNormalized() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CommunityOfflineContract.viewed(feed(), id, true, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommunityOfflineContract.viewed(feed(), id, false, true));
        Map<String, Object> m = feed();
        m.put("privateEvidence", "no");
        assertFalse(SocialSchema.feed(m, id));
        m.remove("privateEvidence");
        m.put("controlVersion", 0L);
        assertFalse(SocialSchema.feed(m, id));
    }

    @Test
    public void draftAllowsEmptyCandidateButNeverAuthorityOrPeerIdentity() {
        Map<String, Object> draft =
                CommunityOfflineContract.draft(id, null, null, null, "", "cat", 1);
        assertFalse(draft.containsKey("authorUid"));
        assertEquals("", draft.get("body"));
        assertThrows(
                IllegalArgumentException.class,
                () -> CommunityOfflineContract.draft(id, id, null, null, "text", null, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CommunityOfflineContract.draft(
                                id, null, null, null, "x".repeat(2001), null, 1));
    }
}
