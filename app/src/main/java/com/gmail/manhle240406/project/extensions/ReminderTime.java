package com.gmail.manhle240406.project.extensions;

import java.time.*;
import java.util.List;

/** Date entry only: no medical interval or dosage computation. */
public final class ReminderTime {
    private ReminderTime() {}

    public static Instant resolve(
            String date, String time, String zone, Integer selectedOffsetMinutes) {
        LocalDateTime local = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time));
        ZoneId id = ZoneId.of(zone);
        List<ZoneOffset> offsets = id.getRules().getValidOffsets(local);
        if (offsets.isEmpty()) throw new IllegalArgumentException("nonexistent_local_time");
        if (offsets.size() > 1 && selectedOffsetMinutes == null)
            throw new IllegalArgumentException("ambiguous_local_time_select_offset");
        ZoneOffset offset =
                selectedOffsetMinutes == null
                        ? offsets.get(0)
                        : ZoneOffset.ofTotalSeconds(selectedOffsetMinutes * 60);
        if (!offsets.contains(offset)) throw new IllegalArgumentException("offset_mismatch");
        return local.toInstant(offset);
    }
}
