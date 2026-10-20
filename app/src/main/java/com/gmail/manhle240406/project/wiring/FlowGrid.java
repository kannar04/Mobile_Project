package com.gmail.manhle240406.project.wiring;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;
import androidx.core.widget.ImageViewCompat;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

/** Compact two-column task grid shared by both tool-hub entry points. */
final class FlowGrid {
    private FlowGrid() {}
    static GridLayout create(Context c){GridLayout grid=new GridLayout(c);grid.setColumnCount(2);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);grid.setUseDefaultMargins(false);grid.setColumnOrderPreserved(true);return grid;}
    static View add(GridLayout grid,String title,int icon,Runnable action,String tag){
        Context c=grid.getContext();MaterialCardView card=new MaterialCardView(c);card.setTag(tag);card.setClickable(true);card.setFocusable(true);card.setContentDescription(title);card.setRadius(SocialViews.dp(c,16));card.setCardElevation(SocialViews.dp(c,1));card.setStrokeWidth(SocialViews.dp(c,1));card.setStrokeColor(MaterialColors.getColor(card,com.google.android.material.R.attr.colorOutlineVariant));card.setCardBackgroundColor(MaterialColors.getColor(card,com.google.android.material.R.attr.colorSurface));card.setOnClickListener(v->action.run());
        LinearLayout content=new LinearLayout(c);content.setOrientation(LinearLayout.VERTICAL);content.setGravity(Gravity.START);content.setPadding(SocialViews.dp(c,16),SocialViews.dp(c,18),SocialViews.dp(c,16),SocialViews.dp(c,14));
        ImageView image=new ImageView(c);image.setImageResource(icon);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);ImageViewCompat.setImageTintList(image,ColorStateList.valueOf(MaterialColors.getColor(card,androidx.appcompat.R.attr.colorPrimary)));content.addView(image,new LinearLayout.LayoutParams(SocialViews.dp(c,36),SocialViews.dp(c,36)));
        TextView label=SocialViews.text(c,title,false);label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);label.setMaxLines(3);label.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(label,new LinearLayout.LayoutParams(-1,0,1));card.addView(content);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));lp.width=0;lp.height=SocialViews.dp(c,148);int gap=SocialViews.dp(c,6);lp.setMargins(gap,gap,gap,gap);grid.addView(card,lp);return card;
    }
    static int groupIcon(int group){return new int[]{R.drawable.ic_shell_search,R.drawable.ic_nav_chat,R.drawable.ic_pet_paw_vector,R.drawable.ic_nav_favorite,R.drawable.ic_nav_profile,R.drawable.ic_shell_settings}[Math.max(0,Math.min(5,group))];}
    static int featureIcon(String feature,int group){
        switch(feature){
            case "F16":return R.drawable.ic_placement_questionnaire;
            case "F17":return R.drawable.ic_placement_my_requests;
            case "F18":return R.drawable.ic_placement_review_requests;
            case "F20":return R.drawable.ic_placement_meeting;
            case "F21":return R.drawable.ic_placement_handover;
            case "F28":return R.drawable.ic_placement_review;
            case "F34":return R.drawable.ic_placement_exchange;
            case "F08":return R.drawable.ic_care_health;
            case "F23":return R.drawable.ic_care_followup;
            case "F24":return R.drawable.ic_care_extend;
            case "F25":return R.drawable.ic_care_welfare;
            case "F26":return R.drawable.ic_care_return;
            case "F27":return R.drawable.ic_care_appeal;
            case "F33":return R.drawable.ic_care_foster;
            case "F36":return R.drawable.ic_care_reminder;
            case "F44":return R.drawable.ic_care_support;
            default:break;
        }
        if(feature.equals("F09")||feature.equals("F07"))return R.drawable.ic_shell_photo;if(feature.equals("F14"))return R.drawable.ic_flow_favorite_outline;if(feature.equals("F15"))return R.drawable.ic_social_share;if(feature.equals("F19"))return R.drawable.ic_nav_chat;if(feature.equals("F30"))return R.drawable.ic_flow_notifications;if(feature.equals("F38")||feature.equals("F39")||feature.equals("F40")||feature.equals("F41"))return R.drawable.ic_shell_settings;return groupIcon(group);
    }
}
