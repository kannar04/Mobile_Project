package com.gmail.manhle240406.project.extensions;
import java.util.*;

/** Pure bounded ranking and transient coarse distance. No location or behavioral persistence. */
public final class DiscoveryTools {
    private DiscoveryTools(){}
    public static String distanceBand(double lat,double lng,double coarseLat,double coarseLng){
        if(!Double.isFinite(lat)||!Double.isFinite(lng)||!Double.isFinite(coarseLat)||!Double.isFinite(coarseLng)||Math.abs(lat)>90||Math.abs(coarseLat)>90||Math.abs(lng)>180||Math.abs(coarseLng)>180)return null;
        double a=Math.pow(Math.sin(Math.toRadians(coarseLat-lat)/2),2)+Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(coarseLat))*Math.pow(Math.sin(Math.toRadians(coarseLng-lng)/2),2);
        double km=6371*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
        return km<5?"under_5km":km<10?"5_to_10km":km<25?"10_to_25km":"25km_plus";
    }
    public static final class Candidate {public final String id,species,region;public final Set<String> careTags;public final long published;public final boolean available;public Candidate(String id,String s,String r,Set<String> tags,long time,boolean available){this.id=id;species=s;region=r;careTags=new HashSet<>(tags);published=time;this.available=available;}}
    public static final class Ranked {public final Candidate candidate;public final int score;public final List<String> reasons;Ranked(Candidate c,int n,List<String> r){candidate=c;score=n;reasons=Collections.unmodifiableList(new ArrayList<>(r.subList(0,Math.min(2,r.size()))));}}
    public static List<Ranked> rank(List<Candidate> input,boolean enabled,Set<String> species,Set<String> regions,Set<String> careTags){if(input.size()>60||species.size()>3||regions.size()>3||careTags.size()>5)throw new IllegalArgumentException("bounded_preferences");Map<String,Candidate> unique=new HashMap<>();for(Candidate c:input)if(c.available&&!unique.containsKey(c.id))unique.put(c.id,c);List<Ranked> out=new ArrayList<>();for(Candidate c:unique.values()){int score=0;List<String> reasons=new ArrayList<>();if(enabled&&species.contains(c.species)){score+=4;reasons.add("preferred_species");}if(enabled&&regions.contains(c.region)){score+=2;reasons.add("preferred_region");}if(enabled&&!Collections.disjoint(c.careTags,careTags)){score++;reasons.add("care_interest");}out.add(new Ranked(c,score,reasons));}out.sort(Comparator.comparingInt((Ranked r)->r.score).reversed().thenComparing(Comparator.comparingLong((Ranked r)->r.candidate.published).reversed()).thenComparing(r->r.candidate.id));return out;}
    public static final class RequestGeneration {
        private long generation;private boolean busy;private long originAt=-1;private Double lat,lng;
        public synchronized long begin(){if(busy)throw new IllegalStateException("one_request_in_flight");busy=true;return generation;}
        public synchronized boolean current(long captured){return captured==generation;}
        public synchronized void reset(){generation++;busy=false;}
        public synchronized boolean complete(long captured){if(captured!=generation)return false;busy=false;return true;}
        public synchronized void transientOrigin(double lat,double lng,long elapsed){if(distanceBand(lat,lng,lat,lng)==null||elapsed<0)throw new IllegalArgumentException("approximate_origin");this.lat=lat;this.lng=lng;originAt=elapsed;}
        public synchronized String band(double coarseLat,double coarseLng,long elapsed){if(lat==null||elapsed<originAt||elapsed-originAt>=300000)return null;return distanceBand(lat,lng,coarseLat,coarseLng);}
        public synchronized void clear(){reset();lat=null;lng=null;originAt=-1;}
    }
}
