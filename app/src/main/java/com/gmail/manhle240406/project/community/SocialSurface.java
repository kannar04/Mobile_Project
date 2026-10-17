package com.gmail.manhle240406.project.community;
import android.content.Context;
import android.view.*;
import android.widget.*;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.appbar.MaterialToolbar;
import com.gmail.manhle240406.project.R;
import static com.gmail.manhle240406.project.community.SocialViews.*;
/** Presentation-only primitives for the writing and discussion surfaces. */
public final class SocialSurface {
 private SocialSurface(){}
 public static LinearLayout page(android.app.Activity a){LinearLayout root=column(a);root.setPadding(0,0,0,0);root.setBackgroundColor(MaterialColors.getColor(root,com.google.android.material.R.attr.colorSurface));install(a,root);insets(root);return root;}
 public static MaterialToolbar header(Context c,LinearLayout root,int title,Runnable back){MaterialToolbar bar=new MaterialToolbar(c);bar.setId(R.id.social_header);bar.setTitle(title);bar.setNavigationIcon(R.drawable.ic_social_back);bar.setNavigationIconTint(MaterialColors.getColor(bar,com.google.android.material.R.attr.colorOnSurface));bar.setNavigationContentDescription(R.string.auth_back);bar.setNavigationOnClickListener(v->back.run());bar.setMinimumHeight(dp(c,64));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));return bar;}
 public static LinearLayout content(Context c,LinearLayout root){ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=column(c);content.setPadding(dp(c,20),dp(c,12),dp(c,20),dp(c,24));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));return content;}
 public static void gap(LinearLayout p,int n){View space=new View(p.getContext());space.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);p.addView(space,new LinearLayout.LayoutParams(1,dp(p.getContext(),n)));}
 public static MaterialButton quiet(Context c,String label,Runnable action){MaterialButton b=new MaterialButton(c,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);b.setStrokeWidth(0);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));b.setText(label);b.setMinHeight(dp(c,48));b.setOnClickListener(v->action.run());return b;}
 public static TextView muted(Context c,String s){TextView v=text(c,s,false);v.setTextColor(MaterialColors.getColor(v,com.google.android.material.R.attr.colorOnSurfaceVariant));return v;}
 public static MaterialButton more(Context c,Runnable action){MaterialButton b=quiet(c,"",action);b.setIconResource(R.drawable.ic_social_more);b.setIconPadding(0);b.setContentDescription(c.getString(R.string.social_menu));b.setMinWidth(dp(c,48));b.setMinimumWidth(dp(c,48));b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));b.setPadding(dp(c,12),0,dp(c,12),0);return b;}
 public static void hideEmpty(TextView v){v.setVisibility(v.getText().length()==0?View.GONE:View.VISIBLE);}
}
