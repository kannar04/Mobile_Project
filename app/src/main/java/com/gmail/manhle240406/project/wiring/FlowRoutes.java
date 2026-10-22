package com.gmail.manhle240406.project.wiring;

import android.app.Activity;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.auth.*;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.profile.*;
import com.gmail.manhle240406.project.notifications.*;
import java.util.*;

/** Approved journey entry points. Feature IDs are test metadata, never permission grants. */
public final class FlowRoutes {
    private FlowRoutes() {}
    public static final int[] GROUPS={R.string.flow_discover,R.string.flow_community,R.string.flow_placement,R.string.flow_care,R.string.flow_account,R.string.flow_operator};
    public static final class Route {
        public final String feature; public final int group,title; public final Class<? extends Activity> activity;
        public final boolean contextual;
        Route(String f,int g,int t,Class<? extends Activity> a,boolean c){feature=f;group=g;title=t;activity=a;contextual=c;}
    }
    private static Route page(String f,int g,int t,Class<? extends Activity> a){return new Route(f,g,t,a,false);}
    private static Route sheet(String f,int g,int t){return new Route(f,g,t,null,true);}
    public static final List<Route> ALL=Collections.unmodifiableList(Arrays.asList(
        page("F01",4,R.string.auth_account_title,AuthActivity.class),
        page("F02",4,R.string.flow_change_password,PasswordChangeActivity.class),
        page("F03",4,R.string.profile_title,ProfileActivity.class),
        page("F05",5,R.string.flow_admin_accounts,CourseAdminActivity.class),
        page("F06",0,R.string.pet_title,com.gmail.manhle240406.project.pet.PetActivity.class),
        page("F07",0,R.string.listing_title,com.gmail.manhle240406.project.listing.ListingActivity.class),
        sheet("F08",3,R.string.flow_care_fields),
        page("F09",0,R.string.photo_gallery_title,com.gmail.manhle240406.project.media.PhotoGalleryActivity.class),
        page("F10",5,R.string.listing_review_title,ListingReviewActivity.class),
        page("F11",0,R.string.catalog_title,CatalogActivity.class),
        page("F12",0,R.string.discovery_title,DiscoveryActivity.class),
        page("F13",0,R.string.flow_filters,DiscoveryActivity.class),
        page("F14",0,R.string.favorites_title,FavoritesActivity.class),
        sheet("F15",0,R.string.flow_share),
        sheet("F16",2,R.string.flow_questionnaire),
        page("F17",2,R.string.my_applications_title,MyApplicationsActivity.class),
        page("F18",2,R.string.host_applications_title,HostApplicationsActivity.class),
        page("F19",1,R.string.chat_list_title,ChatListActivity.class),
        page("F20",2,R.string.meeting_planner_title,MeetingPlannerActivity.class),
        page("F21",2,R.string.handover_title,HandoverActivity.class),
        page("F22",2,R.string.adoption_history_title,AdoptionHistoryActivity.class),
        page("F23",3,R.string.f23_title,PostAdoptionTimelineActivity.class),
        sheet("F24",3,R.string.flow_extension),
        page("F25",3,R.string.f25_title,WelfareConcernsActivity.class),
        page("F26",3,R.string.f26_title,VoluntaryReturnActivity.class),
        page("F27",3,R.string.f27_title,AppealsActivity.class),
        page("F28",2,R.string.f28_title,PlacementReviewsActivity.class),
        page("F29",1,R.string.report_mine,MyReportsActivity.class),
        page("F30",1,R.string.flow_inbox,InboxActivity.class),
        page("F31",1,R.string.social_title,SocialFeedActivity.class),
        page("F32",0,R.string.f32_title,LostAndFoundActivity.class),
        page("F33",3,R.string.f33_title,FosteringActivity.class),
        page("F34",2,R.string.f34_title,PetExchangeActivity.class),
        page("F35",0,R.string.f35_title,NearbyMapActivity.class),
        page("F36",3,R.string.f36_title,RemindersActivity.class),
        sheet("F37",0,R.string.flow_recommendations),
        page("F38",4,R.string.wiring_settings_title,EnhancedSettingsActivity.class),
        page("F39",5,R.string.f39_title,AdminCaseToolsActivity.class),
        page("F40",5,R.string.f40_title,AdminDashboardActivity.class),
        page("F41",4,R.string.f41_title,OfflineSyncActivity.class),
        page("F42",4,R.string.flow_readiness,AuthActivity.class),
        page("F44",3,R.string.f44_title,SupportCenterActivity.class)
    ));
    public static Route find(String feature){for(Route r:ALL)if(r.feature.equals(feature))return r;throw new IllegalArgumentException("unknown_feature");}
    public static int titleFor(Class<?> type){for(Route r:ALL)if(r.activity==type)return r.title;return R.string.app_name;}
}
