package com.gmail.manhle240406.project.community;
import java.util.*;
/** Private setup identity may label its owner's own view; never another reader's view. */
public final class AuthorProfile {
 private AuthorProfile(){}
 public static Map<String,Object> own(String viewer,String author,Map<String,Object> draft){
  if(viewer==null||!viewer.equals(author)||draft==null||!(draft.get("displayName") instanceof String)||((String)draft.get("displayName")).trim().isEmpty())return null;
  Map<String,Object> result=new HashMap<>();result.put("displayName",draft.get("displayName"));result.put("avatarKey",draft.getOrDefault("avatarKey","default"));result.put("bio","");if(draft.get("avatarAssetId") instanceof String)result.put("avatarAssetId",draft.get("avatarAssetId"));return Collections.unmodifiableMap(result);
 }
}
