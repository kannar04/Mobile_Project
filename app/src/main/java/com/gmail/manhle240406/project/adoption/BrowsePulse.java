package com.gmail.manhle240406.project.adoption;
import java.util.*;
/** Process-local invalidation bus. Sources refresh on entry and on an explicit committed change. */
public final class BrowsePulse {
 private static final Set<Runnable> readers=new LinkedHashSet<>();
 public static synchronized void add(Runnable r){readers.add(r);}
 public static synchronized void remove(Runnable r){readers.remove(r);}
 public static void invalidate(){List<Runnable> copy; synchronized(BrowsePulse.class){copy=new ArrayList<>(readers);}for(Runnable r:copy)r.run();}
 private BrowsePulse(){}
}
