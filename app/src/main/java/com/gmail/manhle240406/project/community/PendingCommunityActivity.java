package com.gmail.manhle240406.project.community;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.gmail.manhle240406.project.*;
public final class PendingCommunityActivity extends AppCompatActivity {
 @Override public void onCreate(Bundle b){super.onCreate(b);androidx.activity.EdgeToEdge.enable(this);LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.community_pending_actions,this::finish);LinearLayout content=SocialSurface.content(this,root);CommunityOutbox queue=((AniloveApplication)getApplication()).communityOutbox();queue.entries().observe(this,rows->{content.removeAllViews();for(CommunityOutbox.Entry row:rows){String body=(String)row.command.input.get("body");android.widget.TextView candidate=SocialViews.text(this,body==null?getString(Boolean.TRUE.equals(row.command.input.get("active"))?R.string.social_like_action:R.string.social_unlike):body,false);candidate.setTextIsSelectable(true);content.addView(candidate);content.addView(SocialSurface.muted(this,getString(row.review()?R.string.community_review:R.string.community_pending)));if(!"sending".equals(row.state)&&!"outcome_unknown".equals(row.state))content.addView(SocialViews.button(this,getString(R.string.community_cancel_pending),()->queue.cancel(row.command.operationId)));}});queue.refresh();}
}
