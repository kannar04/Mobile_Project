package com.gmail.manhle240406.project.wiring;
import org.junit.Test;
import static org.junit.Assert.*;
public final class SparkCapabilitiesTest {
 @Test public void sparkNeverEnablesUploadsOrTrustedClientCommands(){assertFalse(SparkCapabilities.uploadsEnabled());assertFalse(SparkCapabilities.trustedCommandsEnabled());}
 @Test public void highRiskPrototypeRoutesRemainGated(){for(String name:new String[]{"AdminCaseToolsActivity","PetExchangeActivity","WelfareConcernsActivity","LostAndFoundActivity","OfflineSyncActivity"})assertTrue(SparkCapabilities.MANAGED_ROUTES.contains(name));}
 @Test public void ordinaryExistingRepositoriesAreNotBlockedAsAdminFeatures(){assertFalse(SparkCapabilities.MANAGED_ROUTES.contains("DiscoveryActivity"));assertFalse(SparkCapabilities.MANAGED_ROUTES.contains("FavoritesActivity"));assertFalse(SparkCapabilities.MANAGED_ROUTES.contains("CatalogActivity"));}
}
