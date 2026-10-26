package com.gmail.manhle240406.project.notifications;

import java.util.*;

/** No routing or OS display side effects. A route is a hint requiring a fresh domain read. */
public final class InboxContract {
    public static final int PAGE = 20, WINDOW = 100, AUTO_PAGES = 3;

    private InboxContract() {}

    public static String itemId(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("inbox_id");
        return id;
    }

    public static boolean route(String kind, String route, String target, String template) {
        if (kind == null
                || route == null
                || template == null
                || target == null
                || target.length() > 128) return false;
        boolean uuid = target.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
        return (kind.equals("adoption")
                        && (((route.equals("adoption") || route.equals("adoption_request")) && uuid)
                                || (route.equals("inbox") && target.equals("main")))
                        && template.equals("adoption_update"))
                || (kind.equals("message")
                        && route.equals("direct")
                        && target.matches("[0-9a-f]{64}")
                        && template.equals("direct_message"))
                || (kind.equals("like")
                        && route.equals("post")
                        && uuid
                        && template.equals("likes_on_post"))
                || (kind.equals("pet_review")
                        && route.equals("pet")
                        && uuid
                        && (template.equals("pet_approved") || template.equals("pet_rejected")))
                || (kind.equals("comment")
                        && route.equals("post")
                        && uuid
                        && template.equals("comments_on_post"))
                || (kind.equals("own_intake")
                        && route.equals("own_report")
                        && uuid
                        && template.equals("report_received"))
                || (kind.equals("summary")
                        && route.equals("inbox")
                        && target.equals("main")
                        && template.equals("daily_activity"));
    }

    public static Map<String, Long> readSet(Map<String, Long> versions) {
        if (versions == null || versions.isEmpty() || versions.size() > PAGE)
            throw new IllegalArgumentException("read_batch_limit");
        Map<String, Long> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : versions.entrySet()) {
            itemId(e.getKey());
            if (e.getValue() == null || e.getValue() < 1)
                throw new IllegalArgumentException("item_version");
            frozen.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(frozen);
    }

    public static boolean hint(
            Map<String, String> data, String uid, String installation, long epoch, long now) {
        if (uid == null
                || installation == null
                || data == null
                || !data.keySet()
                        .equals(
                                new HashSet<>(
                                        Arrays.asList(
                                                "schemaVersion",
                                                "uid",
                                                "installationId",
                                                "bindingEpoch",
                                                "itemId",
                                                "expiresAt")))) return false;
        try {
            return data.get("schemaVersion").equals("1")
                    && uid.equals(data.get("uid"))
                    && installation.equals(data.get("installationId"))
                    && Long.parseLong(data.get("bindingEpoch")) == epoch
                    && epoch > 0
                    && Long.parseLong(data.get("expiresAt")) > now
                    && Long.parseLong(data.get("expiresAt")) <= now + 86_400_000
                    && data.get("itemId").matches("[0-9a-f]{64}");
        } catch (RuntimeException invalid) {
            return false;
        }
    }
}
