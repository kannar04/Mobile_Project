package com.gmail.manhle240406.project.community;

import java.util.*;

/**
 * A pending marker disappearing requires fresh server state, never an optimistic committed count.
 */
public final class PendingReactionTracker {
    private Set<String> previous = Collections.emptySet(),
            previousContributions = Collections.emptySet();

    public Set<String> completedPosts(List<CommunityOutbox.Entry> rows) {
        Set<String> current = new HashSet<>();
        for (CommunityOutbox.Entry e : rows)
            if ("set_like".equals(e.command.command)) current.add(e.command.postId);
        Set<String> completed = new HashSet<>(previous);
        completed.removeAll(current);
        previous = current;
        return completed;
    }

    public Set<String> completedContributions(List<CommunityOutbox.Entry> rows) {
        Set<String> current = new HashSet<>();
        for (CommunityOutbox.Entry e : rows) current.add(e.command.postId);
        Set<String> completed = new HashSet<>(previousContributions);
        completed.removeAll(current);
        previousContributions = current;
        return completed;
    }
}
