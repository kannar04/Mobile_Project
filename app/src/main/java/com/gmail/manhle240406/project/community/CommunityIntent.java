package com.gmail.manhle240406.project.community;

import java.util.*;
import com.gmail.manhle240406.project.adoption.AdoptionValues;
import com.gmail.manhle240406.project.listing.LocalListingDraftStore;

/** F31r4 allowlist and consent age. This is never a general report/admin queue. */
public final class CommunityIntent {
 public static final long MAX_AGE=24L*60*60*1000;
 private CommunityIntent(){}
 public static void validate(SocialCommand c){
  if(!Arrays.asList("publish","set_like","add_comment").contains(c.command))throw new IllegalArgumentException("not_queueable");
  if(c.command.equals("set_like")){
   if(c.input.size()!=1||!(c.input.get("active") instanceof Boolean)||c.targetId!=null)throw new IllegalArgumentException("like_input");
  }else if(c.command.equals("add_comment")){
   if(c.targetId==null||c.input.size()!=1||!SocialRepository.text((String)c.input.get("body"),500,2000))throw new IllegalArgumentException("comment_input");
  }else {
   Map<String,Object> content=new HashMap<>(c.input);Object consent=content.remove("consent");Object audience=content.remove("audience");if(audience!=null&&!"everyone".equals(audience))throw new IllegalArgumentException("audience");
   if(!Boolean.TRUE.equals(consent)||!CommunitySchema.fields(content)||c.targetId!=null||c.contentVersion!=0||c.controlVersion!=0)throw new IllegalArgumentException("publish_input");
  }
 }
 public static boolean maySend(long accepted,long acceptedBoot,long now,long currentBoot,int attempts){return accepted>=0&&now>=accepted&&now-accepted<MAX_AGE&&acceptedBoot>=0&&acceptedBoot==currentBoot&&attempts<8;}
 public static String encode(SocialCommand c){validate(c);Map<String,Object> m=new HashMap<>();m.put("command",c.command);m.put("post",c.postId);m.put("target",c.targetId);m.put("operation",c.operationId);m.put("cv",c.contentVersion);m.put("ctl",c.controlVersion);m.put("tv",c.targetVersion);m.put("input",c.input);return AdoptionValues.canonical(m);}
 @SuppressWarnings("unchecked") public static SocialCommand decode(String json){Map<String,Object> m=LocalListingDraftStore.decode(json);if(m.size()!=8)throw new IllegalArgumentException("intent_schema");SocialCommand c=new SocialCommand((String)m.get("command"),(String)m.get("post"),(String)m.get("target"),(Long)m.get("cv"),(Long)m.get("ctl"),(Long)m.get("tv"),(Map<String,Object>)m.get("input"),(String)m.get("operation"));validate(c);return c;}
}
