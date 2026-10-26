package com.gmail.manhle240406.project.foundation;

import com.google.android.gms.tasks.Task;

import java.util.List;

public interface FoundationRepository {
    enum ReadSource {
        SERVER,
        CACHE
    }

    final class Cursor {
        public final com.google.firebase.Timestamp createdAt;
        public final String operationId;

        public Cursor(com.google.firebase.Timestamp createdAt, String operationId) {
            this.createdAt = createdAt;
            this.operationId = operationId;
        }
    }

    Task<FoundationResult<FoundationDocuments.Config>> readConfig(ReadSource source);

    Task<FoundationResult<FoundationDocuments.Authority>> readMyAuthority();

    Task<FoundationResult<FoundationDocuments.Diagnostic>> runDiagnostic(String operationId);

    Task<FoundationResult<List<FoundationDocuments.Diagnostic>>> listMyDiagnostics(Cursor cursor);

    Task<FoundationResult<Void>> clearMyDiagnostic(String operationId);
}
