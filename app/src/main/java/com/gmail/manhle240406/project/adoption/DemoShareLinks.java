package com.gmail.manhle240406.project.adoption;
/** Deliberately non-operational demo payload: no user content, identity, session or media. */
public final class DemoShareLinks {
 private DemoShareLinks(){}
 public static String text(String kind,String id){if(!("p".equals(kind)||"l".equals(kind))||id==null||!id.matches("[A-Za-z0-9_-]{1,128}"))throw new IllegalArgumentException("share_identifier");return "ANILOVE demo link - not a live page: https://anilove.example.invalid/"+kind+"/"+id;}
}
