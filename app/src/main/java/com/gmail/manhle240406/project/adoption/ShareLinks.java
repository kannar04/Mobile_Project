package com.gmail.manhle240406.project.adoption;

import java.net.URI;
import java.util.*;

/** Credential-free formatter/parser only. Does not open chooser, route, clipboard or browser. */
public final class ShareLinks {
    private final String origin;

    public ShareLinks(String verifiedOrigin, boolean enabled) {
        if (!enabled) {
            origin = null;
            return;
        }
        URI uri = URI.create(verifiedOrigin);
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !verifiedOrigin.equals("https://" + uri.getHost())
                || uri.getHost().equals("localhost"))
            throw new IllegalArgumentException("invalid_origin");
        origin = verifiedOrigin;
    }

    public String format(String listingId) {
        if (origin == null) throw new IllegalStateException("origin_not_configured");
        return origin + "/l/" + AdoptionValues.listingId(listingId);
    }

    public String parse(String link) {
        if (origin == null) throw new IllegalStateException("origin_not_configured");
        URI uri = URI.create(link);
        if (!link.startsWith(origin + "/l/")
                || !"https".equals(uri.getScheme())
                || !URI.create(origin).getHost().equals(uri.getHost())
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawPath().contains("%"))
            throw new IllegalArgumentException("invalid_link");
        String id = uri.getRawPath().substring(3);
        AdoptionValues.listingId(id);
        if (!format(id).equals(link)) throw new IllegalArgumentException("invalid_link");
        return id;
    }

    public String genericText(String listingId) {
        return "View this AniLove listing in the app: " + format(listingId);
    }

    public static final class Pending {
        private String uid, id;

        public synchronized void set(String sessionUid, String listingId) {
            uid = sessionUid;
            id = AdoptionValues.listingId(listingId);
        }

        public synchronized void bindAfterSignIn(String newUid) {
            if (uid != null || id == null || newUid == null)
                throw new IllegalStateException("continuation_not_pending");
            uid = newUid;
        }

        public synchronized String consume(String currentUid) {
            if (!Objects.equals(uid, currentUid)) {
                clear();
                return null;
            }
            String result = id;
            clear();
            return result;
        }

        public synchronized void clear() {
            uid = null;
            id = null;
        }
    }
}
