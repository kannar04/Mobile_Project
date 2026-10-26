package com.gmail.manhle240406.project.wiring;

import static org.junit.Assert.assertEquals;

import com.gmail.manhle240406.project.R;

import org.junit.Test;

public class FlowGridIconTest {
    @Test
    public void everyCareFeatureUsesItsDedicatedIcon() {
        String[] features = {"F08", "F23", "F24", "F25", "F26", "F27", "F33", "F36", "F44"};
        int[] icons = {
            R.drawable.ic_care_health,
            R.drawable.ic_care_followup,
            R.drawable.ic_care_extend,
            R.drawable.ic_care_welfare,
            R.drawable.ic_care_return,
            R.drawable.ic_care_appeal,
            R.drawable.ic_care_foster,
            R.drawable.ic_care_reminder,
            R.drawable.ic_care_support
        };
        for (int i = 0; i < features.length; i++)
            assertEquals(features[i], icons[i], FlowGrid.featureIcon(features[i], 3));
    }
}
