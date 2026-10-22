package com.gmail.manhle240406.project.community;
import java.util.*;
import com.google.firebase.Timestamp;
import com.gmail.manhle240406.project.pet.PetSchema;
/** Guest data is a separate exact whitelist, never a private source/member card. */
public final class PublicStorySchema {
 private PublicStorySchema(){}
 public static boolean valid(Map<String,Object> m,String id){if(m!=null&&Long.valueOf(5).equals(m.get("schemaVersion"))){if(!(m.get("mediaAssetIds") instanceof List)||!Objects.equals(m.get("contentVersion"),m.get("manifestVersion"))||com.gmail.manhle240406.project.media.MediaGrid.ids(m.get("mediaAssetIds")).size()!=((List<?>)m.get("mediaAssetIds")).size())return false;Map<String,Object> old=new HashMap<>(m);old.remove("mediaAssetIds");old.remove("manifestVersion");old.put("schemaVersion",4L);return valid(old,id);}return m!=null&&m.keySet().equals(new HashSet<>(Arrays.asList("schemaVersion","postId","audience","state","contentVersion","body","bundledIllustrationKey","firstPublishedAt","editedAt")))&&PetSchema.id(id)&&id.equals(m.get("postId"))&&Long.valueOf(4).equals(m.get("schemaVersion"))&&"everyone".equals(m.get("audience"))&&"visible".equals(m.get("state"))&&m.get("contentVersion") instanceof Long&&(Long)m.get("contentVersion")>0&&m.get("body") instanceof String&&(m.get("bundledIllustrationKey")==null||m.get("bundledIllustrationKey") instanceof String)&&CommunitySchema.fields(CommunitySchema.fields((String)m.get("body"),(String)m.get("bundledIllustrationKey")))&&m.get("firstPublishedAt") instanceof Timestamp&&(m.get("editedAt")==null||m.get("editedAt") instanceof Timestamp);}
}
