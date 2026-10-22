package com.gmail.manhle240406.project.community;
import android.os.Bundle;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.*;
import androidx.recyclerview.widget.*;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.auth.AuthActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;

/** Guest reader uses only the exact minimal Everyone projection. No member/profile/liker queries. */
public final class GuestStoriesActivity extends AppCompatActivity {
 public static final class Window extends ViewModel {
  final List<DocumentSnapshot> rows=new ArrayList<>();final MutableLiveData<Long> changes=new MutableLiveData<>(0L);
  DocumentSnapshot cursor;boolean busy,end,failed,paused;long generation,event;
  void changed(){changes.setValue(++event);}
  void load(boolean refresh){if(busy&&!refresh||end&&!refresh)return;if(refresh){generation++;cursor=null;end=false;}busy=true;failed=false;changed();long g=generation;
   FirebaseFirestore db=FirebaseFirestore.getInstance();db.document("system/communityPublicPolicy").get(Source.SERVER).addOnCompleteListener(policy->{if(g!=generation)return;if(!policy.isSuccessful()||!Boolean.TRUE.equals(policy.getResult().get("enabled"))){busy=false;paused=true;changed();return;}paused=false;
    Query q=db.collection("communityPublicFeed").whereIn("schemaVersion",Arrays.asList(4L,5L)).whereEqualTo("audience","everyone").whereEqualTo("state","visible").orderBy("firstPublishedAt",Query.Direction.DESCENDING).orderBy(FieldPath.documentId(),Query.Direction.DESCENDING).limit(20);if(cursor!=null)q=q.startAfter(cursor);
    q.get(Source.SERVER).addOnCompleteListener(done->{if(g!=generation)return;busy=false;if(!done.isSuccessful()){failed=true;changed();return;}if(refresh)rows.clear();List<DocumentSnapshot> page=done.getResult().getDocuments();for(DocumentSnapshot d:page)if(PublicStorySchema.valid(d.getData(),d.getId())&&rows.stream().noneMatch(old->old.getId().equals(d.getId())))rows.add(d);while(rows.size()>100)rows.remove(0);cursor=page.isEmpty()?cursor:page.get(page.size()-1);end=page.size()<20;changed();});
   });
  }
  @Override protected void onCleared(){generation++;}
 }
 private Window model;private RecyclerView list;private Adapter adapter;private LinearLayoutManager manager;
 @Override public void onCreate(Bundle b){super.onCreate(b);androidx.activity.EdgeToEdge.enable(this);LinearLayout root=SocialSurface.page(this);com.google.android.material.appbar.MaterialToolbar bar=SocialSurface.header(this,root,R.string.community_public_title,this::finish);
  bar.getMenu().add(0,1,0,R.string.social_refresh);bar.getMenu().add(0,2,1,R.string.ui_write_post);bar.setOnMenuItemClickListener(item->{if(item.getItemId()==1)model.load(true);else startActivity(new Intent(this,FirebaseAuth.getInstance().getCurrentUser()==null?AuthActivity.class:SocialComposerActivity.class).putExtra("account",true));return true;});
  list=new RecyclerView(this);list.setId(R.id.public_feed_list);manager=new LinearLayoutManager(this);list.setLayoutManager(manager);list.setItemAnimator(null);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));model=new ViewModelProvider(this).get(Window.class);adapter=new Adapter();list.setAdapter(adapter);model.changes.observe(this,n->adapter.notifyDataSetChanged());list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView v,int x,int y){if(y>0&&manager.findLastVisibleItemPosition()>=model.rows.size()-5)model.load(false);}});if(model.rows.isEmpty())model.load(false);SocialViews.privateView(root);
 }
 private final class Adapter extends RecyclerView.Adapter<Holder>{public int getItemCount(){return model.rows.size()+1;}public Holder onCreateViewHolder(ViewGroup parent,int type){LinearLayout col=SocialViews.column(parent.getContext());col.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(col);}public void onBindViewHolder(Holder h,int position){h.col.removeAllViews();if(position==model.rows.size()){int label=model.busy?R.string.social_loading:model.paused?R.string.guest_paused:model.failed?R.string.social_retry:model.rows.isEmpty()?R.string.guest_empty:R.string.social_end;h.col.addView(SocialSurface.muted(GuestStoriesActivity.this,getString(label)));if(model.failed||model.paused)h.col.addView(SocialViews.button(GuestStoriesActivity.this,getString(R.string.social_retry),()->model.load(true)));return;}
   DocumentSnapshot row=model.rows.get(position);h.col.addView(SocialSurface.muted(GuestStoriesActivity.this,getString(R.string.community_everyone)));h.col.addView(SocialViews.text(GuestStoriesActivity.this,row.getString("body"),false));com.gmail.manhle240406.project.media.MediaGrid gallery=new com.gmail.manhle240406.project.media.MediaGrid(GuestStoriesActivity.this);gallery.bind(row.get("mediaAssetIds"));h.col.addView(gallery);String art=row.getString("bundledIllustrationKey");if(art!=null){ImageView image=new ImageView(GuestStoriesActivity.this);image.setImageResource("cat".equals(art)?R.drawable.profile_avatar_cat:"dog".equals(art)?R.drawable.profile_avatar_dog:R.drawable.profile_avatar_default);image.setContentDescription(getString(R.string.social_illustration));h.col.addView(image,new LinearLayout.LayoutParams(-1,SocialViews.dp(GuestStoriesActivity.this,140)));}
   h.col.addView(SocialSurface.quiet(GuestStoriesActivity.this,getString(R.string.ui_comments),()->startActivity(new Intent(GuestStoriesActivity.this,FirebaseAuth.getInstance().getCurrentUser()==null?AuthActivity.class:SocialDetailActivity.class).putExtra("account",true).putExtra("post",row.getId()))));if(com.gmail.manhle240406.project.BuildConfig.DEBUG)h.col.addView(SocialSurface.quiet(GuestStoriesActivity.this,getString(R.string.flow_share),()->DemoSharing.open(GuestStoriesActivity.this,"p",row.getId())));
  }}
 private static final class Holder extends RecyclerView.ViewHolder{final LinearLayout col;Holder(LinearLayout col){super(col);this.col=col;}}
}
