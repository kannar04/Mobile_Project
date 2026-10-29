package com.gmail.manhle240406.project.media;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class MediaContractTest {
    private final String a = "11111111-1111-4111-a111-111111111111",
            b = "22222222-2222-4222-a222-222222222222";

    @Test
    public void prepareNeverUpscalesAndBoundsPixelsBeforeAllocation() {
        assertArrayEquals(new int[] {2048, 1024}, MediaContract.preparedSize(4000, 2000));
        assertArrayEquals(new int[] {300, 100}, MediaContract.preparedSize(300, 100));
        assertThrows(IllegalArgumentException.class, () -> MediaContract.preparedSize(5000, 5000));
        assertThrows(
                IllegalArgumentException.class,
                () -> MediaContract.preparedSize(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> MediaContract.preparedSize(0, 10));
    }

    @Test
    public void reorderKeepsImmutableSelectionAndRejectsDuplicateAssets() {
        List<String> source = Arrays.asList(a, b), moved = MediaContract.move(source, 0, 1);
        assertEquals(Arrays.asList(b, a), moved);
        assertEquals(Arrays.asList(a, b), source);
        assertThrows(UnsupportedOperationException.class, () -> moved.add(a));
        assertThrows(
                IllegalArgumentException.class,
                () -> MediaContract.orderedSelection(Arrays.asList(a, a)));
        assertThrows(IllegalArgumentException.class, () -> MediaContract.move(source, 0, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> MediaContract.orderedSelection(Arrays.asList(a, b, a, b, a)));
    }

    @Test
    public void commandIdentityRejectsPathsAndNonDigestInputs() {
        assertThrows(IllegalArgumentException.class, () -> MediaContract.id("../other"));
        assertThrows(IllegalArgumentException.class, () -> MediaContract.digest("filename.jpg"));
        assertEquals("a".repeat(64), MediaContract.digest("a".repeat(64)));
    }
}
