package com.gmail.manhle240406.project.wiring;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class FlowHubAccessTest {
 @Test public void operatorEntryRequiresAnActiveScopedCapability(){
  assertFalse(FlowHubFragment.operatorAccess(null));
  assertFalse(FlowHubFragment.operatorAccess(row(false,Arrays.asList("course_content_moderation"))));
  assertFalse(FlowHubFragment.operatorAccess(row(true,Arrays.asList("unrelated"))));
  assertTrue(FlowHubFragment.operatorAccess(row(true,Arrays.asList("report_review"))));
 }
 private Map<String,Object> row(boolean active,List<String> capabilities){Map<String,Object> value=new HashMap<>();value.put("schemaVersion",1L);value.put("active",active);value.put("capabilities",capabilities);return value;}
}
