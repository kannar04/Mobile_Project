package com.gmail.manhle240406.project.foundation;

import java.util.Objects;

/** Pure session guard: A -> B -> A still invalidates every earlier A request. */
public final class SessionEpoch {
  public static final class Ticket {
    public final String uid;
    private final long generation;

    private Ticket(String uid, long generation) {
      this.uid = uid;
      this.generation = generation;
    }
  }

  private String uid;
  private long generation;

  public synchronized void change(String nextUid) {
    if (!Objects.equals(uid, nextUid)) {
      uid = nextUid;
      generation++;
    }
  }

  public synchronized void invalidate() {
    generation++;
  }

  public synchronized Ticket capture() {
    return new Ticket(uid, generation);
  }

  public synchronized boolean current(Ticket ticket) {
    return ticket.generation == generation && Objects.equals(ticket.uid, uid);
  }
}
