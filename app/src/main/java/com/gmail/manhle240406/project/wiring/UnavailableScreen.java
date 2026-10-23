package com.gmail.manhle240406.project.wiring;

import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialSurface;

/** Reuses the existing native surface for unavailable route states; never displays fake mutations. */
public final class UnavailableScreen {
    private UnavailableScreen() {}
    public static boolean managed(AppCompatActivity a) {
        if(!SparkCapabilities.MANAGED_ROUTES.contains(a.getClass().getSimpleName())) return false;
        show(a,R.string.wiring_service_unavailable); return true;
    }
    public static void show(AppCompatActivity a,int message) {
        LinearLayout root=SocialSurface.page(a);
        SocialSurface.header(a,root,FlowRoutes.titleFor(a.getClass()),a::finish)
            .setTitle(a.getString(FlowRoutes.titleFor(a.getClass())).replaceAll("\\s*\\(F[0-9]+\\)",""));
        LinearLayout content=SocialSurface.content(a,root);
        content.addView(SocialSurface.muted(a,a.getString(message)));
        com.gmail.manhle240406.project.community.SocialViews.privateView(root);
    }
}
