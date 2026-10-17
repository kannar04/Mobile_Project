package com.gmail.manhle240406.project.wiring;

import java.util.*;

/** Runtime boundaries, never a role/capability grant. No paid-service or upload fallback. */
public final class SparkCapabilities {
    private SparkCapabilities() {}
    public static boolean uploadsEnabled() { return false; }
    public static boolean trustedCommandsEnabled() { return false; }
    public static final Set<String> MANAGED_ROUTES=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "AdvancedPetCareActivity","AdminCaseToolsActivity","PostAdoptionTimelineActivity","FollowupExtensionsActivity",
        "WelfareConcernsActivity","VoluntaryReturnActivity","AppealsActivity","PlacementReviewsActivity",
        "LostAndFoundActivity","FosteringActivity","PetExchangeActivity","NearbyMapActivity","RemindersActivity",
        "RecommendationsActivity","AdminDashboardActivity","QuestionnaireActivity","MyApplicationsActivity",
        "HostApplicationsActivity","MeetingPlannerActivity","HandoverActivity","AdoptionHistoryActivity",
        "ChatListActivity","ChatDetailActivity","AdoptionJourneyActivity","AdoptionSuccessActivity","ListingReviewActivity",
        "PetCareActivity","SupportCenterActivity","OfflineSyncActivity")));
}
