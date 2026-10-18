package com.gmail.manhle240406.project.community;
import org.junit.*;import java.util.*;import static org.junit.Assert.*;
public final class SessionAliasesTest {
 @Before public void reset(){SessionAliases.clear();}
 @Test public void aliasesAreSharedButViewerIsolatedAndExpire(){SessionAliases.put("a","author",Collections.singletonMap("displayName","Name"),1);assertEquals("Name",SessionAliases.get("a","author",2).value.get("displayName"));assertNull(SessionAliases.get("b","author",2));assertNull(SessionAliases.get("a","author",60_001));}
 @Test public void cacheIsLimitedToTwentyAndNullResultsExpireSooner(){for(int i=0;i<21;i++)SessionAliases.put("a","author"+i,Collections.emptyMap(),1);assertNull(SessionAliases.get("a","author0",2));SessionAliases.put("a","hidden",null,1);assertNotNull(SessionAliases.get("a","hidden",2));assertNull(SessionAliases.get("a","hidden",5_001));}
 @Test public void invalidationDropsCopiesAndValuesAreImmutable(){Map<String,Object> source=new HashMap<>();source.put("displayName","Name");SessionAliases.put("a","author",source,1);source.put("displayName","Other");assertEquals("Name",SessionAliases.get("a","author",2).value.get("displayName"));long before=SessionAliases.generation();SessionAliases.clear();assertTrue(SessionAliases.generation()>before);assertNull(SessionAliases.get("a","author",2));}
}
