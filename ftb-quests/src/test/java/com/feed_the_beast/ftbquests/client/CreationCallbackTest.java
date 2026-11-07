package com.feed_the_beast.ftbquests.client;

import com.feed_the_beast.ftbquests.quest.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreationCallbackTest
{
    @Test void incrementalCreationStillInvokesCustomObjectCallbacksExactlyOnce()
    {
        ClientQuestFile file=new ClientQuestFile();AtomicInteger calls=new AtomicInteger();
        var custom=new com.feed_the_beast.ftbquests.quest.task.CheckmarkTask(new Quest(new Chapter(file))){
            @Override public void editedFromGUI(){calls.incrementAndGet();file.refreshObject(this);}
        };
        file.createdFromGUI(custom);
        assertEquals(1,calls.get());
    }
}
