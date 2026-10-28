package com.gmail.manhle240406.project.community;
import org.junit.Test;
import java.util.*;
import com.google.firebase.Timestamp;
import static org.junit.Assert.*;
public class PublicStorySchemaTest {
 private final String id="11111111-1111-4111-8111-111111111111";
 private Map<String,Object> story(){return SocialRepository.map("schemaVersion",4L,"postId",id,"audience","everyone","state","visible","contentVersion",1L,"body","Fictional public story","bundledIllustrationKey",null,"firstPublishedAt",new Timestamp(10,0),"editedAt",null);}
 @Test public void publicWhitelistExcludesIdentityAndUnknownFields(){Map<String,Object> m=story();assertTrue(PublicStorySchema.valid(m,id));m.put("authorUid","private");assertFalse(PublicStorySchema.valid(m,id));}
 @Test public void invalidRemoteDataDoesNotCrashOrBecomePublic(){Map<String,Object> m=story();m.put("bundledIllustrationKey",4L);assertFalse(PublicStorySchema.valid(m,id));m=story();m.put("audience","community");assertFalse(PublicStorySchema.valid(m,id));m=story();m.remove("editedAt");assertFalse(PublicStorySchema.valid(m,id));m=story();m.put("contentVersion",0L);assertFalse(PublicStorySchema.valid(m,id));}
}
