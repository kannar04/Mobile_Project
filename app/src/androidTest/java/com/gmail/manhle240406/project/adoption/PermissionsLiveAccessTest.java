package com.gmail.manhle240406.project.adoption;
import androidx.test.ext.junit.runners.AndroidJUnit4;import com.google.android.gms.tasks.Tasks;import com.google.firebase.auth.FirebaseAuth;import org.junit.Test;import org.junit.runner.RunWith;import java.util.*;import java.util.concurrent.*;import static org.junit.Assert.*;import static com.gmail.manhle240406.project.adoption.AdoptionFlowModel.map;
/** Read-only deployed access checks; never selects an identity or writes real user content. */
@RunWith(AndroidJUnit4.class) public final class PermissionsLiveAccessTest {
 @Test public void deployedRecipientHistoryAndScopedAdminAccessUseExistingGoogleIdentity()throws Exception{
  String uid=FirebaseAuth.getInstance().getUid();assertNotNull(uid);assertNull(AdoptionApi.testTransport);assertNull(AdoptionApi.testEnvelopeTransport);
  for(String kind:Arrays.asList("content","account")){Map<String,Object> r=Tasks.await(AdoptionApi.call("courseNotices",map("kind",kind,"after",null)),35,TimeUnit.SECONDS);assertTrue(r.get("rows") instanceof List);assertTrue(((List<?>)r.get("rows")).size()<=20);assertTrue(r.containsKey("next"));}
  boolean owner="nguyenminhthucmine@gmail.com".equalsIgnoreCase(FirebaseAuth.getInstance().getCurrentUser().getEmail());
  try{Map<String,Object> r=Tasks.await(AdoptionApi.call("courseAdminRead",map("mode","queue","kind","post","parentId",null,"after",null)),35,TimeUnit.SECONDS);assertTrue("Only the approved owner has broad course moderation",owner);assertTrue(r.get("rows") instanceof List);}catch(ExecutionException e){if(owner)throw e;Throwable x=e;boolean denied=false;while(x!=null){if(x.getMessage()!=null&&(x.getMessage().contains("capability_or_conflict")||x.getMessage().contains("protected_account_ineligible")))denied=true;x=x.getCause();}assertTrue("Ordinary account denied by the deployed capability boundary",denied);}
  assertEquals(uid,FirebaseAuth.getInstance().getUid());
 }
}
