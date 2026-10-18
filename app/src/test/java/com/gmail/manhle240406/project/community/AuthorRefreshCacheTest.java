package com.gmail.manhle240406.project.community;
import org.junit.*;import static org.junit.Assert.*;import java.util.*;
public class AuthorRefreshCacheTest {
 @After public void clear(){SessionAliases.clear();}
 @Test public void explicitRefreshInvalidatesUnchangedPostIdentityAndLateEpoch(){SessionAliases.put("viewer","author",Collections.singletonMap("displayName","Old"),100);long epoch=SessionAliases.generation();assertNotNull(SessionAliases.get("viewer","author",200));SessionAliases.clear();assertTrue(SessionAliases.generation()>epoch);assertNull(SessionAliases.get("viewer","author",201));SessionAliases.put("viewer","author",Collections.singletonMap("displayName","New"),202);assertEquals("New",SessionAliases.get("viewer","author",203).value.get("displayName"));}
 @Test public void cachedIdentityIsViewerScopedBoundedAndExpiresWithoutScrollReads(){SessionAliases.put("one","author",Collections.singletonMap("displayName","Name"),100);assertNull(SessionAliases.get("two","author",101));assertNotNull(SessionAliases.get("one","author",59999));assertNull(SessionAliases.get("one","author",60100));for(int i=0;i<21;i++)SessionAliases.put("one","author"+i,Collections.emptyMap(),100);assertNull(SessionAliases.get("one","author0",101));assertNotNull(SessionAliases.get("one","author20",101));}
}
