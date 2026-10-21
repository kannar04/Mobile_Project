package com.gmail.manhle240406.project.auth;

import android.os.Bundle;
import android.text.InputType;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.*;
import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.security.AccountSecurityRepository;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;

/** Credentials never enter saved state, view models, logs or retries. */
public final class PasswordChangeActivity extends AppCompatActivity {
    public static final class Operation extends AndroidViewModel {
        final MutableLiveData<Boolean> busy=new MutableLiveData<>(false);
        final MutableLiveData<AccountSecurityRepository.Result> result=new MutableLiveData<>();
        public Operation(android.app.Application a){super(a);}
        void send(String current,String next,String confirm){
            if(Boolean.TRUE.equals(busy.getValue()))return;
            AccountSecurityRepository repo=((AniloveApplication)getApplication()).services().security;
            if(repo==null){result.setValue(AccountSecurityRepository.Result.SIGN_IN_REQUIRED);return;}
            result.setValue(null);busy.setValue(true);
            repo.changePassword(current,next,confirm).whenComplete((r,e)->{
                result.postValue(e==null?r:AccountSecurityRepository.Result.OUTCOME_UNKNOWN);busy.postValue(false);
            });
        }
    }
    @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);
        LinearLayout root=SocialSurface.page(this);SocialViews.privateView(root);
        SocialSurface.header(this,root,R.string.flow_change_password,this::finish);
        LinearLayout content=SocialSurface.content(this,root);
        content.addView(SocialSurface.muted(this,getString(R.string.flow_password_help)));
        TextInputEditText current=password(content,R.string.flow_current_password,R.id.flow_current_password);
        TextInputEditText next=password(content,R.string.flow_new_password,R.id.flow_new_password);
        TextInputEditText confirm=password(content,R.string.flow_confirm_password,R.id.flow_confirm_password);
        TextView status=SocialSurface.muted(this,"");status.setId(R.id.flow_status);status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(status);
        Operation op=new ViewModelProvider(this,new ViewModelProvider.AndroidViewModelFactory(getApplication())).get(Operation.class);
        MaterialButton send=SocialViews.primaryButton(this,getString(R.string.flow_change_password),()->{
            String c=current.getText().toString(),n=next.getText().toString(),check=confirm.getText().toString();
            if(c.isEmpty()||n.isEmpty()||!n.equals(check)){confirm.setError(getString(R.string.flow_password_invalid));confirm.requestFocus();return;}
            op.send(c,n,check);current.setText("");next.setText("");confirm.setText("");
        });send.setId(R.id.flow_submit);content.addView(send);
        content.addView(SocialSurface.quiet(this,getString(R.string.wiring_reset_title),()->startActivity(new android.content.Intent(this,PasswordRecoveryActivity.class))));
        op.busy.observe(this,b->{send.setEnabled(!Boolean.TRUE.equals(b));current.setEnabled(!Boolean.TRUE.equals(b));next.setEnabled(!Boolean.TRUE.equals(b));confirm.setEnabled(!Boolean.TRUE.equals(b));});
        op.result.observe(this,r->{if(r==null)return;int message;
            switch(r){case PROVIDER_MANAGED:message=R.string.flow_password_provider;break;
                case INVALID_INPUT:message=R.string.flow_password_invalid;break;
                case SIGN_IN_REQUIRED:case OUTCOME_UNKNOWN:case CLEANUP_REQUIRED:message=R.string.flow_password_sign_in;break;
                default:message=R.string.flow_password_failed;}
            status.setText(message);
            if(r==AccountSecurityRepository.Result.SIGN_IN_REQUIRED||r==AccountSecurityRepository.Result.OUTCOME_UNKNOWN||r==AccountSecurityRepository.Result.CLEANUP_REQUIRED)
                content.addView(SocialViews.button(this,getString(R.string.auth_signin),()->startActivity(new android.content.Intent(this,AuthActivity.class))));
        });
    }
    private TextInputEditText password(LinearLayout p,int label,int id){TextInputEditText v=SocialViews.field(this,p,getString(label),1);v.setId(id);v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);v.setSaveEnabled(false);v.setSaveFromParentEnabled(false);v.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);return v;}
}
