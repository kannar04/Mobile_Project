package com.gmail.manhle240406.project.media;

import java.util.*;

/** F09 private-first contract. Consumer attachment is intentionally not enabled. */
public final class MediaContract {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final long MAX_PIXELS = 20_000_000;
    public static final int MAX_EDGE = 2048, MAX_OUTSTANDING = 10, DAILY_TICKETS = 20;
    public static final long TICKET_MS = 3_600_000, UNATTACHED_MS = 86_400_000;
    private MediaContract() {}
    public static int visibleTiles(int total) { if(total<0||total>10)throw new IllegalArgumentException("selection_limit");return Math.min(4,total); }
    public static int overflowCount(int total) { return total-visibleTiles(total); }
    public static String id(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("invalid_id");
        return value;
    }
    public static int[] preparedSize(int width, int height) {
        if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
            throw new IllegalArgumentException("image_dimensions");
        double scale = Math.min(1d, MAX_EDGE / (double) Math.max(width, height));
        return new int[]{Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale))};
    }
    public static List<String> orderedSelection(List<String> assets) {
        if (assets == null || assets.size() > MAX_OUTSTANDING)
            throw new IllegalArgumentException("selection_limit");
        List<String> result = new ArrayList<>();
        for (String asset : assets) { id(asset); if (result.contains(asset)) throw new IllegalArgumentException("duplicate_asset"); result.add(asset); }
        return Collections.unmodifiableList(result);
    }
    public static List<String> move(List<String> assets, int from, int to) {
        List<String> result = new ArrayList<>(orderedSelection(assets));
        if (from < 0 || to < 0 || from >= result.size() || to >= result.size()) throw new IllegalArgumentException("selection_index");
        result.add(to, result.remove(from)); return orderedSelection(result);
    }
    public static String digest(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("input_digest");
        return value;
    }
}
