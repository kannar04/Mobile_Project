package com.gmail.manhle240406.project.security;

import java.time.Instant;
import java.time.Duration;
import java.util.*;

/** F43 pure planning/verification kernel, never an Auth-delete or recursive data-delete executor. */
public final class ClosurePolicy {
    private ClosurePolicy() {}
    public enum Phase { RECEIVED_NEEDS_INVENTORY, CLOSING, INVENTORY_OR_CASE_REVIEW,
        PUBLIC_REMOVAL_IN_PROGRESS, ERASING, PROVIDER_DELETE_PENDING, COMPLETED_SCOPE, NEEDS_OPERATOR }
    public enum DataClass { PUBLIC_ACCESS, DISPOSABLE_PERSONAL, PROVIDER, LINEAGE, CASE_EVIDENCE, CLOSED_SUPPORT, AUDIT }
    public enum Action { REVOKE, PURGE_IF_UNHELD, MINIMIZE_RESTRICTED, MANAGED_PROVIDER_DELETE, RETAIN_DEDUP }
    public static final class CoursePolicy {
        public final String operator; public final long version; public final Instant assessmentEndAt,purgeAt;
        public CoursePolicy(String operator,long version,Instant end,Instant purge) {
            if(operator==null || operator.isBlank() || version<1 || end==null || purge==null || purge.isBefore(end))
                throw new IllegalArgumentException("course_retention_configuration_required");
            this.operator=operator; this.version=version; assessmentEndAt=end; purgeAt=purge;
        }
    }
    public static final class Source {
        public final String key; public final long schemaVersion,generation; public final DataClass dataClass;
        public final boolean adapted,held; public final int count;
        public Source(String key,long schema,long generation,DataClass kind,boolean adapted,boolean held,int count) {
            if(key==null || !key.matches("[a-z][a-z0-9_]{0,63}") || schema<1 || generation<1 || kind==null || count<0)
                throw new IllegalArgumentException("source_manifest");
            this.key=key; schemaVersion=schema; this.generation=generation; dataClass=kind;
            this.adapted=adapted; this.held=held; this.count=count;
        }
        public Action action() {
            if(dataClass==DataClass.PUBLIC_ACCESS) return Action.REVOKE;
            if(held || dataClass==DataClass.LINEAGE || dataClass==DataClass.CASE_EVIDENCE) return Action.MINIMIZE_RESTRICTED;
            if(dataClass==DataClass.PROVIDER) return Action.MANAGED_PROVIDER_DELETE;
            if(dataClass==DataClass.AUDIT) return Action.RETAIN_DEDUP;
            return Action.PURGE_IF_UNHELD;
        }
    }
    public static final class Manifest {
        public final long version; public final boolean completeInventory; public final List<Source> sources;
        public Manifest(long version,boolean complete,List<Source> sources) {
            if(version<1 || sources==null || sources.size()>100) throw new IllegalArgumentException("manifest");
            Set<String> seen=new HashSet<>();
            for(Source s:sources) if(s==null || !seen.add(s.key)) throw new IllegalArgumentException("duplicate_source");
            this.version=version; completeInventory=complete;
            this.sources=Collections.unmodifiableList(new ArrayList<>(sources));
        }
        public boolean executable() { return completeInventory && !sources.isEmpty() && sources.stream().allMatch(s->s.adapted); }
        public Phase initialPhase() { return executable()?Phase.CLOSING:Phase.RECEIVED_NEEDS_INVENTORY; }
    }
    public static void requireRecentAuth(Instant verifiedAuthTime,Instant serverNow) {
        if(verifiedAuthTime==null || verifiedAuthTime.isAfter(serverNow)
                || Duration.between(verifiedAuthTime,serverNow).compareTo(Duration.ofMinutes(5))>0)
            throw new IllegalStateException("recent_same_uid_auth_required");
    }
    public static Instant nextHoldReview(Instant now) { return now.plus(Duration.ofDays(7)); }
    public static Instant terminalRetentionReview(Instant closedAt,CoursePolicy policy) {
        Instant due=closedAt.plus(Duration.ofDays(30)); return due.isBefore(policy.purgeAt)?due:policy.purgeAt;
    }
    public static void verifyStep(Source source,long observedGeneration,int batchSize,int attempts) {
        if(!source.adapted || observedGeneration!=source.generation || batchSize<1 || batchSize>20 || attempts<0 || attempts>=5)
            throw new IllegalStateException("step_requires_operator_or_current_generation");
    }
    /** Completion witness is from source adapters, not caller-provided Boolean flags or Auth absence alone. */
    public static boolean complete(Manifest manifest,Map<String,Long> verifiedGenerations,
                                   boolean providerAbsent,boolean localCleanupAcknowledged) {
        if(!manifest.executable() || !providerAbsent || !localCleanupAcknowledged
                || verifiedGenerations.size()!=manifest.sources.size()) return false;
        for(Source s:manifest.sources) if(!Objects.equals(verifiedGenerations.get(s.key),s.generation)) return false;
        return true;
    }
}
