package com.gmail.manhle240406.project.profile;

import java.util.*;
import java.util.regex.Pattern;

/** F03 allowlists. Public rendering never receives a private contact record. */
public final class ProfileSchema {
    private ProfileSchema() {}

    public static final List<String> AVATARS =
            Collections.unmodifiableList(Arrays.asList("default", "cat", "dog"));
    public static final List<String> REGIONS =
            Collections.unmodifiableList(Arrays.asList("north", "central", "south"));
    public static final Set<String> DRAFT =
            keys(
                    "ownerUid",
                    "schemaVersion",
                    "version",
                    "displayName",
                    "avatarKey",
                    "regionCode",
                    "bio",
                    "submission",
                    "lastOperationId",
                    "updatedAt");
    public static final Set<String> CONTACT =
            keys(
                    "ownerUid",
                    "schemaVersion",
                    "version",
                    "phoneE164",
                    "lastOperationId",
                    "updatedAt");
    public static final Set<String> CONTROL =
            keys(
                    "ownerUid",
                    "schemaVersion",
                    "version",
                    "visibility",
                    "lastOperationId",
                    "updatedAt");
    public static final Set<String> PUBLIC =
            keys(
                    "ownerUid",
                    "schemaVersion",
                    "publicationVersion",
                    "sourceDraftVersion",
                    "displayName",
                    "avatarKey",
                    "regionCode",
                    "bio",
                    "publishedAt");
    private static final Pattern UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    // A conservative warning, never a claim that all personal data has been detected.
    private static final Pattern CONTACT_PATTERN =
            Pattern.compile(
                    "(?iu)(?:\\+?\\d[\\d .()\\-]{5,}\\d|[\\w.+-]+@[\\w.-]+\\.[\\p{L}]{2,}|(?:địa"
                            + " chỉ|dia chi|address|đường|duong|street|phường|phuong|số nhà|so"
                            + " nha)\\s*[:\\d\\p{L}])");

    private static Set<String> keys(String... keys) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(keys)));
    }

    public static int length(String s) {
        return s.codePointCount(0, s.length());
    }

    public static boolean validContent(Map<String, Object> m) {
        Object name = m.get("displayName"), bio = m.get("bio"), region = m.get("regionCode");
        return name instanceof String
                && name.equals(((String) name).trim())
                && length((String) name) >= 2
                && length((String) name) <= 40
                && bio instanceof String
                && length((String) bio) <= 500
                && AVATARS.contains(m.get("avatarKey"))
                && (region == null || REGIONS.contains(region));
    }

    public static Map<String, Object> content(
            String name, String avatar, String region, String bio) {
        Map<String, Object> map = new HashMap<>();
        map.put("displayName", name.trim());
        map.put("avatarKey", avatar);
        map.put("regionCode", region);
        map.put("bio", bio.trim());
        return Collections.unmodifiableMap(map);
    }

    public static Map<String, Object> publicContent(Map<String, Object> map) {
        if (!validContent(map)) throw new IllegalArgumentException("invalid_content");
        return content(
                (String) map.get("displayName"),
                (String) map.get("avatarKey"),
                (String) map.get("regionCode"),
                (String) map.get("bio"));
    }

    public static boolean sensitive(Map<String, Object> map) {
        return CONTACT_PATTERN.matcher(map.get("displayName") + "\n" + map.get("bio")).find();
    }

    /** Normalizes Vietnamese mobile input without treating formatting as verification. */
    public static String phone(String value) {
        if (value == null) return null;
        String s = value.trim().replaceAll("[\\s().-]", "");
        if (s.isEmpty()) return null;
        if (s.startsWith("0084")) s = "+84" + s.substring(4);
        else if (s.startsWith("84")) s = "+" + s;
        else if (s.startsWith("0")) s = "+84" + s.substring(1);
        return s;
    }

    public static boolean validPhone(Object value) {
        if (value == null) return true;
        if (!(value instanceof String)) return false;
        String phone = (String) value;
        // Current Vietnamese numbers contain nine national digits after +84 and do not start with
        // zero.
        return phone.matches("\\+84[1-9][0-9]{8}");
    }

    public static boolean positive(Object n) {
        return n instanceof Long && (Long) n > 0;
    }

    public static long version(Map<String, Object> record) {
        return record == null ? 0 : (Long) record.get("version");
    }

    public static boolean validRecord(Map<String, Object> m, Set<String> shape, String uid) {
        if (m != null
                && Long.valueOf(2).equals(m.get("schemaVersion"))
                && (shape == DRAFT || shape == PUBLIC)) {
            if (!m.containsKey("avatarAssetId")
                    || !m.containsKey("avatarChoiceVersion")
                    || !(m.get("avatarChoiceVersion") instanceof Long)
                    || (Long) m.get("avatarChoiceVersion") < 0) return false;
            Object id = m.get("avatarAssetId");
            if (id != null && (!(id instanceof String) || !validOperationId((String) id)))
                return false;
            Map<String, Object> old = new HashMap<>(m);
            old.remove("avatarAssetId");
            old.remove("avatarChoiceVersion");
            old.put("schemaVersion", 1L);
            return validRecord(old, shape, uid);
        }
        if (m == null
                || !m.keySet().equals(shape)
                || !uid.equals(m.get("ownerUid"))
                || !Long.valueOf(1).equals(m.get("schemaVersion"))) return false;
        if (shape == PUBLIC)
            return positive(m.get("publicationVersion"))
                    && positive(m.get("sourceDraftVersion"))
                    && validContent(m);
        if (!positive(m.get("version"))
                || !(m.get("lastOperationId") instanceof String)
                || !UUID.matcher((String) m.get("lastOperationId")).matches()) return false;
        if (shape == DRAFT)
            return validContent(m)
                    && Arrays.asList("draft", "submitted").contains(m.get("submission"));
        if (shape == CONTACT) return validPhone(m.get("phoneE164"));
        return shape == CONTROL && Arrays.asList("hidden", "public").contains(m.get("visibility"));
    }

    public static boolean validModeration(Map<String, Object> m, String uid) {
        return m != null
                && m.keySet()
                        .equals(
                                keys(
                                        "schemaVersion",
                                        "ownerUid",
                                        "hold",
                                        "moderationVersion",
                                        "reasonCode",
                                        "decisionId",
                                        "updatedAt"))
                && uid.equals(m.get("ownerUid"))
                && Long.valueOf(1).equals(m.get("schemaVersion"))
                && Arrays.asList("clear", "held").contains(m.get("hold"))
                && positive(m.get("moderationVersion"))
                && (m.get("reasonCode") == null || m.get("reasonCode") instanceof String)
                && (m.get("decisionId") == null || m.get("decisionId") instanceof String)
                && m.get("updatedAt") instanceof com.google.firebase.Timestamp;
    }

    public static boolean samePayload(Map<String, Object> record, Map<String, Object> payload) {
        if (record == null) return false;
        for (String key : payload.keySet())
            if (!Objects.equals(record.get(key), payload.get(key))) return false;
        return true;
    }

    public static boolean validOperationId(String id) {
        return id != null && UUID.matcher(id).matches();
    }
}
