package com.gmail.manhle240406.project.adoption;

import java.time.ZoneId;
import java.util.*;

/** Pure validation; declared preparation is neither legal agreement nor verified welfare. */
public final class PreparationContract {
  private PreparationContract() {}

  public static final List<String> TERMS_CLAUSES =
      Collections.unmodifiableList(
          Arrays.asList(
              "disclosed_care_needs",
              "voluntary_preparation",
              "separate_party_handover_reports",
              "no_legal_title_or_retrieval_rights",
              "followup_inactive_none_demo",
              "help_and_discrepancy_access"));

  public static void meeting(Map<String, Object> value, long now, long expires) {
    QuestionnaireContract.keys(value, "utcMillis", "displayTimeZone", "location", "notes");
    Object n = value.get("utcMillis");
    if (!(n instanceof Long)
        || (Long) n <= now
        || (Long) n > now + 30L * 86400000
        || (Long) n > expires) throw new IllegalArgumentException("meeting_horizon");
    ZoneId.of((String) value.get("displayTimeZone"));
    AdoptionValues.text((String) value.get("location"), 500);
    if (((String) value.get("location")).isEmpty())
      throw new IllegalArgumentException("meeting_location");
    AdoptionValues.text((String) value.get("notes"), 500);
  }

  public static void terms(Map<String, Object> value) {
    QuestionnaireContract.keys(value, "templateVersion", "mode", "followupMode");
    if (!Long.valueOf(1).equals(value.get("templateVersion"))
        || !"fictional_course".equals(value.get("mode"))
        || !"none_demo".equals(value.get("followupMode")))
      throw new IllegalArgumentException("unsupported_terms");
  }

  @SuppressWarnings("unchecked")
  public static void checklist(Map<String, Object> value) {
    QuestionnaireContract.keys(value, "entries", "notes");
    Map<String, Object> entries = (Map<String, Object>) value.get("entries");
    QuestionnaireContract.keys(
        entries, "known_needs", "transport", "supplies", "responsibility", "questions");
    for (Object v : entries.values())
      if (!Arrays.asList("yes", "no", "needs_discussion").contains(v))
        throw new IllegalArgumentException("checklist_type");
    AdoptionValues.text((String) value.get("notes"), 500);
  }

  public static String effectiveStatus(
      String application, String status, Map<String, Object> parent) {
    return parent != null
            && Boolean.TRUE.equals(parent.get("finalized"))
            && !application.equals(parent.get("selectedApplicationId"))
            && !Arrays.asList(
                    "withdrawn", "rejected", "cancelled", "completed", "closed_not_selected")
                .contains(status)
        ? "closed_not_selected"
        : status;
  }
}
