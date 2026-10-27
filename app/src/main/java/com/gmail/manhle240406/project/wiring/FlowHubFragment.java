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
        if(group<0){
            FlowGrid.add(grid,getString(R.string.review_manage_pets),R.drawable.ic_pet_paw_vector,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.pet.PetActivity.class)),"explore_pets");
            FlowGrid.add(grid,getString(R.string.discovery_title),R.drawable.ic_shell_search,()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).discovery("",false),"explore_discovery");
            FlowGrid.add(grid,getString(R.string.feedback_my_posts),R.drawable.ic_shell_photo,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.community.SocialComposerActivity.class).putExtra("own",true)),"explore_listings");
            FlowGrid.add(grid,getString(R.string.oc2_adoption_manager),R.drawable.ic_pet_paw_vector,()->startActivity(new Intent(requireActivity(),com.gmail.manhle240406.project.adoption.LifecycleActivity.class)),"explore_adoption_history");
            FlowGrid.add(grid,getString(R.string.flow_care),FlowGrid.groupIcon(3),()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("tools:3"),"flow_group_care");
            FlowGrid.add(grid,getString(R.string.nav_profile),R.drawable.ic_nav_profile,()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("profile"),"flow_profile");
            addOperatorEntry(grid);
        }else for(FlowRoutes.Route route:FlowRoutes.ALL)if(route.group==group&&!hiddenShortcut(route.feature)){
            FlowGrid.add(grid,getString(route.title).replaceAll("\\s*\\(F[0-9]+\\)",""),FlowGrid.featureIcon(route.feature,route.group),()->open(route),"flow_"+route.feature);
        }
    }
    static boolean operatorAccess(java.util.Map<String,Object> row){if(row==null||!Boolean.TRUE.equals(row.get("active"))||!Long.valueOf(1).equals(row.get("schemaVersion"))||!(row.get("capabilities") instanceof java.util.List))return false;java.util.List<?> values=(java.util.List<?>)row.get("capabilities");for(String capability:new String[]{"course_content_moderation","report_review","account_lookup","account_restrict","adoption_lifecycle"})if(values.contains(capability))return true;return false;}
    private void addOperatorEntry(GridLayout grid){if(com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser()==null)return;com.gmail.manhle240406.project.adoption.AdoptionApi.call("flowRead",com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map("kind","operatorAccess")).addOnSuccessListener(row->{if(!isAdded()||getView()==null||!Boolean.TRUE.equals(row.get("allowed"))||grid.findViewWithTag("flow_group_operator")!=null)return;FlowGrid.add(grid,getString(FlowRoutes.GROUPS[5]),FlowGrid.groupIcon(5),()->((com.gmail.manhle240406.project.MainActivity)requireActivity()).navigate("tools:5"),"flow_group_operator");});}
    private boolean hiddenShortcut(String feature){return java.util.Arrays.asList("F12","F13","F14","F15","F16","F17","F18","F19","F20","F21","F22","F28","F32","F34","F35","F37","F41").contains(feature);}
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
