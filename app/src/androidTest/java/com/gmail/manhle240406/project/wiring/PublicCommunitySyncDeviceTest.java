package com.gmail.manhle240406.project.wiring;

import static org.junit.Assert.*;

import android.content.*;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.gmail.manhle240406.project.community.*;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.*;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Real Spark SQL-to-transaction proof. Secondary app, isolated database; default account untouched.
 */
@RunWith(AndroidJUnit4.class)
public final class PublicCommunitySyncDeviceTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private void write(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void drain(CommunityOutbox q) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        q.drain(latch::countDown);
        assertTrue("Sync worker timed out", latch.await(100, TimeUnit.SECONDS));
    }

    @Test
    public void offlineAcknowledgementReopensAndSyncsExactOperations() throws Exception {
        Context app = context();
        FirebaseUser cached = FirebaseAuth.getInstance().getCurrentUser();
        if (cached != null
                && cached.getEmail() != null
                && cached.getEmail().startsWith("native-flow-")
                && cached.getEmail().endsWith("@example.invalid")) {
            try {
                Tasks.await(cached.reload(), 20, TimeUnit.SECONDS);
            } catch (ExecutionException error) {
                if (error.getCause() instanceof FirebaseAuthInvalidUserException)
                    FirebaseAuth.getInstance().signOut();
                else throw error;
            }
        }
        String prior = FirebaseAuth.getInstance().getUid(), run = UUID.randomUUID().toString();
        File dir = new File(app.getExternalFilesDir(null), "public-sync-evidence");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        File manifest = new File(dir, "fixture.json");
        File verified = new File(dir, "verified.txt");
        if (verified.exists()) assertTrue(verified.delete());
        FirebaseApp secondary =
                FirebaseApp.initializeApp(
                        app, FirebaseApp.getInstance().getOptions(), "public-sync-" + run);
        FirebaseAuth auth = FirebaseAuth.getInstance(secondary);
        FirebaseFirestore db = FirebaseFirestore.getInstance(secondary);
        db.setFirestoreSettings(
                new FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                        .build());
        FirebaseUser user = null;
        SessionGateway sessions = null;
        CommunityOutbox reopened = null;
        String post = UUID.randomUUID().toString(),
                comment = UUID.randomUUID().toString(),
                publishOp = UUID.randomUUID().toString(),
                likeOp = UUID.randomUUID().toString(),
                commentOp = UUID.randomUUID().toString();
        try {
            user =
                    Tasks.await(
                                    auth.createUserWithEmailAndPassword(
                                            "native-flow-" + run + "@example.invalid",
                                            run + UUID.randomUUID()),
                                    30,
                                    TimeUnit.SECONDS)
                            .getUser();
            assertNotNull(user);
            JSONObject fixture =
                    new JSONObject()
                            .put("project", "aniloveproject")
                            .put("uid", user.getUid())
                            .put("post", post)
                            .put("comment", comment)
                            .put("publishOp", publishOp)
                            .put("likeOp", likeOp)
                            .put("commentOp", commentOp);
            write(manifest, fixture.toString());
            long end = SystemClock.elapsedRealtime() + 120000;
            while (!verified.exists() && SystemClock.elapsedRealtime() < end)
                SystemClock.sleep(100);
            assertTrue("Host must verify only this synthetic fixture", verified.exists());
            Tasks.await(user.reload(), 20, TimeUnit.SECONDS);
            assertTrue(user.isEmailVerified());
            Tasks.await(user.getIdToken(true), 20, TimeUnit.SECONDS);
            sessions = new SessionGateway(auth);
            SessionEpoch.Ticket ticket = sessions.capture();
            String uid = ticket.uid;
            Tasks.await(
                    db.document("users/" + uid)
                            .set(
                                    SocialRepository.map(
                                            "uid",
                                            uid,
                                            "schemaVersion",
                                            1L,
                                            "createdAt",
                                            FieldValue.serverTimestamp())),
                    20,
                    TimeUnit.SECONDS);
            assertEquals(
                    MembershipRepository.State.READY,
                    Tasks.await(
                            new MembershipRepository(db, sessions).ensureBasicMembership(ticket),
                            25,
                            TimeUnit.SECONDS));
            Context isolated =
                    new ContextWrapper(app) {
                        @Override
                        public Context getApplicationContext() {
                            return this;
                        }

                        @Override
                        public File getNoBackupFilesDir() {
                            File d =
                                    new File(
                                            super.getNoBackupFilesDir(), "public-sync-device-test");
                            if (!d.isDirectory() && !d.mkdirs())
                                throw new IllegalStateException("directory");
                            return d;
                        }
                    };
            CommunityOutbox.Scheduler scheduler =
                    new CommunityOutbox.Scheduler() {
                        public void schedule(long delay) {}

                        public void cancel() {}
                    };
            Tasks.await(db.disableNetwork(), 10, TimeUnit.SECONDS);
            CommunityOutbox first = new CommunityOutbox(isolated, sessions, scheduler, db);
            Map<String, Object> content =
                    CommunitySchema.fields("ANILOVE temporary fictional native sync test.", null);
            content.put("consent", true);
            content.put("audience", "everyone");
            SocialCommand publish =
                    new SocialCommand("publish", post, null, 0, 0, 0, content, publishOp);
            Tasks.await(first.accept(ticket, publish), 10, TimeUnit.SECONDS);
            reopened = new CommunityOutbox(isolated, sessions, scheduler, db);
            Tasks.await(db.enableNetwork(), 10, TimeUnit.SECONDS);
            drain(reopened);
            DocumentSnapshot root =
                    Tasks.await(
                            db.document("communityPosts/" + post).get(Source.SERVER),
                            20,
                            TimeUnit.SECONDS);
            assertEquals(Long.valueOf(4), root.getLong("schemaVersion"));
            assertEquals("visible", root.getString("visibility"));
            assertTrue(
                    PublicStorySchema.valid(
                            Tasks.await(
                                            db.document("communityPublicFeed/" + post)
                                                    .get(Source.SERVER),
                                            20,
                                            TimeUnit.SECONDS)
                                    .getData(),
                            post));
            Tasks.await(
                    reopened.accept(
                            ticket,
                            new SocialCommand(
                                    "set_like",
                                    post,
                                    null,
                                    1,
                                    0,
                                    0,
                                    SocialRepository.map("active", true),
                                    likeOp)),
                    10,
                    TimeUnit.SECONDS);
            Tasks.await(
                    reopened.accept(
                            ticket,
                            new SocialCommand(
                                    "add_comment",
                                    post,
                                    comment,
                                    1,
                                    0,
                                    0,
                                    SocialRepository.map(
                                            "body", "Temporary fictional native queued comment."),
                                    commentOp)),
                    10,
                    TimeUnit.SECONDS);
            drain(reopened);
            assertEquals(
                    Long.valueOf(1),
                    Tasks.await(
                                    db.document("communityCounts/" + post).get(Source.SERVER),
                                    20,
                                    TimeUnit.SECONDS)
                            .getLong("likeCount"));
            assertEquals(
                    "visible",
                    Tasks.await(
                                    db.document(
                                                    "communityCommentFeed/"
                                                            + post
                                                            + "/comments/"
                                                            + comment)
                                            .get(Source.SERVER),
                                    20,
                                    TimeUnit.SECONDS)
                            .getString("state"));
            Tasks.await(reopened.accept(ticket, publish), 10, TimeUnit.SECONDS);
            drain(reopened);
            for (String op : Arrays.asList(publishOp, likeOp, commentOp))
                assertTrue(
                        Tasks.await(
                                        db.document("users/" + uid + "/communityOperations/" + op)
                                                .get(Source.SERVER),
                                        20,
                                        TimeUnit.SECONDS)
                                .exists());
            assertEquals(
                    Long.valueOf(1),
                    Tasks.await(
                                    db.document("communityPosts/" + post).get(Source.SERVER),
                                    20,
                                    TimeUnit.SECONDS)
                            .getLong("contentVersion"));
            assertTrue(
                    "Default account must remain unchanged",
                    Objects.equals(prior, FirebaseAuth.getInstance().getUid()));
            write(
                    new File(dir, "result.json"),
                    new JSONObject()
                            .put("passed", true)
                            .put("offlineSqlAck", true)
                            .put("reopenedEncryptedQueue", true)
                            .put("publishLikeCommentSync", true)
                            .put("exactReceiptRetry", true)
                            .put("defaultAccountPreserved", true)
                            .toString());
        } finally {
            if (reopened != null) reopened.stop();
            if (user != null) Tasks.await(user.delete(), 30, TimeUnit.SECONDS);
            if (sessions != null) sessions.close();
            Tasks.await(db.terminate(), 10, TimeUnit.SECONDS);
            secondary.delete();
            assertTrue(
                    "Default account must remain unchanged",
                    Objects.equals(prior, FirebaseAuth.getInstance().getUid()));
        }
    }
}
