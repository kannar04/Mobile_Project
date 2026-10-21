package com.gmail.manhle240406.project.community;

import android.widget.ImageView;
import android.widget.TextView;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.media.MediaGrid;
import java.util.Map;

/** Only a currently permitted public profile supplies another member's identity. */
public final class AuthorIdentity {
 private AuthorIdentity(){}
 public static void bind(AuthorLookup lookup,String uid,TextView name,ImageView avatar){
  String key=uid+":"+(lookup==null?"none":lookup.identityGeneration());
  Object old=name.getTag(R.id.post_author_name);
  if(old instanceof Binding&&((Binding)old).key.equals(key)&&android.os.SystemClock.elapsedRealtime()<((Binding)old).expires)return;
  Binding binding=new Binding(key);name.setTag(R.id.post_author_name,binding);boolean sameAuthor=old instanceof Binding&&((Binding)old).key.startsWith(uid+":");if(!sameAuthor)name.setText(R.string.social_member);
  avatar.setTag(new Object());if(!sameAuthor)placeholder(avatar,R.drawable.profile_avatar_default);
  avatar.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  if(lookup==null||uid==null)return;android.view.View.OnClickListener open=v->v.getContext().startActivity(new android.content.Intent(v.getContext(),com.gmail.manhle240406.project.profile.PublicProfileActivity.class).putExtra("uid",uid));name.setOnClickListener(open);avatar.setOnClickListener(open);name.setMinHeight(com.gmail.manhle240406.project.community.SocialViews.dp(name.getContext(),48));
  lookup.get(uid).addOnSuccessListener(profile->{if(name.getTag(R.id.post_author_name)!=binding)return;if(profile==null){name.setText(R.string.social_member);placeholder(avatar,R.drawable.profile_avatar_default);binding.expires=android.os.SystemClock.elapsedRealtime()+5_000;return;}apply(profile,name,avatar);});
 }
 private static final class Binding {final String key;long expires=android.os.SystemClock.elapsedRealtime()+60_000;Binding(String key){this.key=key;}}
 private static void placeholder(ImageView image,int resource){android.graphics.drawable.Drawable icon=androidx.appcompat.content.res.AppCompatResources.getDrawable(image.getContext(),resource);image.setImageTintList(null);if(icon!=null){icon=icon.mutate();androidx.core.graphics.drawable.DrawableCompat.setTint(icon,com.google.android.material.color.MaterialColors.getColor(image,com.google.android.material.R.attr.colorOnSurfaceVariant));}image.setImageDrawable(icon);}
 public static void apply(Map<String,Object> profile,TextView name,ImageView avatar){
  name.setText((String)profile.get("displayName"));avatar.setImageTintList(null);
  placeholder(avatar,"cat".equals(profile.get("avatarKey"))?R.drawable.profile_avatar_cat:"dog".equals(profile.get("avatarKey"))?R.drawable.profile_avatar_dog:R.drawable.profile_avatar_default);
  if(profile.get("avatarAssetId") instanceof String)MediaGrid.load(avatar,(String)profile.get("avatarAssetId"),"thumbnail");
 }
}
