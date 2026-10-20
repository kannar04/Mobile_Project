package com.gmail.manhle240406.project.profile;

import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.*;

/** Honest F43 entry/preflight availability. No provider deletion without managed retention inventory. */
public final class AccountDeletionActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);
        LinearLayout root=SocialSurface.page(this);SocialViews.privateView(root);
        SocialSurface.header(this,root,R.string.flow_delete_account,this::finish);
        LinearLayout content=SocialSurface.content(this,root);
        content.addView(SocialViews.text(this,getString(R.string.flow_delete_help),false));
        TextView status=SocialSurface.muted(this,"");status.setId(R.id.flow_status);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        android.view.View check=SocialViews.button(this,getString(R.string.flow_check_availability),()->com.gmail.manhle240406.project.adoption.AdoptionApi.call("flowRead",com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map("kind","obligations")).addOnCompleteListener(t->{if(!isDestroyed())status.setText(t.isSuccessful()&&Boolean.TRUE.equals(t.getResult().get("blocked"))?R.string.lc_delete_blocked:R.string.flow_delete_unavailable);}));check.setId(R.id.flow_submit);content.addView(check);content.addView(status);
        content.addView(SocialViews.primaryButton(this,getString(R.string.flow_keep_account),this::finish));
    }
}
