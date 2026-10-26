package com.gmail.manhle240406.project.adoption;

import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableReference;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Firebase Auth context supplies identity; no client actor/role or privileged credentials. */
public final class AdoptionApi {
    static java.util.function.BiFunction<String, Map<String, Object>, Task<Map<String, Object>>>
            testTransport;

    interface EnvelopeTransport {
        Task<Map<String, Object>> call(
                String operation, Map<String, Object> input, Map<String, Object> consent);
    }

    static EnvelopeTransport testEnvelopeTransport;
    private static FirebaseFunctions runtime;

    private AdoptionApi() {}

    private static synchronized FirebaseFunctions runtime() {
        if (runtime == null) {
            runtime = FirebaseFunctions.getInstance("asia-east2");
            if (com.gmail.manhle240406.project.BuildConfig.FIREBASE_EMULATORS)
                runtime.useEmulator(
                        com.gmail.manhle240406.project.BuildConfig.FIREBASE_EMULATOR_HOST,
                        com.gmail.manhle240406.project.BuildConfig.FUNCTIONS_EMULATOR_PORT);
        }
        return runtime;
    }

    public static Task<Map<String, Object>> call(String operation, Map<String, Object> input) {
        return call(operation, input, null);
    }

    @SuppressWarnings("unchecked")
    public static Task<Map<String, Object>> call(
            String operation, Map<String, Object> input, Map<String, Object> consent) {
        if (com.gmail.manhle240406.project.BuildConfig.DEBUG && testEnvelopeTransport != null)
            return testEnvelopeTransport.call(operation, input, consent);
        if (com.gmail.manhle240406.project.BuildConfig.DEBUG && testTransport != null)
            return testTransport.apply(operation, input);
        HttpsCallableReference ref = runtime().getHttpsCallable("adoptionOperations");
        ref.setTimeout(30, TimeUnit.SECONDS);
        Map<String, Object> body = new HashMap<>();
        body.put("operation", operation);
        body.put("input", input);
        if (consent != null) body.put("participationConsent", consent);
        return ref.call(body)
                .continueWith(
                        t -> {
                            Object result = t.getResult().getData();
                            if (!(result instanceof Map))
                                throw new IllegalStateException("invalid_response");
                            return (Map<String, Object>) result;
                        });
    }
}
