package com.gmail.manhle240406.project.wiring;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.adoption.*;
import com.gmail.manhle240406.project.community.CommunityFeedActivity;
import com.gmail.manhle240406.project.notifications.InboxActivity;
import com.gmail.manhle240406.project.profile.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.MaterialColors;

/** One shared native navigation surface for browsing destinations; editors keep focused navigation. */
public final class MainNavigation implements Application.ActivityLifecycleCallbacks {
    private static boolean browsing(Activity a) {
        return a instanceof ProfileActivity || a instanceof DiscoveryActivity || a instanceof FavoritesActivity
            || a instanceof InboxActivity || a instanceof CommunityFeedActivity || a instanceof FlowHubActivity
            || a instanceof EnhancedSettingsActivity || a instanceof com.gmail.manhle240406.project.community.GuestStoriesActivity;
    }
    @Override public void onActivityPostCreated(Activity a, Bundle state) {
        if (!browsing(a)) return;
        FrameLayout content=a.findViewById(android.R.id.content);
        if(content.getChildCount()!=1 || a.findViewById(R.id.bottom_navigation)!=null) return;
        View page=content.getChildAt(0); content.removeView(page);
        LinearLayout shell=new LinearLayout(a);shell.setOrientation(LinearLayout.VERTICAL);
        FrameLayout body=new FrameLayout(a);
        shell.addView(body,new LinearLayout.LayoutParams(-1,0,1));body.addView(page,new FrameLayout.LayoutParams(-1,-1));
        BottomNavigationView nav=new BottomNavigationView(a);nav.setId(R.id.bottom_navigation);
        nav.inflateMenu(R.menu.bottom_nav_menu);
        nav.setBackgroundColor(MaterialColors.getColor(nav,com.google.android.material.R.attr.colorSurface));
        nav.setElevation(0);nav.setLabelVisibilityMode(com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setItemIconTintList(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(nav,androidx.appcompat.R.attr.colorPrimary)));nav.setItemTextColor(android.content.res.ColorStateList.valueOf(MaterialColors.getColor(nav,com.google.android.material.R.attr.colorOnSurface)));
        shell.addView(nav,new LinearLayout.LayoutParams(-1,-2));content.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        int selected=a instanceof EnhancedSettingsActivity?R.id.nav_profile:R.id.nav_home;
        nav.setSelectedItemId(selected);
        nav.setOnItemSelectedListener(item->{
            MainActivity.open(a,item.getItemId()==R.id.nav_favorites?"favorites":item.getItemId()==R.id.nav_chat?"chat":item.getItemId()==R.id.nav_profile?"profile":"home");
            a.finish();return true;
        });
        // The bar owns the bottom system inset. The page still owns status/cutout and IME insets.
        ViewCompat.setOnApplyWindowInsetsListener(shell,(v,i)->{
            nav.setVisibility(i.isVisible(WindowInsetsCompat.Type.ime())?View.GONE:View.VISIBLE);return i;
        });
        ViewCompat.setOnApplyWindowInsetsListener(body,(v,i)->new WindowInsetsCompat.Builder(i)
            .setInsets(WindowInsetsCompat.Type.navigationBars(),Insets.NONE).build());
        shell.post(()->ViewCompat.requestApplyInsets(shell));
    }
    @Override public void onActivityCreated(Activity a,Bundle b){}
    @Override public void onActivityStarted(Activity a){}
    @Override public void onActivityResumed(Activity a){if(a.getApplication() instanceof AniloveApplication){com.gmail.manhle240406.project.community.CommunityOutbox q=((AniloveApplication)a.getApplication()).communityOutbox();if(q!=null){q.refresh();if(q.entries().getValue()!=null&&!q.entries().getValue().isEmpty())q.schedule(0);}}}
    @Override public void onActivityPaused(Activity a){}
    @Override public void onActivityStopped(Activity a){}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    @Override public void onActivityDestroyed(Activity a){}
}
