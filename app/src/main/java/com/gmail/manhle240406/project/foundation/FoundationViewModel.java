package com.gmail.manhle240406.project.foundation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.UUID;

/** Diagnostic state for approved consumers/tests. No automatic writes on recreation. */
public final class FoundationViewModel extends ViewModel {
    private final SavedStateHandle saved;
    private final MutableLiveData<FoundationResult<FoundationDocuments.Diagnostic>> result =
            new MutableLiveData<>();
    private SessionGateway sessions;
    private boolean pending;
    private long generation;
    private final Runnable reset;

    public FoundationViewModel(SavedStateHandle saved) {
        this.saved = saved;
        reset =
                () -> {
                    generation++;
                    pending = false;
                    saved.remove("operationId");
                    saved.remove("operationUid");
                    result.setValue(
                            FoundationResult.error(FoundationResult.Status.SESSION_CHANGED));
                };
    }

    public LiveData<FoundationResult<FoundationDocuments.Diagnostic>> result() {
        return result;
    }

    public String operationId() {
        String id = saved.get("operationId");
        if (id == null) {
            id = UUID.randomUUID().toString();
            saved.set("operationId", id);
        }
        return id;
    }

    public void run(FirebaseEnvironment environment) {
        if (pending) return;
        if (environment.repository == null) {
            result.setValue(FoundationResult.error(FoundationResult.Status.CONFIGURATION_ERROR));
            return;
        }
        if (sessions == null) {
            sessions = environment.sessions;
            String previousUid = saved.get("operationUid");
            String currentUid = sessions.capture().uid;
            if (!java.util.Objects.equals(previousUid, currentUid)) reset.run();
            saved.set("operationUid", currentUid);
            sessions.onInvalidated(reset);
        }
        pending = true;
        result.setValue(FoundationResult.error(FoundationResult.Status.PENDING));
        long expected = generation;
        environment
                .repository
                .runDiagnostic(operationId())
                .addOnCompleteListener(
                        task -> {
                            if (expected != generation) return;
                            pending = false;
                            result.setValue(
                                    task.isSuccessful()
                                            ? task.getResult()
                                            : FoundationResult.error(
                                                    FoundationResult.Status.CHECKING_OUTCOME));
                        });
    }

    @Override
    protected void onCleared() {
        generation++;
        if (sessions != null) sessions.removeInvalidation(reset);
        saved.remove("operationId");
        saved.remove("operationUid");
        result.setValue(null);
    }
}
