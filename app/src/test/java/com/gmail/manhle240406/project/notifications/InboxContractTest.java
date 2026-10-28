package com.gmail.manhle240406.project.notifications;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class InboxContractTest {
    @Test public void routeHintsNeverAcceptArbitraryUrlsOrUnapprovedDomains(){
        String id="11111111-1111-4111-a111-111111111111";
        assertTrue(InboxContract.route("comment","post",id,"comments_on_post"));
        assertTrue(InboxContract.route("own_intake","own_report",id,"report_received"));
        assertTrue(InboxContract.route("adoption","adoption_request",id,"adoption_update"));
        assertFalse(InboxContract.route("adoption","adoption_request",id,"direct_message"));
        assertFalse(InboxContract.route("comment","admin",id,"comments_on_post"));
        assertFalse(InboxContract.route("comment","post","https://example.org","comments_on_post"));
        assertFalse(InboxContract.route("reviewer","own_report",id,"report_received"));
    }
    @Test public void markDisplayedSetIsBoundedAndFrozen(){
        Map<String,Long> source=new HashMap<>();source.put("a".repeat(64),1L);
        Map<String,Long> frozen=InboxContract.readSet(source);source.clear();assertEquals(1,frozen.size());
        assertThrows(UnsupportedOperationException.class,()->frozen.clear());
        assertThrows(IllegalArgumentException.class,()->InboxContract.readSet(Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class,()->InboxContract.readSet(Collections.singletonMap("a".repeat(64),0L)));
        Map<String,Long> large=new HashMap<>();for(int i=0;i<21;i++)large.put(String.format("%064x",i),1L);
        assertThrows(IllegalArgumentException.class,()->InboxContract.readSet(large));
    }
    @Test public void genericHintsRequireCurrentAccountBindingEpochExpiryAndExactFields(){
        Map<String,String> hint=new HashMap<>();hint.put("schemaVersion","1");hint.put("uid","A");hint.put("installationId","device");hint.put("bindingEpoch","2");hint.put("itemId","a".repeat(64));hint.put("expiresAt","2000");
        assertTrue(InboxContract.hint(hint,"A","device",2,1000));
        assertFalse(InboxContract.hint(hint,"B","device",2,1000));
        assertFalse(InboxContract.hint(hint,"A","device",3,1000));
        assertFalse(InboxContract.hint(hint,"A","device",2,2000));
        hint.put("body","Private comment");assertFalse(InboxContract.hint(hint,"A","device",2,1000));
    }
    @Test public void memoryWindowBoundsRefreshAndAccountIsolation(){
        InboxWindow window=new InboxWindow();window.session("A");
        for(int page=0;page<6;page++){List<InboxWindow.Entry> rows=new ArrayList<>();for(int i=0;i<20;i++){int n=page*20+i;rows.add(new InboxWindow.Entry(String.format("%064x",n),1000-n,10000,1));}window.page("A",rows,2000);}
        assertEquals(100,window.rows("A").size());String anchor=window.rows("A").get(5).id;window.anchor("A",anchor,17);window.pendingRead("A",anchor,1);
        InboxWindow.Entry newer=new InboxWindow.Entry("f".repeat(64),1500,10000,1);window.head("A",Collections.singletonList(newer),2000);assertTrue(window.newActivity("A"));assertFalse(window.rows("A").stream().anyMatch(e->e.id.equals(newer.id)));
        window.refresh("A",2000);assertEquals(newer.id,window.rows("A").get(0).id);assertEquals(anchor,window.anchor("A"));assertEquals(17,window.offset("A"));
        assertThrows(IllegalArgumentException.class,()->window.page("A",Collections.singletonList(new InboxWindow.Entry(anchor,9999,20000,2)),2000));
        window.session("B");assertTrue(window.rows("B").isEmpty());assertTrue(window.pendingReads("B").isEmpty());assertThrows(IllegalStateException.class,()->window.rows("A"));
    }
}
