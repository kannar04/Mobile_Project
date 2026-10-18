package com.gmail.manhle240406.project.adoption;
import com.google.android.gms.tasks.*;import com.google.firebase.functions.*;import java.util.*;import java.util.concurrent.TimeUnit;
/** Firebase Auth context supplies identity; no client actor/role or privileged credentials. */
public final class AdoptionApi {
 static java.util.function.BiFunction<String,Map<String,Object>,Task<Map<String,Object>>> testTransport;
 private AdoptionApi(){}
 @SuppressWarnings("unchecked") public static Task<Map<String,Object>> call(String operation,Map<String,Object> input){if(com.gmail.manhle240406.project.BuildConfig.DEBUG&&testTransport!=null)return testTransport.apply(operation,input);HttpsCallableReference ref=FirebaseFunctions.getInstance("asia-east2").getHttpsCallable("adoptionOperations");ref.setTimeout(15,TimeUnit.SECONDS);Map<String,Object> body=new HashMap<>();body.put("operation",operation);body.put("input",input);return ref.call(body).continueWith(t->{Object result=t.getResult().getData();if(!(result instanceof Map))throw new IllegalStateException("invalid_response");return (Map<String,Object>)result;});}
}
