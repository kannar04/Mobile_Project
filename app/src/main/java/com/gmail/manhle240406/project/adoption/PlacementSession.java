package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.community.FeedWindow;
import com.gmail.manhle240406.project.foundation.SessionGateway;
import com.gmail.manhle240406.project.listing.ListingSchema;
import java.util.*;
/** UID/query generation, memory-only drafts and stable reading anchors for future wiring. */
public final class PlacementSession<T> implements AutoCloseable {
 private final FeedWindow<T> window;private final FeedWindow.Identity<T> identity;private final SessionGateway sessions;private final Runnable invalidation=this::clear;
 private String uid,signature,anchor;private long generation;private int offset;private boolean bottom=true;private Map<String,Object> draft=Collections.emptyMap();private final Set<String> pending=new HashSet<>();private final List<T> newHead=new ArrayList<>();
 public PlacementSession(FeedWindow.Identity<T> identity){this(identity,null);}
 public PlacementSession(FeedWindow.Identity<T> identity,SessionGateway sessions){this.identity=identity;window=new FeedWindow<>(identity);this.sessions=sessions;if(sessions!=null)sessions.onInvalidated(invalidation);}
 public synchronized long apply(String owner,String querySignature){if(owner==null||querySignature==null)throw new IllegalArgumentException("session_query");if(!Objects.equals(uid,owner)){clear();uid=owner;}if(!Objects.equals(signature,querySignature)){signature=querySignature;generation++;pending.clear();window.clear();newHead.clear();anchor=null;offset=0;bottom=true;}return generation;}
 public synchronized boolean begin(long token,String cursor){return uid!=null&&token==generation&&pending.add(cursor==null?"head":cursor);}
 public synchronized boolean accept(long token,String cursor,List<T> page,boolean prepend){if(uid==null||token!=generation||!pending.remove(cursor==null?"head":cursor))return false;if(page.size()>20)throw new IllegalArgumentException("page_limit");window.append(page,prepend);repair();return true;}
 public synchronized void failed(long token,String cursor){if(token==generation)pending.remove(cursor==null?"head":cursor);}
 public synchronized void head(long token,List<T> page){if(token!=generation||uid==null)return;if(page.size()>20)throw new IllegalArgumentException("page_limit");newHead.clear();newHead.addAll(page);}
 /** Explicit refresh; chat may request it only when already at bottom. */
 public synchronized boolean refresh(long token,boolean chat){if(token!=generation||uid==null)return false;if(chat&&!bottom)return false;window.append(newHead,!chat);newHead.clear();repair();return true;}
 public synchronized void reading(String id,int pixels,boolean atBottom){if(window.position(id)<0)throw new IllegalArgumentException("missing_anchor");anchor=id;offset=pixels;bottom=atBottom;}
 private void repair(){if(anchor!=null&&window.position(anchor)<0){List<T> rows=window.rows();anchor=rows.isEmpty()?null:identity.id(rows.get(0));offset=0;}}
 public synchronized List<T> rows(){return Collections.unmodifiableList(window.rows());}
 public synchronized String anchor(){return anchor;}public synchronized int offset(){return offset;}public synchronized boolean hasNew(){return !newHead.isEmpty();}
 public synchronized void draft(Map<String,Object> value){if(uid==null)throw new IllegalStateException("session_required");if(AdoptionValues.canonical(value).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>16384)throw new IllegalArgumentException("draft_limit");draft=ListingSchema.freeze(value);}
 public synchronized Map<String,Object> draft(){return draft;}
 public synchronized void clear(){uid=null;signature=null;anchor=null;offset=0;bottom=true;draft=Collections.emptyMap();pending.clear();newHead.clear();window.clear();generation++;}
 @Override public synchronized void close(){if(sessions!=null)sessions.removeInvalidation(invalidation);clear();}
}
