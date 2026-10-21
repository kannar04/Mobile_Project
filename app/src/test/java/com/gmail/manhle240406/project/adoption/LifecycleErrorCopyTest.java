package com.gmail.manhle240406.project.adoption;

import com.gmail.manhle240406.project.R;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class LifecycleErrorCopyTest {
 @Test public void mapsCommonServerFailuresToActionableCopy(){
  assertEquals(R.string.lc_missing_phone,LifecycleActivity.errorCopy("phone_required"));
  assertEquals(R.string.lc_error_active_request,LifecycleActivity.errorCopy("active_request"));
  assertEquals(R.string.lc_error_changed,LifecycleActivity.errorCopy("adoption_changed"));
  assertEquals(R.string.lc_error_host_only,LifecycleActivity.errorCopy("host_required"));
  assertEquals(R.string.lc_error_network,LifecycleActivity.errorCopy("network_unavailable"));
 }
}
