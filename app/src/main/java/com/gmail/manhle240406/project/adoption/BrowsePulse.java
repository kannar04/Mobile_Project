package com.gmail.manhle240406.project.adoption;
import android.os.*;import java.util.*;
/** One process timer, only while source surfaces are started; no scroll-triggered polls. */
public final class BrowsePulse {
 private static final Handler handler=new Handler(Looper.getMainLooper());private static final Set<Runnable> readers=new LinkedHashSet<>();
 private static final Runnable tick=new Runnable(){public void run(){for(Runnable r:new ArrayList<>(readers))r.run();if(!readers.isEmpty())handler.postDelayed(this,25_000);}};
 public static void add(Runnable r){if(readers.add(r)&&readers.size()==1)handler.postDelayed(tick,25_000);}
 public static void remove(Runnable r){readers.remove(r);if(readers.isEmpty())handler.removeCallbacks(tick);}
 private BrowsePulse(){}
}
