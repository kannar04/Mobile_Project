package com.gmail.manhle240406.project.adoption;
import java.util.*;
/** UTC elapsed policy; display timezone/phone clock never alter authoritative dates. */
public final class FollowupTiming {
 public static final long DAY=86400000L,GRACE=48L*3600000,EXTENSION=14L*DAY;
 public static final List<Integer> OFFSETS=Collections.unmodifiableList(Arrays.asList(1,7,14,30,60,90));
 private FollowupTiming(){}
 public static long due(long baseline,int offset){if(!OFFSETS.contains(offset)||baseline<0)throw new IllegalArgumentException("task_offset");return Math.addExact(baseline,Math.multiplyExact(offset,DAY));}
 public static String state(long effectiveDue,long serverNow,Long receivedAt){if(receivedAt!=null)return "received";return serverNow<effectiveDue?"scheduled":serverNow==effectiveDue?"due":serverNow<=Math.addExact(effectiveDue,GRACE)?"within_grace":"overdue_needs_review";}
 public static void extension(long originalDue,long requestedDue,long now){if(requestedDue<=now||requestedDue>Math.addExact(originalDue,EXTENSION))throw new IllegalArgumentException("extension_bound");}
 public static boolean normalAppeal(long noticeAt,long now){return now<=Math.addExact(noticeAt,14L*DAY);}
 public static boolean editReview(long submittedAt,long windowEnd,long now,boolean revealed){return !revealed&&now<Math.min(windowEnd,Math.addExact(submittedAt,48L*3600000));}
}
