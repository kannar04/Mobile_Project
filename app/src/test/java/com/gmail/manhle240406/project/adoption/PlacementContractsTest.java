package com.gmail.manhle240406.project.adoption;

import static org.junit.Assert.*;

import com.gmail.manhle240406.project.community.FeedWindow;
import com.gmail.manhle240406.project.pet.PetSchema;

import org.junit.Test;

import java.time.*;
import java.util.*;

public class PlacementContractsTest {
    private Map<String, Object> question() {
        return PetSchema.map(
                "id",
                "routine",
                "type",
                "tri_state",
                "required",
                true,
                "topic",
                "care_routine",
                "purpose",
                "Discuss preparation",
                "labels",
                PetSchema.map("vi", "Chu?n b?", "en", "Preparation"),
                "options",
                Arrays.asList("yes", "no", "needs_discussion"));
    }

    private Map<String, Object> template(List<Map<String, Object>> q) {
        return PetSchema.map(
                "schemaVersion", 1L, "templateId", "care_demo", "version", 1L, "questions", q);
    }

    @Test
    public void unknownAndFirstTimeExperienceNeverBecomeSafetyScore() {
        Map<String, Object> a =
                QuestionnaireContract.answers(
                        template(Collections.singletonList(question())),
                        PetSchema.map("routine", "needs_discussion"));
        assertEquals("needs_discussion", a.get("routine"));
        assertFalse(a.containsKey("score"));
        assertThrows(UnsupportedOperationException.class, () -> a.put("score", 100));
    }

    @Test
    public void invasiveQuestionsAndPrivateUnexpectedAnswersReject() {
        Map<String, Object> q = question();
        q.put("topic", "salary");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QuestionnaireContract.answers(
                                template(Collections.singletonList(q)),
                                PetSchema.map("routine", "yes")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QuestionnaireContract.answers(
                                template(Collections.singletonList(question())),
                                PetSchema.map("routine", "yes", "address", "private")));
    }

    @Test
    public void missingRequiredAndPrescribedFailureChoiceReject() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QuestionnaireContract.answers(
                                template(Collections.singletonList(question())),
                                Collections.emptyMap()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QuestionnaireContract.answers(
                                template(Collections.singletonList(question())),
                                PetSchema.map("routine", "unfit")));
    }

    @Test
    public void sixRequiredAndEightTotalLimitsProtectShortForm() {
        List<Map<String, Object>> q = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Map<String, Object> row = question();
            row.put("id", "q" + i);
            q.add(row);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionnaireContract.answers(template(q), Collections.emptyMap()));
    }

    @Test
    public void utf8TotalBudgetRejectsOversizedPrivateDraft() {
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, Object> answers = new HashMap<>();
        for (int i = 0; i < 8; i++) {
            Map<String, Object> q = question();
            q.put("id", "q" + i);
            q.put("type", "short_text");
            q.put("required", false);
            q.put("options", Collections.emptyList());
            questions.add(q);
            answers.put("q" + i, "??".repeat(400));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> QuestionnaireContract.answers(template(questions), answers));
    }

    @Test
    public void planUsesExplicitTimezoneThirtyDayAndReservationHorizon() {
        Map<String, Object> m =
                PetSchema.map(
                        "utcMillis",
                        3600000L,
                        "displayTimeZone",
                        "Asia/Bangkok",
                        "location",
                        "Fictional point",
                        "notes",
                        "");
        PreparationContract.meeting(m, 0, 7200000);
        assertThrows(IllegalArgumentException.class, () -> PreparationContract.meeting(m, 0, 1000));
        m.put("displayTimeZone", "Mars/Unknown");
        assertThrows(DateTimeException.class, () -> PreparationContract.meeting(m, 0, 7200000));
    }

    @Test
    public void unapprovedFollowupAndHiddenClausesCannotBecomeTerms() {
        Map<String, Object> terms =
                PetSchema.map(
                        "templateVersion",
                        1L,
                        "mode",
                        "fictional_course",
                        "followupMode",
                        "none_demo");
        PreparationContract.terms(terms);
        terms.put("followupMode", "90days");
        assertThrows(IllegalArgumentException.class, () -> PreparationContract.terms(terms));
        terms.put("followupMode", "none_demo");
        terms.put("retrieval", true);
        assertThrows(IllegalArgumentException.class, () -> PreparationContract.terms(terms));
    }

    @Test
    public void parentCompletionClosesOnlyNonselectedRequestsEvenBeforeChildRepair() {
        Map<String, Object> parent =
                PetSchema.map("finalized", true, "selectedApplicationId", "winner");
        assertEquals(
                "closed_not_selected",
                PreparationContract.effectiveStatus("loser", "pending", parent));
        assertEquals(
                "completed", PreparationContract.effectiveStatus("winner", "completed", parent));
        assertEquals("withdrawn", PreparationContract.effectiveStatus("loser", "withdrawn", null));
    }

    private PlacementSession<String> session() {
        return new PlacementSession<>(
                new FeedWindow.Identity<String>() {
                    public String id(String s) {
                        return s;
                    }

                    public long version(String s) {
                        return 1;
                    }
                });
    }

    @Test
    public void logoutAndFilterGenerationDropStalePagesAndPrivateDraft() {
        PlacementSession<String> s = session();
        long a = s.apply("alice", "role:Host");
        s.draft(PetSchema.map("message", "private"));
        assertTrue(s.begin(a, null));
        assertFalse(s.begin(a, null));
        long b = s.apply("bob", "role:Host");
        assertFalse(s.accept(a, null, Arrays.asList("old"), false));
        assertTrue(s.draft().isEmpty());
        assertTrue(s.rows().isEmpty());
        assertTrue(s.begin(b, null));
        assertTrue(s.accept(b, null, Arrays.asList("new"), false));
        s.close();
        assertTrue(s.rows().isEmpty());
        assertFalse(s.begin(b, null));
    }

    @Test
    public void prependPreservesReadingAnchorAndNewChatDoesNotJumpWhileReading() {
        PlacementSession<String> s = session();
        long g = s.apply("a", "thread");
        assertTrue(s.begin(g, null));
        s.accept(g, null, Arrays.asList("m2", "m3"), false);
        s.reading("m2", 31, false);
        assertTrue(s.begin(g, "older"));
        s.accept(g, "older", Arrays.asList("m1"), true);
        assertEquals("m2", s.anchor());
        assertEquals(31, s.offset());
        s.head(g, Arrays.asList("m4"));
        assertFalse(s.refresh(g, true));
        assertTrue(s.hasNew());
        s.reading("m3", 0, true);
        assertTrue(s.refresh(g, true));
        assertFalse(s.hasNew());
        assertEquals(Arrays.asList("m1", "m2", "m3", "m4"), s.rows());
    }

    @Test
    public void boundedWindowAndUnsupportedPagesNeverGrowPrivateMemory() {
        PlacementSession<String> s = session();
        long g = s.apply("a", "history");
        for (int p = 0; p < 6; p++) {
            List<String> rows = new ArrayList<>();
            for (int i = 0; i < 20; i++) rows.add("r" + (p * 20 + i));
            String cursor = "p" + p;
            assertTrue(s.begin(g, cursor));
            s.accept(g, cursor, rows, false);
        }
        assertEquals(100, s.rows().size());
        assertEquals("r20", s.rows().get(0));
    }
}
