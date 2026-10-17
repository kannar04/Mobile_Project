package com.gmail.manhle240406.project.community;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PendingReactionTrackerTest {
 private final String post="11111111-1111-4111-8111-111111111111";
 private CommunityOutbox.Entry entry(String state,boolean active){return new CommunityOutbox.Entry(new SocialCommand("set_like",post,null,1,0,0,SocialRepository.map("active",active),UUID.randomUUID().toString()),state,1,1,0,0);}
 @Test public void acknowledgedOrCanceledLikeRefreshesServerStateOnce(){PendingReactionTracker t=new PendingReactionTracker();assertTrue(t.completedPosts(Arrays.asList(entry("queued",true))).isEmpty());assertEquals(Collections.singleton(post),t.completedPosts(Collections.emptyList()));assertTrue(t.completedPosts(Collections.emptyList()).isEmpty());}
 @Test public void sendingOrReviewStillHasPendingMarker(){PendingReactionTracker t=new PendingReactionTracker();t.completedPosts(Arrays.asList(entry("queued",true)));assertTrue(t.completedPosts(Arrays.asList(entry("sending",true))).isEmpty());assertTrue(t.completedPosts(Arrays.asList(entry("needs_review",true))).isEmpty());}
 @Test public void coalescedOppositeIntentDoesNotPretendToBeCommitted(){PendingReactionTracker t=new PendingReactionTracker();t.completedPosts(Arrays.asList(entry("queued",true)));assertTrue(t.completedPosts(Arrays.asList(entry("queued",false))).isEmpty());assertEquals(Collections.singleton(post),t.completedPosts(Collections.emptyList()));}
 @Test public void pendingCopyClearsOnlyAfterAllContributionsForPostLeaveQueue(){PendingReactionTracker t=new PendingReactionTracker();CommunityOutbox.Entry reaction=entry("queued",true),comment=new CommunityOutbox.Entry(new SocialCommand("add_comment",post,UUID.randomUUID().toString(),1,0,0,SocialRepository.map("body","Candidate"),UUID.randomUUID().toString()),"queued",1,1,0,0);assertTrue(t.completedContributions(Arrays.asList(reaction,comment)).isEmpty());assertTrue(t.completedContributions(Arrays.asList(comment)).isEmpty());assertEquals(Collections.singleton(post),t.completedContributions(Collections.emptyList()));}
}
