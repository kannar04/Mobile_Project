package com.gmail.manhle240406.project.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;

import com.gmail.manhle240406.project.foundation.SessionEpoch;
import com.gmail.manhle240406.project.foundation.SessionGateway;

import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;

/** Call on a worker thread with a currently permitted picker stream; never queues uploads. */
public final class CandidatePreparer implements AutoCloseable {
    public static final class Candidate {
        public final File file;
        public final String sha256;
        public final int width, height;

        private Candidate(File f, String hash, int w, int h) {
            file = f;
            sha256 = hash;
            width = w;
            height = h;
        }
    }

    private final File directory;
    private final SessionGateway sessions;
    private volatile boolean closed;
    private final SessionEpoch.Ticket owner;
    private final java.util.concurrent.atomic.AtomicInteger allocated =
            new java.util.concurrent.atomic.AtomicInteger();
    private final Runnable invalidate = this::clear;

    public CandidatePreparer(Context context, SessionGateway gateway) throws IOException {
        sessions = gateway;
        owner = gateway.capture();
        directory = new File(context.getNoBackupFilesDir(), "media-" + UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("candidate_directory");
        sessions.onInvalidated(invalidate);
    }

    private void current(SessionEpoch.Ticket ticket) throws IOException {
        if (closed
                || ticket.uid == null
                || !sessions.current(ticket)
                || !sessions.current(owner)
                || !java.util.Objects.equals(ticket.uid, owner.uid))
            throw new IOException("session_changed");
    }

    public Candidate prepare(SessionEpoch.Ticket ticket, InputStream selected) throws IOException {
        java.util.Objects.requireNonNull(selected);
        try {
            current(ticket);
        } catch (IOException stale) {
            try {
                selected.close();
            } catch (IOException closing) {
                stale.addSuppressed(closing);
            }
            throw stale;
        }
        if (allocated.incrementAndGet() > MediaContract.MAX_OUTSTANDING) {
            allocated.decrementAndGet();
            selected.close();
            throw new IOException("selection_limit");
        }
        File source = new File(directory, UUID.randomUUID() + ".source"),
                output = new File(directory, UUID.randomUUID() + ".jpg");
        Bitmap bitmap = null;
        boolean success = false;
        try {
            try (InputStream in = selected;
                    OutputStream out = new FileOutputStream(source)) {
                byte[] block = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(block)) != -1) {
                    total += n;
                    if (total > MediaContract.MAX_BYTES) throw new IOException("encoded_size");
                    current(ticket);
                    out.write(block, 0, n);
                }
                if (total == 0) throw new IOException("empty_image");
            }
            bitmap =
                    ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(source),
                            (decoder, info, src) -> {
                                String mime = info.getMimeType();
                                if (!java.util.Arrays.asList(
                                                "image/jpeg",
                                                "image/png",
                                                "image/heif",
                                                "image/heic")
                                        .contains(mime))
                                    throw new IllegalArgumentException("unsupported_format");
                                int[] size =
                                        MediaContract.preparedSize(
                                                info.getSize().getWidth(),
                                                info.getSize().getHeight());
                                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                                decoder.setTargetSize(size[0], size[1]);
                                decoder.setOnPartialImageListener(error -> false);
                            });
            current(ticket);
            try (OutputStream out = new FileOutputStream(output)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out))
                    throw new IOException("image_encode");
            }
            if (output.length() > MediaContract.MAX_BYTES) throw new IOException("encoded_size");
            MessageDigest hash;
            try {
                hash = MessageDigest.getInstance("SHA-256");
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            try (InputStream in = new FileInputStream(output)) {
                byte[] block = new byte[8192];
                int n;
                while ((n = in.read(block)) != -1) hash.update(block, 0, n);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : hash.digest())
                hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            current(ticket);
            success = true;
            return new Candidate(output, hex.toString(), bitmap.getWidth(), bitmap.getHeight());
        } catch (IllegalArgumentException rejected) {
            throw new IOException("unsupported_image", rejected);
        } finally {
            if (bitmap != null) bitmap.recycle();
            source.delete();
            if (!success) {
                output.delete();
                allocated.decrementAndGet();
            }
            if (closed) directory.delete();
        }
    }

    public void release(Candidate candidate) {
        if (!directory.equals(candidate.file.getParentFile()))
            throw new IllegalArgumentException("foreign_candidate");
        if (candidate.file.delete()) allocated.decrementAndGet();
    }

    /** Ends this preparer; logout never waits for a worker's input/decode monitor. */
    public void clear() {
        closed = true;
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        directory.delete();
    }

    @Override
    public void close() {
        closed = true;
        sessions.removeInvalidation(invalidate);
        clear();
    }
}
