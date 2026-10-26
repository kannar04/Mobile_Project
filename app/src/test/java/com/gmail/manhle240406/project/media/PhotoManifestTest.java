package com.gmail.manhle240406.project.media;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public final class PhotoManifestTest {
    @Test
    public void fourTilesRetainFullManifestAndCorrectOverflow() {
        assertEquals(0, MediaContract.visibleTiles(0));
        assertEquals(1, MediaContract.visibleTiles(1));
        assertEquals(3, MediaContract.visibleTiles(3));
        assertEquals(4, MediaContract.visibleTiles(4));
        assertEquals(0, MediaContract.overflowCount(4));
        assertEquals(1, MediaContract.overflowCount(5));
        assertEquals(6, MediaContract.overflowCount(10));
    }

    @Test
    public void tenUniquePhotosCanReorderWithoutTruncation() {
        List<String> ids = new ArrayList<>();
        for (int n = 0; n < 10; n++) ids.add(UUID.randomUUID().toString());
        List<String> moved = MediaContract.move(ids, 9, 0);
        assertEquals(10, moved.size());
        assertEquals(ids.get(9), moved.get(0));
        assertEquals(ids.get(0), moved.get(1));
        assertEquals(10, new HashSet<>(moved).size());
    }

    @Test
    public void duplicateAndEleventhPhotosAreRejected() {
        List<String> ids = new ArrayList<>();
        for (int n = 0; n < 101; n++) ids.add(UUID.randomUUID().toString());
        assertThrows(IllegalArgumentException.class, () -> MediaContract.orderedSelection(ids));
        assertThrows(
                IllegalArgumentException.class,
                () -> MediaContract.orderedSelection(Arrays.asList(ids.get(0), ids.get(0))));
    }
}
