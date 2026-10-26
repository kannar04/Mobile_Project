package com.gmail.manhle240406.project.community;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public final class DailyContractsTest {
    private static final class Row {
        String id;
        long version;

        Row(String id, long v) {
            this.id = id;
            version = v;
        }
    }

    private FeedWindow<Row> window() {
        return new FeedWindow<>(
                new FeedWindow.Identity<Row>() {
                    public String id(Row r) {
                        return r.id;
                    }

                    public long version(Row r) {
                        return r.version;
                    }
                });
    }

    @Test
    public void overlappingPagesDeduplicateWithoutMovingReaderAndKeepNewerContent() {
        FeedWindow<Row> w = window();
        w.append(Arrays.asList(new Row("a", 1), new Row("b", 1)), false);
        w.append(Arrays.asList(new Row("b", 2), new Row("c", 1)), false);
        assertEquals(3, w.rows().size());
        assertEquals(1, w.position("b"));
        assertEquals(2, w.rows().get(1).version);
        w.append(Collections.singletonList(new Row("b", 1)), false);
        assertEquals(2, w.rows().get(1).version);
    }

    @Test
    public void forwardAndReverseTraversalBoundRetentionAndPreserveAnchorIdentity() {
        FeedWindow<Row> w = window();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < 120; i++) rows.add(new Row("p" + i, 1));
        w.append(rows, false);
        assertEquals(100, w.rows().size());
        assertEquals(-1, w.position("p0"));
        assertEquals(0, w.position("p20"));
        w.append(rows.subList(0, 20), true);
        assertEquals(100, w.rows().size());
        assertEquals(20, w.position("p20"));
        assertEquals(-1, w.position("p119"));
    }

    @Test
    public void removalKeepsSurvivingRelativeOrder() {
        FeedWindow<Row> w = window();
        w.append(Arrays.asList(new Row("a", 1), new Row("b", 1), new Row("c", 1)), false);
        w.remove("b");
        assertEquals(1, w.position("c"));
        w.clear();
        assertEquals(0, w.rows().size());
    }

    @Test
    public void desiredLikeIntentAndExactReceiptInputAreFrozen() {
        String id = UUID.randomUUID().toString(), op = UUID.randomUUID().toString();
        Map<String, Object> input = new HashMap<>();
        input.put("active", true);
        SocialCommand c = new SocialCommand("set_like", id, null, 1, 0, 0, input, op);
        input.put("active", false);
        assertEquals(true, c.input.get("active"));
        Map<String, Object> r =
                SocialRepository.map(
                        "schemaVersion",
                        2L,
                        "operationId",
                        op,
                        "command",
                        "set_like",
                        "postId",
                        id,
                        "targetId",
                        null,
                        "base",
                        c.base(),
                        "input",
                        c.input);
        assertTrue(c.matches(r));
        r.put("input", Collections.singletonMap("active", false));
        assertFalse(c.matches(r));
    }

    @Test
    public void commandRejectsModerationNewsAndNegativeVersion() {
        String id = UUID.randomUUID().toString();
        for (String kind : Arrays.asList("moderate", "news", "clear_hold")) {
            try {
                new SocialCommand(
                        kind,
                        id,
                        null,
                        1,
                        1,
                        0,
                        Collections.emptyMap(),
                        UUID.randomUUID().toString());
                fail();
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void commentUnicodeBoundaryAndBlankDenial() {
        assertTrue(SocialRepository.text("🐈".repeat(500), 500, 2000));
        assertFalse(SocialRepository.text("🐈".repeat(501), 500, 2000));
        assertFalse(SocialRepository.text("   ", 500, 2000));
    }

    @Test
    public void reportsAllowOptionalDetailsButOtherNeedsExplanation() {
        String id = UUID.randomUUID().toString();
        ReportCommand c =
                new ReportCommand(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "submit",
                        "post",
                        id,
                        null,
                        1,
                        0,
                        "spam",
                        "",
                        null);
        assertEquals("post_" + id, c.targetKey());
        try {
            new ReportCommand(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "submit",
                    "post",
                    id,
                    null,
                    1,
                    0,
                    "other",
                    "",
                    null);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void commentReportKeyIncludesParentAndRejectsAccountAdapter() {
        String p = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
        ReportCommand c =
                new ReportCommand(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "submit",
                        "comment",
                        id,
                        p,
                        1,
                        0,
                        "privacy",
                        "Synthetic concern",
                        null);
        assertEquals("comment_" + p + "_" + id, c.targetKey());
        try {
            new ReportCommand(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "submit",
                    "account",
                    id,
                    null,
                    1,
                    0,
                    "spam",
                    "",
                    null);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
