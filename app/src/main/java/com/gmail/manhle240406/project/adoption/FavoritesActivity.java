package com.gmail.manhle240406.project.adoption;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialSurface;
import static com.gmail.manhle240406.project.community.SocialViews.*;
/** Saved-list presentation. No synthetic row is presented as account data. */
public class FavoritesActivity extends AppCompatActivity {
 @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.favorites_title,this::finish);LinearLayout content=SocialSurface.content(this,root);TextView subtitle=SocialSurface.muted(this,getString(R.string.ui_saved_subtitle));content.addView(subtitle);SocialSurface.gap(content,40);ImageView heart=new ImageView(this);heart.setImageResource(R.drawable.ic_nav_favorite);heart.setImageTintList(android.content.res.ColorStateList.valueOf(com.google.android.material.color.MaterialColors.getColor(heart,androidx.appcompat.R.attr.colorPrimary)));heart.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(this,56),dp(this,56));image.gravity=android.view.Gravity.CENTER_HORIZONTAL;content.addView(heart,image);SocialSurface.gap(content,24);TextView title=text(this,getString(R.string.ui_saved_unconnected),true);title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);title.setGravity(android.view.Gravity.CENTER);content.addView(title);TextView help=SocialSurface.muted(this,getString(R.string.ui_saved_help));help.setGravity(android.view.Gravity.CENTER);content.addView(help);SocialSurface.gap(content,24);content.addView(SocialSurface.muted(this,getString(R.string.ui_saved_private)));privateView(root);}
}
