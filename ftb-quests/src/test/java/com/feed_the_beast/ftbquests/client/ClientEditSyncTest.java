package com.feed_the_beast.ftbquests.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientEditSyncTest
{
    @Test void anOldReplyCannotFinishANewerEdit()
    {
        ClientEditSync sync = new ClientEditSync();
        long first = sync.begin(42), second = sync.begin(42);
        assertFalse(sync.receive(42, sync.session, first));
        assertFalse(sync.receive(42, ClientEditSync.UNSOLICITED, 0));
        assertTrue(sync.receive(42, sync.session, second));
        assertFalse(sync.receive(42, sync.session, first));
        assertTrue(sync.receive(42, ClientEditSync.UNSOLICITED, 0));
        assertFalse(sync.receive(42, sync.session, second));
    }

    @Test void editsToDifferentObjectsFinishIndependently()
    {
        ClientEditSync sync = new ClientEditSync();
        long quest = sync.begin(42), chapter = sync.begin(43);
        assertTrue(sync.receive(42, sync.session, quest));
        assertTrue(sync.receive(42, ClientEditSync.UNSOLICITED, 0));
        assertFalse(sync.receive(43, ClientEditSync.UNSOLICITED, 0));
        assertTrue(sync.receive(43, sync.session, chapter));
    }

    @Test void aLaterMoveSupersedesEarlierPropertyReplies()
    {
        ClientEditSync sync = new ClientEditSync();
        long edit = sync.begin(42), move = sync.begin(42), nextEdit = sync.begin(42);
        assertFalse(sync.receive(42, sync.session, move));
        assertFalse(sync.receive(42, sync.session, edit));
        assertTrue(sync.receive(42, sync.session, nextEdit));
    }

    @Test void anotherEditorsSessionCannotAcknowledgeOurPendingChange()
    {
        ClientEditSync first = new ClientEditSync(), other = new ClientEditSync();
        long revision = first.begin(42);
        assertFalse(first.receive(42, other.session, revision));
        assertTrue(first.receive(42, first.session, revision));
        assertTrue(first.receive(42, other.session, revision));
    }
}
