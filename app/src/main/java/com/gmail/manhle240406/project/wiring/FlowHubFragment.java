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
public final class FlowHubFragment extends MainPageFragment {
    private androidx.appcompat.app.AlertDialog dialog;
    @Override protected void createPage(Bundle state){
        int group=getArguments()==null?-1:getArguments().getInt("group",-1);
        if(group< -1||group>=FlowRoutes.GROUPS.length)group=-1;
        LinearLayout root=SocialViews.column(requireContext());
        setContentView(root);
        SocialSurface.header(requireActivity(),root,group<0?R.string.flow_explore:FlowRoutes.GROUPS[group],this::finish);
        LinearLayout content=SocialSurface.content(requireActivity(),root);
        if(group==5){android.view.View review=SocialViews.button(requireContext(),getString(R.string.ad_review),()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.ListingReviewActivity.class)));review.setTag("adoption_admin_review");content.addView(review);}
        if(group==2){
            content.addView(SocialViews.primaryButton(requireContext(),getString(R.string.ad_find),()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).discovery("",false)));
            content.addView(SocialSurface.quiet(requireContext(),getString(R.string.ad_new),()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.listing.ListingActivity.class))));
            content.addView(SocialSurface.quiet(requireContext(),getString(R.string.ad_my_applications),()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.MyApplicationsActivity.class))));
            content.addView(SocialSurface.quiet(requireContext(),getString(R.string.ad_owner_title),()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.listing.ListingActivity.class))));return;
        }
        if(group<0){for(int i=0;i<FlowRoutes.GROUPS.length;i++){
            final int selected=i;
            android.view.View row=SocialViews.button(requireActivity(),getString(FlowRoutes.GROUPS[i]),()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("tools:"+selected));
            row.setTag("flow_group_"+i);content.addView(row);
        }}else for(FlowRoutes.Route route:FlowRoutes.ALL)if(route.group==group){
            android.view.View row=SocialViews.button(requireActivity(),getString(route.title).replaceAll("\\s*\\(F[0-9]+\\)",""),()->open(route));
            row.setTag("flow_"+route.feature);content.addView(row);
        }
    }
    private void open(FlowRoutes.Route route){
        if(!route.contextual&&route.activity==com.gmail.manhle240406.project.adoption.DiscoveryActivity.class){((com.gmail.manhle240406.project.MainActivity)requireActivity()).discovery("",route.feature.equals("F13"));return;}
        if(!route.contextual){Intent intent=new Intent(requireActivity(),route.activity);if(route.feature.equals("F01")||route.feature.equals("F42"))intent.putExtra("account",true);if(route.feature.equals("F13"))intent.putExtra("filters",true);startActivity(intent);return;}
        int message=route.feature.equals("F05")?R.string.flow_admin_accounts_help:route.feature.equals("F15")?R.string.flow_share_unavailable:route.feature.equals("F37")?R.string.flow_recommendation_help:R.string.flow_context_required;
        dialog=new MaterialAlertDialogBuilder(requireActivity()).setTitle(route.title).setMessage(message).setNegativeButton(R.string.flow_back,null).create();
        if(route.feature.equals("F08"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.pet_title),(d,w)->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.pet.PetActivity.class)));
        if(route.feature.equals("F16"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.my_applications_title),(d,w)->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.MyApplicationsActivity.class)));
        if(route.feature.equals("F24"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.f23_title),(d,w)->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.PostAdoptionTimelineActivity.class)));
        if(route.feature.equals("F37"))dialog.setButton(android.content.DialogInterface.BUTTON_POSITIVE,getString(R.string.discovery_title),(d,w)->((com.gmail.manhle240406.project.MainActivity)requireActivity()).discovery("",false));
        dialog.show();
    }
    @Override public void onDestroyView(){if(dialog!=null)dialog.dismiss();super.onDestroyView();}
}
