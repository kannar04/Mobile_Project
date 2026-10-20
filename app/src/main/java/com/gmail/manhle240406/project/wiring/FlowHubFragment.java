package com.gmail.manhle240406.project.wiring;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.GridLayout;
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
        GridLayout grid=FlowGrid.create(requireContext());content.addView(grid,new LinearLayout.LayoutParams(-1,-2));
        if(group==5)FlowGrid.add(grid,getString(R.string.ad_review),R.drawable.ic_shell_search,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.ListingReviewActivity.class)),"adoption_admin_review");
        if(group<0){
            FlowGrid.add(grid,getString(R.string.pet_title),R.drawable.ic_pet_paw_vector,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.pet.PetActivity.class)),"explore_pets");
            FlowGrid.add(grid,getString(R.string.feedback_my_posts),R.drawable.ic_shell_photo,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.community.SocialComposerActivity.class).putExtra("own",true)),"explore_listings");
            FlowGrid.add(grid,getString(R.string.oc2_adoption_manager),R.drawable.ic_pet_paw_vector,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.LifecycleActivity.class)),"explore_adoption_history");
            for(int i=0;i<FlowRoutes.GROUPS.length;i++){
            final int selected=i;
            FlowGrid.add(grid,getString(FlowRoutes.GROUPS[i]),FlowGrid.groupIcon(i),()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("tools:"+selected),"flow_group_"+i);
        }}else for(FlowRoutes.Route route:FlowRoutes.ALL)if(route.group==group){
            FlowGrid.add(grid,getString(route.title).replaceAll("\\s*\\(F[0-9]+\\)",""),FlowGrid.featureIcon(route.feature,route.group),()->open(route),"flow_"+route.feature);
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
