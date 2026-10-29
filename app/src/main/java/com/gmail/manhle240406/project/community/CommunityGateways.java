package com.gmail.manhle240406.project.community;

/**
 * Integration contracts stay unavailable until exact F29/F39 approvals and implementations exist.
 */
public final class CommunityGateways {
    public enum State {
        UNAVAILABLE
    }

    public static final class Target {
        public final String postId;
        public final long contentVersion;

        public Target(String post, long v) {
            if (!com.gmail.manhle240406.project.pet.PetSchema.id(post) || v < 1)
                throw new IllegalArgumentException();
            postId = post;
            contentVersion = v;
        }
    }

    public interface CommunityReportGateway {
        State state();
    }

    public interface CommunityReviewGateway {
        State state();
    }

    public static final CommunityReportGateway REPORT = () -> State.UNAVAILABLE;
    public static final CommunityReviewGateway REVIEW = () -> State.UNAVAILABLE;

    public static boolean publicLoopReady() {
        return false;
    }

    private CommunityGateways() {}
}
