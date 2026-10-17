package com.gmail.manhle240406.project.wiring;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Reuses the native list/toolbar primitives; categories lead to ordinary task entry points. */
public final class FlowHubActivity extends AppCompatActivity {
    private androidx.appcompat.app.AlertDialog dialog;
    @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);
        int group=getIntent().getIntExtra("group",-1);
        if(group< -1||group>=FlowRoutes.GROUPS.length)group=-1;
        LinearLayout root=SocialSurface.page(this);
        SocialSurface.header(this,root,group<0?R.string.flow_explore:FlowRoutes.GROUPS[group],this::finish);
        LinearLayout content=SocialSurface.content(this,root);
        if(group<0){for(int i=0;i<FlowRoutes.GROUPS.length;i++){
            final int selected=i;
            android.view.View row=SocialViews.button(this,getString(FlowRoutes.GROUPS[i]),()->startActivity(new Intent(this,FlowHubActivity.class).putExtra("group",selected)));
            row.setTag("flow_group_"+i);content.addView(row);
        }}else for(FlowRoutes.Route route:FlowRoutes.ALL)if(route.group==group){
            android.view.View row=SocialViews.button(this,getString(route.title).replaceAll("\\s*\\(F[0-9]+\\)",""),()->open(route));
            row.setTag("flow_"+route.feature);content.addView(row);
        }
    }
    private void open(FlowRoutes.Route route){
        if(!route.contextual){Intent intent=new Intent(this,route.activity);if(route.feature.equals("F01")||route.feature.equals("F42"))intent.putExtra("account",true);if(route.feature.equals("F13"))intent.putExtra("filters",true);startActivity(intent);return;}
        int message=route.feature.equals("F05")?R.string.flow_admin_accounts_help:route.feature.equals("F15")?R.string.flow_share_unavailable:route.feature.equals("F37")?R.string.flow_recommendation_help:R.string.flow_context_required;
        dialog=new MaterialAlertDialogBuilder(this).setTitle(route.title).setMessage(message).setNegativeButton(R.string.flow_back,null).create();
        if(route.feature.equals("F08"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.pet_title),(d,w)->startActivity(new Intent(this,com.gmail.manhle240406.project.pet.PetActivity.class)));
        if(route.feature.equals("F16"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.my_applications_title),(d,w)->startActivity(new Intent(this,com.gmail.manhle240406.project.adoption.MyApplicationsActivity.class)));
        if(route.feature.equals("F24"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.f23_title),(d,w)->startActivity(new Intent(this,com.gmail.manhle240406.project.adoption.PostAdoptionTimelineActivity.class)));
        if(route.feature.equals("F37"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.discovery_title),(d,w)->startActivity(new Intent(this,com.gmail.manhle240406.project.adoption.DiscoveryActivity.class)));
        dialog.show();
    }
    @Override protected void onDestroy(){if(dialog!=null)dialog.dismiss();super.onDestroy();}
}
