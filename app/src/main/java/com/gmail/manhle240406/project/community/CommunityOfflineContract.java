package com.gmail.manhle240406.project.community;

import com.google.firebase.Timestamp;
import java.util.*;

/** F31 r3 mapper for a future F41 consumer; no database, disk write or default-enabled consumer. */
public final class CommunityOfflineContract {
    private static boolean bundled(String key) { return key==null || Arrays.asList("default","cat","dog").contains(key); }
    private CommunityOfflineContract() {}
    public static Map<String,Object> viewed(Map<String,Object> online,String postId,boolean fromCache,boolean pendingWrites) {
        if(fromCache || pendingWrites || !SocialSchema.feed(online,postId) || !Long.valueOf(3).equals(online.get("schemaVersion"))
                || !bundled((String)online.get("bundledIllustrationKey")))
            throw new IllegalArgumentException("fresh_schema3_projection_required");
        Map<String,Object> safe=new LinkedHashMap<>();
        for(String k:Arrays.asList("postId","schemaVersion","contentVersion","controlVersion","type","body",
                "bundledIllustrationKey","audience","state")) safe.put(k,online.get(k));
        safe.put("firstPublishedAt",timestamp(online.get("firstPublishedAt")));
        safe.put("editedAt",timestamp(online.get("editedAt")));
        return Collections.unmodifiableMap(safe); // Deliberately excludes author identity/counts/reactions/comments.
    }
    private static Object timestamp(Object value) {
        if(value==null) return null;
        Timestamp t=(Timestamp)value;
        return Collections.unmodifiableMap(SocialRepository.map("seconds",t.getSeconds(),"nanoseconds",(long)t.getNanoseconds()));
    }
    public static Map<String,Object> draft(String draftId,String postId,Long contentVersion,Long controlVersion,
                                           String body,String bundledKey,long generation) {
        if(!com.gmail.manhle240406.project.pet.PetSchema.id(draftId)
                || postId!=null&&!com.gmail.manhle240406.project.pet.PetSchema.id(postId)
                || (postId==null ? contentVersion!=null || controlVersion!=null
                    : contentVersion==null || controlVersion==null || contentVersion<1 || controlVersion<1)
                || body==null || body.codePointCount(0,body.length())>2000
                || !bundled(bundledKey) || generation<1)
            throw new IllegalArgumentException("draft_allowlist");
        return Collections.unmodifiableMap(SocialRepository.map("draftId",draftId,"postId",postId,
                "baseContentVersion",contentVersion,"baseControlVersion",controlVersion,"body",body,
                "bundledIllustrationKey",bundledKey,"draftGeneration",generation));
    }
}
