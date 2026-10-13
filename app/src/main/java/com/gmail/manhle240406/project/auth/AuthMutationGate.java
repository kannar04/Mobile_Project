package com.gmail.manhle240406.project.auth;

/** One unresolved SDK mutation at a time. Timeout does not release the lock. */
public final class AuthMutationGate {
    public enum Settlement { APPLY, RECONCILE_VISITOR, DISCARD }
    public static final class Ticket {
        public final long id;
        private final long generation;
        private Ticket(long id, long generation) { this.id = id; this.generation = generation; }
    }
    private long sequence;
    private long generation;
    private Ticket pending;
    private boolean visitor;
    public AuthMutationGate(boolean visitor) { this.visitor = visitor; }
    public synchronized Ticket begin() {
        if (pending != null) return null;
        visitor = false;
        pending = new Ticket(++sequence, ++generation);
        return pending;
    }
    public synchronized void logout() { visitor = true; generation++; }
    public synchronized boolean visitor() { return visitor; }
    public synchronized boolean busy() { return pending != null; }
    public synchronized boolean current(Ticket ticket) {
        return ticket != null && pending == ticket && generation == ticket.generation && !visitor;
    }
    public synchronized Settlement settle(Ticket ticket) {
        if (ticket == null || pending != ticket) return Settlement.DISCARD;
        boolean apply = current(ticket);
        pending = null;
        return apply ? Settlement.APPLY : Settlement.RECONCILE_VISITOR;
    }
}
