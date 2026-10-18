package com.gmail.manhle240406.project.profile;
import android.os.Bundle;
import android.content.Intent;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.media.*;
import java.util.*;

/** Bounded public projection only: no queries against another member's private workspace. */
public final class PublicProfileActivity extends AppCompatActivity {
 private LinearLayout content;private String uid,after;private boolean busy;private int pages;private android.view.View next;
 @Override public void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);uid=getIntent().getStringExtra("uid");LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.oc2_public_profile,this::finish);content=SocialSurface.content(this,root);load();}
 private void load(){if(busy||uid==null)return;busy=true;if(next!=null)content.removeView(next);TextView status=SocialSurface.muted(this,getString(R.string.profile_loading));content.addView(status);ManagedMediaClient.call("publicProfilePage",ManagedMediaClient.map("uid",uid,"after",after)).addOnCompleteListener(t->{if(isDestroyed()||isFinishing())return;busy=false;content.removeView(status);if(!t.isSuccessful()){status.setText(R.string.social_unavailable);content.addView(status);content.addView(SocialSurface.quiet(this,getString(R.string.social_retry),this::load));return;}Map<String,Object> result=t.getResult();if(pages==0){Map<String,Object> p=(Map<String,Object>)result.get("profile");ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);content.addView(avatar,new LinearLayout.LayoutParams(SocialViews.dp(this,72),SocialViews.dp(this,72)));TextView name=SocialViews.text(this,"",true);content.addView(name);AuthorIdentity.apply(p,name,avatar);content.addView(SocialSurface.muted(this,String.valueOf(p.getOrDefault("bio",""))));}if(++pages>3){while(content.getChildCount()>3)content.removeViewAt(3);pages=1;}for(Map<String,Object> post:com.gmail.manhle240406.project.adoption.AdoptionFlowModel.rows(result.get("posts"))){content.addView(SocialViews.text(this,String.valueOf(post.get("body")),false));MediaGrid photos=new MediaGrid(this);photos.bind(post.get("mediaAssetIds"));content.addView(photos);if("adoption".equals(post.get("type"))){AdoptionPostCard pet=new AdoptionPostCard(this);pet.bind(post);content.addView(pet);}content.addView(SocialSurface.quiet(this,getString(R.string.social_open),()->startActivity(new Intent(this,SocialDetailActivity.class).putExtra("post",String.valueOf(post.get("postId"))))));}after=(String)result.get("next");if(after!=null){next=SocialSurface.quiet(this,getString(R.string.social_more),this::load);content.addView(next);}});}
}
