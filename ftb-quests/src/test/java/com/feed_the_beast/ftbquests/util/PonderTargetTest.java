package com.feed_the_beast.ftbquests.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PonderTargetTest
{
    @Test void legacyComponentsAndSpecificTutorialsAcceptFullLinks()
    {
        assertEquals(new PonderTarget("minecraft:compass",""),PonderTarget.parse("minecraft:compass"));
        assertEquals(new PonderTarget("minecraft:compass",""),PonderTarget.parse("compass"));
        assertEquals(new PonderTarget("minecraft:compass",""),PonderTarget.parse(":compass"));
        assertEquals(new PonderTarget("minecraft:compass",""),PonderTarget.parse("MINECRAFT:compass"));
        PonderTarget selected=PonderTarget.parse(" ponder:pack:machine#pack:guide/step_2 ");
        assertEquals("pack:machine#pack:guide/step_2",selected.value());
        assertEquals("ponder:pack:machine#pack:guide/step_2",selected.uri());
        assertEquals(selected,PonderTarget.parse(selected.uri()));
    }
    @Test void ponderNamespaceIsNotMistakenForTheLinkScheme()
    {
        for(String value:new String[]{"ponder:machine","ponder:machine#pack:scene","ponder:machine#ponder:scene"})
        {
            PonderTarget target=PonderTarget.parse(value);
            assertEquals(value,target.value());
            assertEquals(target,PonderTarget.parse(target.uri()));
        }
    }
    @Test void malformedTargetsNeverBecomeDefaultOrUnrelatedScenes()
    {
        assertThrows(IllegalArgumentException.class,()->PonderTarget.parse(null));
        for(String value:new String[]{"", "pack:", "pack:a#", "pack:a#scene", "pack:a#pack:b#pack:c", "pack:a/../b", "pack:./b", "pack:a//b", "pack:Item", "https://example.org", "ponder:pack:a:extra", "pack:"+"a".repeat(510)})
            assertThrows(IllegalArgumentException.class,()->PonderTarget.parse(value),value);
    }
}
