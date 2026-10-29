package com.gmail.manhle240406.project.extensions;

import static org.junit.Assert.*;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.AniloveApplication;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;

import org.junit.*;
import org.junit.runner.RunWith;

import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class OfflineStoreNativeTest {
    private AniloveApplication app;
    private EncryptedOfflineStore store;
    private String nonce;
    private final FakeClock clock = new FakeClock();

    private static class FakeClock implements EncryptedOfflineStore.Clock {
        long elapsed = 1000, wall = 1792022400000L, boot = 7;

        public long elapsed() {
            return elapsed;
        }

        public long wall() {
            return wall;
        }

        public long boot() {
            return boot;
        }
    }

    private static final EncryptedOfflineStore.Consumer FIXTURE =
            new EncryptedOfflineStore.Consumer() {
                public int schemaVersion() {
                    return 1;
                }

                public long leaseMillis() {
                    return 86400000;
                }

                public boolean permitsDraft() {
                    return true;
                }

                public boolean permitsThumbnail() {
                    return true;
                }

                public void validateThumbnail(
                        String entity,
                        String asset,
                        String variant,
                        long generation,
                        byte[] bytes,
                        String sha) {
                    if (!variant.equals("fictional_sanitized") || bytes.length > 1024)
                        throw new IllegalArgumentException("fixture_media_only");
                }

                public Map<String, Object> validate(Map<String, Object> in, boolean draft) {
                    if (!in.keySet().equals(Set.of("title", "region"))
                            || !(in.get("title") instanceof String)
                            || !(in.get("region") instanceof String))
                        throw new IllegalArgumentException("fixture_whitelist");
                    return new HashMap<>(in);
                }
            };

    private EncryptedOfflineStore open(String nonce) {
        return new EncryptedOfflineStore(
                app,
                app.foundation().sessions,
                app.foundation().sessions.capture(),
                nonce,
                Map.of("fixture_card", FIXTURE),
                clock);
    }

    private <T> T await(com.google.android.gms.tasks.Task<T> t) throws Exception {
        return Tasks.await(t, 20, TimeUnit.SECONDS);
    }

    private Map<String, Object> body(String title) {
        return Map.of("title", title, "region", "coarse-region");
    }

    @Before
    public void setup() throws Exception {
        app = ApplicationProvider.getApplicationContext();
        assertEquals("local_emulator", app.foundation().alias);
        Tasks.await(FirebaseAuth.getInstance().signInAnonymously(), 20, TimeUnit.SECONDS);
        nonce = UUID.randomUUID().toString();
        store = open(nonce);
    }

    @After
    public void cleanup() throws Exception {
        try {
            if (store != null) await(store.closeNamespace());
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> app.auth().logout());
        }
    }

    @Test
    public void cacheAndDraftSurviveStoreReopenButStaySeparateFromServerAuthority()
            throws Exception {
        await(
                store.putValidated(
                        "fixture_card", "item", 1, 1, clock.wall, body("private fixture")));
        await(store.saveDraft("fixture_card", "draft", 0, 1, body("unsent fixture")));
        store.close();
        store = open(nonce);
        assertEquals(
                "private fixture", await(store.read("fixture_card", "item")).payload.get("title"));
        assertEquals("local_only", await(store.readDraft("fixture_card", "draft")).state);
        assertEquals(
                "previously_validated_snapshot", await(store.read("fixture_card", "item")).state);
    }

    @Test
    public void rebootLeaseExpiryAndElapsedRollbackFailClosed() throws Exception {
        await(store.putValidated("fixture_card", "item", 1, 1, clock.wall, body("fictional")));
        clock.wall -= 864000000;
        assertNotNull(await(store.read("fixture_card", "item")));
        clock.boot++;
        assertNull(await(store.read("fixture_card", "item")));
        clock.boot--;
        clock.elapsed = 0;
        assertNull(await(store.read("fixture_card", "item")));
        clock.elapsed = 86401000;
        assertNull(await(store.read("fixture_card", "item")));
    }

    @Test
    public void monotonicVersionTombstoneAndSameVersionConflictHold() throws Exception {
        await(store.putValidated("fixture_card", "item", 2, 3, clock.wall, body("current")));
        assertThrows(
                Exception.class,
                () ->
                        await(
                                store.putValidated(
                                        "fixture_card", "item", 1, 3, clock.wall, body("old"))));
        assertThrows(
                Exception.class,
                () ->
                        await(
                                store.putValidated(
                                        "fixture_card",
                                        "item",
                                        2,
                                        3,
                                        clock.wall,
                                        body("conflicting"))));
        await(store.tombstone("fixture_card", "item", 2, 3));
        assertNull(await(store.read("fixture_card", "item")));
        assertThrows(
                Exception.class,
                () ->
                        await(
                                store.putValidated(
                                        "fixture_card", "item", 2, 3, clock.wall, body("late"))));
        await(
                store.putValidated(
                        "fixture_card", "item", 2, 4, clock.wall, body("explicitly revalidated")));
        assertNotNull(await(store.read("fixture_card", "item")));
    }

    @Test
    public void clearViewedPreservesDraftAndOldReceiptCannotDeleteNewEdit() throws Exception {
        await(store.putValidated("fixture_card", "item", 1, 1, clock.wall, body("viewed")));
        await(store.saveDraft("fixture_card", "draft", 0, 1, body("first")));
        await(store.saveDraft("fixture_card", "draft", 1, 1, body("second")));
        assertFalse(await(store.acknowledgeDraft("fixture_card", "draft", 1)));
        await(store.clearViewed());
        assertNull(await(store.read("fixture_card", "item")));
        assertEquals(
                "second", await(store.readDraft("fixture_card", "draft")).payload.get("title"));
        assertThrows(
                Exception.class,
                () -> await(store.saveDraft("fixture_card", "draft", 1, 1, body("stale edit"))));
    }

    @Test
    public void defaultConsumerMatrixRejectsMedicalCaseAndExtraFields() throws Exception {
        assertThrows(
                Exception.class,
                () ->
                        await(
                                store.putValidated(
                                        "private_medical",
                                        "item",
                                        1,
                                        1,
                                        clock.wall,
                                        body("forbidden"))));
        assertThrows(
                Exception.class,
                () ->
                        await(
                                store.putValidated(
                                        "fixture_card",
                                        "item",
                                        1,
                                        1,
                                        clock.wall,
                                        Map.of(
                                                "title", "fixture", "region", "coarse", "phone",
                                                "secret"))));
    }

    @Test
    public void queryMembershipIsBoundedAndOlderGenerationCannotReplaceAnchorWindow()
            throws Exception {
        await(store.putValidated("fixture_card", "a", 1, 1, clock.wall, body("A")));
        await(store.putValidated("fixture_card", "b", 1, 1, clock.wall, body("B")));
        String q = "a".repeat(64);
        await(store.saveQuery(q, 2, "fixture_card", Arrays.asList("b", "a"), "b", 12));
        assertThrows(
                Exception.class,
                () -> await(store.saveQuery(q, 1, "fixture_card", List.of("a"), "a", 0)));
        assertEquals("B", await(store.savedQuery(q)).get(0).payload.get("title"));
        await(store.tombstone("fixture_card", "b", 1, 1));
        assertEquals(1, await(store.savedQuery(q)).size());
    }

    @Test
    public void encryptedVariantBindsGenerationAndLeaseAndMissingRemainsHonest() throws Exception {
        await(
                store.putValidated(
                        "fixture_card", "item", 1, 1, clock.wall, body("with fixture image")));
        byte[] bytes = {1, 2, 3, 4};
        StringBuilder sha = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
            sha.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        await(
                store.putThumbnail(
                        "fixture_card",
                        "item",
                        "asset",
                        "fictional_sanitized",
                        1,
                        bytes,
                        sha.toString()));
        assertArrayEquals(
                bytes,
                await(store.thumbnail("fixture_card", "item", "asset", "fictional_sanitized", 1)));
        assertNull(
                await(store.thumbnail("fixture_card", "item", "asset", "fictional_sanitized", 2)));
        clock.elapsed += 86400000;
        assertNull(
                await(store.thumbnail("fixture_card", "item", "asset", "fictional_sanitized", 1)));
    }

    @Test
    public void logoutDisposesKeyAndSameUidNewNamespaceCannotResurrectDraft() throws Exception {
        await(store.saveDraft("fixture_card", "draft", 0, 1, body("private unsent")));
        await(store.closeNamespace());
        assertThrows(Exception.class, () -> await(store.readDraft("fixture_card", "draft")));
        store = open(UUID.randomUUID().toString());
        assertNull(await(store.readDraft("fixture_card", "draft")));
    }

    @Test
    public void expiredDraftFreezesAndIsNeverLruDeleted() throws Exception {
        await(store.saveDraft("fixture_card", "draft", 0, 1, body("unsent")));
        clock.wall += 31L * 86400000;
        assertEquals(
                "frozen_needs_keep_or_discard",
                await(store.readDraft("fixture_card", "draft")).state);
        await(store.setViewingEnabled(false));
        assertNotNull(await(store.readDraft("fixture_card", "draft")));
    }

    @Test
    public void outcomeMetadataDoesNotReplayAndConfirmedOldGenerationCannotEraseNewDraft()
            throws Exception {
        await(store.saveDraft("fixture_card", "draft", 0, 1, body("first")));
        String operation = UUID.randomUUID().toString(), hash = "b".repeat(64);
        await(
                store.recordUnknownOutcome(
                        operation,
                        "publish",
                        "opaque_source",
                        1,
                        hash,
                        "fixture_card",
                        "draft",
                        1));
        await(store.saveDraft("fixture_card", "draft", 1, 1, body("new edit")));
        assertThrows(
                Exception.class,
                () -> await(store.applyConfirmedReceipt(operation, "c".repeat(64), true)));
        assertFalse(await(store.applyConfirmedReceipt(operation, hash, true)));
        assertEquals(
                "new edit", await(store.readDraft("fixture_card", "draft")).payload.get("title"));
    }

    private java.io.File dbFile() throws Exception {
        String raw = app.foundation().sessions.capture().uid + ":" + nonce;
        StringBuilder hash = new StringBuilder();
        for (byte b :
                MessageDigest.getInstance("SHA-256")
                        .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return new java.io.File(app.getNoBackupFilesDir(), "offline/" + hash + "/cache.db");
    }

    @Test
    public void sqliteContainsNoPlaintextAndTamperedCiphertextNeverFallsBack() throws Exception {
        String secret = "FICTIONAL_PRIVATE_PAYLOAD_NOT_PLAINTEXT";
        await(store.putValidated("fixture_card", "item", 1, 1, clock.wall, body(secret)));
        java.io.File file = dbFile();
        byte[] disk = java.nio.file.Files.readAllBytes(file.toPath());
        assertFalse(
                new String(disk, java.nio.charset.StandardCharsets.ISO_8859_1).contains(secret));
        android.database.sqlite.SQLiteDatabase db =
                android.database.sqlite.SQLiteDatabase.openDatabase(
                        file.getAbsolutePath(),
                        null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
        try {
            db.execSQL("UPDATE cache_entries SET payload=X'00' WHERE entity='item'");
        } finally {
            db.close();
        }
        assertThrows(Exception.class, () -> await(store.read("fixture_card", "item")));
    }

    @Test
    public void nestedWhitelistedPayloadRestoresMapsAndListsWithoutRawJsonObjects()
            throws Exception {
        await(store.closeNamespace());
        EncryptedOfflineStore.Consumer nested =
                new EncryptedOfflineStore.Consumer() {
                    public int schemaVersion() {
                        return 1;
                    }

                    public long leaseMillis() {
                        return 3600000;
                    }

                    public Map<String, Object> validate(Map<String, Object> p, boolean draft) {
                        if (!p.keySet().equals(Set.of("summary", "labels"))
                                || !(p.get("summary") instanceof Map)
                                || !(p.get("labels") instanceof List))
                            throw new IllegalArgumentException("nested_fixture_schema");
                        return new HashMap<>(p);
                    }
                };
        store =
                new EncryptedOfflineStore(
                        app,
                        app.foundation().sessions,
                        app.foundation().sessions.capture(),
                        UUID.randomUUID().toString(),
                        Map.of("fixture_nested", nested),
                        clock);
        await(
                store.putValidated(
                        "fixture_nested",
                        "item",
                        1,
                        1,
                        clock.wall,
                        Map.of(
                                "summary",
                                Map.of("name", "Fictional pet"),
                                "labels",
                                Arrays.asList("cat", "unknown"))));
        assertEquals(
                "Fictional pet",
                ((Map<?, ?>) await(store.read("fixture_nested", "item")).payload.get("summary"))
                        .get("name"));
    }

    @Test
    public void privateConsumerRegistrationCannotOverrideDefaultSensitiveExclusions()
            throws Exception {
        EncryptedOfflineStore denied =
                new EncryptedOfflineStore(
                        app,
                        app.foundation().sessions,
                        app.foundation().sessions.capture(),
                        UUID.randomUUID().toString(),
                        Map.of("private_medical", FIXTURE),
                        clock);
        try {
            assertThrows(
                    Exception.class,
                    () ->
                            await(
                                    denied.putValidated(
                                            "private_medical",
                                            "item",
                                            1,
                                            1,
                                            clock.wall,
                                            body("excluded"))));
        } finally {
            await(denied.closeNamespace());
        }
    }
}
