package com.gmail.manhle240406.project.profile;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialSurface;
import com.gmail.manhle240406.project.community.SocialViews;
import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Links a real Firebase phone credential and mirrors only the verified E.164 value privately. */
public final class PhoneVerificationActivity extends AppCompatActivity {
    private String verificationId; private TextInputEditText number,code; private TextView status;
    private MaterialButton send,confirm; private SessionEpoch.Ticket ticket; private boolean committing;
    @Override protected void onCreate(Bundle state){super.onCreate(state);EdgeToEdge.enable(this);
        com.gmail.manhle240406.project.foundation.SessionGateway sessions=((AniloveApplication)getApplication()).foundation().sessions;
        ticket=sessions==null?null:sessions.capture();if(ticket==null||ticket.uid==null){finish();return;}
        LinearLayout root=SocialSurface.page(this);SocialSurface.header(this,root,R.string.lc_phone,()->getOnBackPressedDispatcher().onBackPressed());LinearLayout body=SocialSurface.content(this,root);SocialViews.privateView(root);
        body.addView(SocialViews.text(this,getString(R.string.lc_phone_consent),false));number=SocialViews.field(this,body,getString(R.string.lc_phone_hint),1);number.setInputType(InputType.TYPE_CLASS_PHONE);code=SocialViews.field(this,body,getString(R.string.lc_code),1);code.setInputType(InputType.TYPE_CLASS_NUMBER);code.setVisibility(View.GONE);
        status=SocialSurface.muted(this,"");status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        send=SocialViews.primaryButton(this,getString(R.string.lc_sms),this::sendCode);body.addView(send);confirm=SocialViews.primaryButton(this,getString(R.string.lc_confirm_code),this::confirmCode);confirm.setVisibility(View.GONE);body.addView(confirm);
    }
    private boolean current(){return ticket!=null&&((AniloveApplication)getApplication()).foundation().sessions.current(ticket)&&!isDestroyed();}
    private void sendCode(){String phone=ProfileSchema.phone(String.valueOf(number.getText()));if(!ProfileSchema.validPhone(phone)){number.setError(getString(R.string.profile_invalid_phone));return;}number.setText(phone);number.setSelection(phone.length());number.setError(null);send.setEnabled(false);status.setText(R.string.review_phone_sending);
        PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance()).setPhoneNumber(phone).setTimeout(60L,TimeUnit.SECONDS).setActivity(this).setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks(){public void onVerificationCompleted(PhoneAuthCredential credential){link(credential);}public void onVerificationFailed(FirebaseException error){if(!current())return;send.setEnabled(true);status.setText(phoneError(error));}public void onCodeSent(String id,PhoneAuthProvider.ForceResendingToken token){if(!current())return;verificationId=id;code.setVisibility(View.VISIBLE);confirm.setVisibility(View.VISIBLE);status.setText(R.string.review_phone_code_sent);code.requestFocus();}}).build());}
    private void confirmCode(){String value=String.valueOf(code.getText()).trim();if(verificationId==null||value.length()<4){code.setError(getString(R.string.lc_code));return;}link(PhoneAuthProvider.getCredential(verificationId,value));}
    private void link(PhoneAuthCredential credential){if(committing||!current())return;committing=true;send.setEnabled(false);confirm.setEnabled(false);status.setText(R.string.review_phone_verifying);FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();if(user==null){failed(null);return;}user.linkWithCredential(credential).continueWithTask(done->{if(!done.isSuccessful()&&done.getException() instanceof FirebaseAuthException&&"ERROR_PROVIDER_ALREADY_LINKED".equals(((FirebaseAuthException)done.getException()).getErrorCode()))return user.updatePhoneNumber(credential);if(!done.isSuccessful())throw done.getException();return user.reload();}).continueWithTask(done->{if(!done.isSuccessful())throw done.getException();return user.getIdToken(true);}).addOnCompleteListener(done->{if(!current())return;if(!done.isSuccessful()||user.getPhoneNumber()==null){failed(done.getException());return;}saveVerified(user.getPhoneNumber());});}
    private void saveVerified(String phone){ProfileRepository repository=new ProfileRepository(FirebaseFirestore.getInstance(),((AniloveApplication)getApplication()).foundation().sessions);repository.load(ticket).continueWithTask(read->{if(!read.isSuccessful()||read.getResult().code!=ProfileRepository.Code.OK)throw new IllegalStateException("profile_unavailable");long version=ProfileSchema.version(read.getResult().value.contact);ProfileCommand command=new ProfileCommand(ProfileCommand.Kind.CONTACT,version,UUID.randomUUID().toString(),Collections.singletonMap("phoneE164",phone));return repository.execute(ticket,command);}).addOnCompleteListener(done->{if(!current())return;if(done.isSuccessful()&&done.getResult().code==ProfileRepository.Code.OK){setResult(RESULT_OK);finish();}else failed(done.getException());});}
    private int phoneError(Throwable failure){Throwable e=failure;while(e!=null){if(e instanceof FirebaseAuthUserCollisionException)return R.string.review_phone_in_use;if(e instanceof FirebaseTooManyRequestsException)return R.string.review_phone_quota;if(e instanceof FirebaseAuthInvalidCredentialsException)return R.string.review_phone_invalid_code;e=e.getCause();}return R.string.review_phone_failed;}
    private void failed(Throwable failure){if(!current())return;committing=false;send.setEnabled(true);confirm.setEnabled(verificationId!=null);status.setText(phoneError(failure));}
}
