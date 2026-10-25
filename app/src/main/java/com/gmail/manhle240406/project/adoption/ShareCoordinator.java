package com.gmail.manhle240406.project.adoption;
import com.gmail.manhle240406.project.foundation.*;
import com.google.android.gms.tasks.*;

/** Export generic text only after a fresh eligible detail read. Actual sharing is separate wiring. */
public final class ShareCoordinator {
    private final ListingDiscoveryRepository reader;private final ShareLinks links;private final SessionGateway sessions;
    public ShareCoordinator(ListingDiscoveryRepository reader,ShareLinks links,SessionGateway sessions){this.reader=reader;this.links=links;this.sessions=sessions;}
    public Task<String> prepare(SessionEpoch.Ticket t,String listingId){return reader.detail(t,listingId).continueWith(done->{if(t.uid==null||!sessions.current(t))throw new IllegalStateException("session_changed");if(!done.isSuccessful())throw java.util.Objects.requireNonNull(done.getException());if(done.getResult()==null)throw new IllegalStateException("unavailable");return links.genericText(listingId);});}
}
